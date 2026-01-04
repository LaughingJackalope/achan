package concord.dev.domain

import com.pgvector.PGvector
import concord.dev.config.VectorUserType
import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.Type
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "page_content")
class PageContent : PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "thread_id", nullable = false, unique = true, columnDefinition = "UUID")
    @Convert(converter = ThreadIdConverter::class)
    var threadId: ThreadId? = null

    @Column(nullable = false)
    lateinit var url: String

    // Extracted content fields
    @Column
    var title: String? = null

    @Column
    var description: String? = null

    @Column(name = "article_text", columnDefinition = "TEXT")
    var articleText: String? = null

    @Column(name = "raw_html", columnDefinition = "TEXT")
    var rawHtml: String? = null

    // HTTP metadata
    @Column(name = "content_type", length = 100)
    var contentType: String? = null

    @Column(name = "status_code", nullable = false)
    var statusCode: Int = 0

    // Timing and versioning
    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: Instant = Instant.now()

    @Column(name = "content_hash")
    var contentHash: String? = null

    // Flexible structured data
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null

    // Internal notes not visible to users
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "internal_notes", columnDefinition = "jsonb")
    var internalNotes: String? = null

    // AI Enrichment: Semantic embedding (768 dimensions from nomic-embed-text)
    @Type(VectorUserType::class)
    @Column(columnDefinition = "vector(768)")
    var embedding: PGvector? = null

    companion object : PanacheCompanion<PageContent> {
        fun findByThreadId(threadId: ThreadId): PageContent? {
            return find("threadId", threadId).firstResult()
        }

        fun findByUrl(url: String): PageContent? {
            return find("url", url).firstResult()
        }

        /**
         * Convert List<Double> to PGvector
         */
        fun listToVector(embedding: List<Double>): PGvector {
            return PGvector(embedding.map { it.toFloat() }.toFloatArray())
        }

        /**
         * Convert PGvector back to List<Double>
         */
        fun vectorToList(vector: PGvector): List<Double> {
            return vector.toArray().map { it.toDouble() }
        }
    }
}