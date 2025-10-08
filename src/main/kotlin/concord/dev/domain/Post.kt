package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "post",
    uniqueConstraints = [UniqueConstraint(columnNames = ["thread_id", "post_number"])]
)
class Post : PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "thread_id", nullable = false, columnDefinition = "UUID")
    lateinit var threadId: UUID

    @Column(name = "parent_post_id")
    var parentPostId: Long? = null

    @Column(nullable = false, columnDefinition = "TEXT")
    lateinit var content: String

    @Column(name = "posted_at", nullable = false)
    var postedAt: Instant = Instant.now()

    @Column(name = "post_number", nullable = false)
    var postNumber: Int = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null

    companion object : PanacheCompanion<Post> {
        fun findByThreadId(threadId: UUID, limit: Int = 50, offset: Int = 0): List<Post> {
            return find("threadId = ?1 ORDER BY postNumber ASC", threadId)
                .page(offset / limit, limit)
                .list()
        }

        fun countByThreadId(threadId: UUID): Long {
            return count("threadId", threadId)
        }

        fun findByThreadIdAndParentId(threadId: UUID, parentId: Long, limit: Int = 50, offset: Int = 0): List<Post> {
            return find("threadId = ?1 AND parentPostId = ?2 ORDER BY postNumber ASC", threadId, parentId)
                .page(offset / limit, limit)
                .list()
        }
    }
}