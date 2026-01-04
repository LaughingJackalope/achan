package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "post",
    uniqueConstraints = [UniqueConstraint(columnNames = ["thread_id", "post_number"])]
)
class Post : PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Convert(converter = PostIdConverter::class)
    var id: PostId? = null

    @Column(name = "thread_id", nullable = false, columnDefinition = "UUID")
    @Convert(converter = ThreadIdConverter::class)
    var threadId: ThreadId? = null

    @Column(name = "parent_post_id")
    @Convert(converter = PostIdConverter::class)
    var parentPostId: PostId? = null

    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = ContentConverter::class)
    var content: Content? = null

    @Column(name = "posted_at", nullable = false)
    var postedAt: Instant = Instant.now()

    @Column(name = "post_number", nullable = false)
    @Convert(converter = PostNumberConverter::class)
    var postNumber: PostNumber = PostNumber(1)

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null

    companion object : PanacheCompanion<Post> {
        fun findByThreadId(threadId: ThreadId, size: Int = 50, page: Int = 0): List<Post> {
            return find("threadId = ?1 ORDER BY postNumber ASC", threadId)
                .page(page, size)
                .list()
        }

        fun countByThreadId(threadId: ThreadId): Long {
            return count("threadId", threadId)
        }

        fun findByThreadIdAndParentId(threadId: ThreadId, parentId: PostId, size: Int = 50, page: Int = 0): List<Post> {
            return find("threadId = ?1 AND parentPostId = ?2 ORDER BY postNumber ASC", threadId, parentId)
                .page(page, size)
                .list()
        }
    }
}