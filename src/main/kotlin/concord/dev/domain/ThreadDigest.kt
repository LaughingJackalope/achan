package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Thread Digest - AI-generated summary and key information about a thread.
 * 
 * Provides agents with quick context about thread content without reading all posts.
 * Includes summary, key claims, open questions, and related threads.
 */
@Entity
@Table(name = "thread_digest")
class ThreadDigest : PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "thread_id", nullable = false, unique = true, columnDefinition = "UUID")
    var threadId: java.util.UUID? = null

    @Column(nullable = false, columnDefinition = "TEXT")
    var summary: String? = null

    /**
     * Key claims extracted from the thread discussion.
     * Structure:
     * [
     *   {
     *     "claim_id": "uuid",
     *     "text": "Docker has better security isolation",
     *     "support_score": 0.3,
     *     "citations": [12, 15, 18],
     *     "rebuttals": [23, 27]
     *   }
     * ]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_claims", columnDefinition = "jsonb")
    var keyClaims: String? = null

    /**
     * Open questions or unresolved issues in the thread.
     * Array of question strings.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "open_questions", columnDefinition = "jsonb")
    var openQuestions: String? = null

    /**
     * Related threads with similarity scores and relationship types.
     * Structure:
     * [
     *   {
     *     "thread_id": "uuid",
     *     "similarity": 0.85,
     *     "relationship": "provides_evidence"
     *   }
     * ]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "related_threads", columnDefinition = "jsonb")
    var relatedThreads: String? = null

    @Column(name = "last_synthesized_at", nullable = false)
    var lastSynthesizedAt: Instant = Instant.now()

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    companion object : PanacheCompanion<ThreadDigest> {
        fun findByThreadId(threadId: ThreadId): ThreadDigest? {
            return find("threadId", threadId.value).firstResult()
        }

        fun deleteByThreadId(threadId: ThreadId): Long {
            return delete("threadId", threadId.value)
        }
    }
}
