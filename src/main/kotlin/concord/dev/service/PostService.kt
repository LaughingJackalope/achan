package concord.dev.service

import concord.dev.domain.Post
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import org.hibernate.Transaction
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class PostService(
    private val entityManager: EntityManager,
    private val threadService: ThreadService
) {

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    fun createPost(
        threadId: UUID,
        content: String,
        parentPostId: Long? = null,
        metadata: String? = null
    ): Post {
        // Use serializable isolation for sequential post numbering
        entityManager.unwrap(org.hibernate.Session::class.java)
            .doWork { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("SET TRANSACTION ISOLATION LEVEL SERIALIZABLE")
                }
            }

        // Get next post number
        val nextNumber = entityManager.createQuery(
            "SELECT COALESCE(MAX(p.postNumber), 0) + 1 FROM Post p WHERE p.threadId = :threadId",
            Int::class.java
        )
            .setParameter("threadId", threadId)
            .singleResult

        // Create post
        val post = Post().apply {
            this.threadId = threadId
            this.parentPostId = parentPostId
            this.content = content
            this.postNumber = nextNumber
            this.postedAt = Instant.now()
            this.metadata = metadata
        }

        post.persist()

        // Update thread post count and updated_at
        threadService.incrementPostCount(threadId)

        return post
    }

    fun getPosts(
        threadId: UUID,
        limit: Int = 50,
        offset: Int = 0,
        parentId: Long? = null
    ): List<Post> {
        return if (parentId != null) {
            Post.findByThreadIdAndParentId(threadId, parentId, limit, offset)
        } else {
            Post.findByThreadId(threadId, limit, offset)
        }
    }

    fun getPostCount(threadId: UUID): Long {
        return Post.countByThreadId(threadId)
    }
}