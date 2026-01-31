package concord.dev.service

import concord.dev.domain.*
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.Instant

@ApplicationScoped
class PostService(
    private val entityManager: EntityManager,
    private val threadService: ThreadService
) {

    /**
     * Create a post with per-thread sequential numbering.
     * Uses optimistic locking with retry on unique constraint violations.
     */
    @Transactional
    fun createPost(
        threadId: ThreadId,
        content: String,
        parentPostId: PostId? = null,
        metadata: String? = null,
        agentId: String? = null,
        postType: PostType? = null,
        confidence: Double? = null
    ): Post {
        Log.infof("[PostService] Creating post - threadId=%s, contentLength=%d", threadId, content.length)
        
        val maxRetries = 5
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                return createPostAttempt(threadId, content, parentPostId, metadata, agentId, postType, confidence)
            } catch (e: Exception) {
                Log.warnf(e, "[PostService] Attempt %d failed for threadId=%s", attempt + 1, threadId)
                if (isUniqueConstraintViolation(e)) {
                    // Backoff with small jitter before retrying
                    val backoffMs = 5L + (attempt * 5L) + (kotlin.random.Random.nextLong(0, 10))
                    Log.debugf("[PostService] Backing off for %dms before retry", backoffMs)
                    try { java.lang.Thread.sleep(backoffMs) } catch (_: InterruptedException) {}
                    lastException = e
                } else {
                    Log.errorf(e, "[PostService] Non-retryable error creating post")
                    throw e
                }
            }
        }

        Log.errorf(lastException, "[PostService] Failed to create post after %d retries", maxRetries)
        throw lastException ?: IllegalStateException("Failed to create post due to unknown error")
    }
    
    private fun createPostAttempt(
        threadId: ThreadId,
        content: String,
        parentPostId: PostId? = null,
        metadata: String? = null,
        agentId: String? = null,
        postType: PostType? = null,
        confidence: Double? = null
    ): Post {
        Log.debugf("[PostService] Getting next post number for threadId=%s", threadId)
        
        // Get next post number value
        val nextNumberValue = entityManager.createQuery(
            "SELECT COALESCE(MAX(p.postNumber), 0) + 1 FROM Post p WHERE p.threadId = :threadId",
            Int::class.java
        )
            .setParameter("threadId", threadId.value)
            .singleResult
        
        Log.debugf("[PostService] Next post number: %d", nextNumberValue)

        // Create post
        val post = Post().apply {
            this.threadId = threadId.value
            this.parentPostId = parentPostId
            this.content = content
            this.postNumber = nextNumberValue
            this.postedAt = Instant.now()
            this.metadata = metadata
            this.agentId = agentId
            this.postType = postType
            this.confidence = confidence
        }
        
        Log.debugf("[PostService] Persisting post with postNumber=%d", nextNumberValue)
        post.persist()
        
        // Flush to surface constraint violations within this transaction
        Log.debugf("[PostService] Flushing entity manager")
        entityManager.flush()
        
        Log.debugf("[PostService] Post persisted successfully, incrementing thread count")

        // Update thread post count and updated_at
        threadService.incrementPostCount(threadId)
        
        Log.infof("[PostService] Post created successfully - postId=%d, postNumber=%d", post.id, post.postNumber)

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