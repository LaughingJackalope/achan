package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "thread")
class Thread : PanacheEntityBase {

    @Id
    @Column(columnDefinition = "UUID")
    var id: UUID = UUID.randomUUID()

    @Column(nullable = false, unique = true)
    var url: String? = null

    @Column
    var slug: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "post_count", nullable = false)
    var postCount: Int = 0

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "crawl_status", nullable = false)
    var crawlStatus: CrawlStatus = CrawlStatus.PENDING

    companion object : PanacheCompanion<Thread> {
        fun findByUrl(url: String): Thread? = find("url", url).firstResult()
    }
}

enum class CrawlStatus {
    PENDING,
    COMPLETE,
    FAILED,
    BLOCKED
}