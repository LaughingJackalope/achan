package concord.dev.service

import concord.dev.domain.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.Instant

@ApplicationScoped
class PostService(
    private val entityManager: EntityManager,
    private val threadService: ThreadService
) {

    // Self-injection to enable transactional retries via proxy
    @jakarta.inject.Inject
    lateinit var self: PostService

    /**
     * Create a post with per-thread sequential numbering using an optimistic approach:
     * - Compute next number with MAX+1
     * - Persist and flush
     * - On unique constraint violation (race), retry a few times with jitter
     *
     * We intentionally avoid SERIALIZABLE isolation for better throughput under contention.
     */
    fun createPost(
        threadId: ThreadId,
        content: String,
        parentPostId: PostId? = null,
        metadata: String? = null
    ): Post {
        val maxRetries = 5
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                // Call the single-attempt creator in its own transaction (via proxy)
                return self.tryCreatePostOnce(threadId, content, parentPostId, metadata)
            } catch (e: Exception) {
                if (isUniqueConstraintViolation(e)) {
                    // Backoff with small jitter before retrying
                    val backoffMs = 5L + (attempt * 5L) + (kotlin.random.Random.nextLong(0, 10))
                    try { java.lang.Thread.sleep(backoffMs) } catch (_: InterruptedException) {}
                    lastException = e
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IllegalStateException("Failed to create post due to unknown error")
    }

    @Transactional
    fun tryCreatePostOnce(
        threadId: ThreadId,
        content: String,
        parentPostId: PostId? = null,
        metadata: String? = null
    ): Post {
        // Get next post number value
        val nextNumberValue = entityManager.createQuery(
            "SELECT COALESCE(MAX(p.postNumber), 0) + 1 FROM Post p WHERE p.threadId = :threadId",
            Int::class.java
        )
            .setParameter("threadId", threadId)
            .singleResult

        // Create post
        val post = Post().apply {
            this.threadId = threadId
            this.parentPostId = parentPostId
            this.content = Content(content)
            this.postNumber = PostNumber(nextNumberValue)
            this.postedAt = Instant.now()
            this.metadata = metadata
        }

        post.persist()
        // Flush to surface constraint violations within this transaction
        entityManager.flush()

        // Update thread post count and updated_at
        threadService.incrementPostCount(threadId)

        return post
    }

    private fun isUniqueConstraintViolation(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is org.hibernate.exception.ConstraintViolationException) return true
            if (t is java.sql.SQLException && t.sqlState == "23505") return true // Postgres unique_violation
            t = t.cause
        }
        return false
    }

    fun getPosts(
        threadId: ThreadId,
        size: Int = 50,
        page: Int = 0,
        parentId: PostId? = null
    ): List<Post> {
        return if (parentId != null) {
            Post.findByThreadIdAndParentId(threadId, parentId, size, page)
        } else {
            Post.findByThreadId(threadId, size, page)
        }
    }

    fun getPostCount(threadId: ThreadId): Long {
        return Post.countByThreadId(threadId)
    }
}