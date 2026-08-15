package concord.dev.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanion
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "failed_event")
class FailedEvent : PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    // Event identification
    @Column(name = "event_type", nullable = false, length = 100)
    lateinit var eventType: String

    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    lateinit var eventPayload: String

    // Error details
    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    lateinit var errorMessage: String

    @Column(name = "error_stacktrace", columnDefinition = "TEXT")
    var errorStacktrace: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", length = 50)
    var errorCategory: ErrorCategory? = null

    // Retry tracking
    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0

    @Column(name = "max_retries", nullable = false)
    var maxRetries: Int = 3

    @Column(name = "next_retry_at")
    var nextRetryAt: Instant? = null

    // Metadata
    @Column(name = "thread_id", columnDefinition = "UUID")
    var threadId: UUID? = null

    @Column(name = "source_consumer", length = 100)
    var sourceConsumer: String? = null

    @Column(name = "kafka_topic", length = 100)
    var kafkaTopic: String? = null

    @Column(name = "kafka_partition")
    var kafkaPartition: Int? = null

    @Column(name = "kafka_offset")
    var kafkaOffset: Long? = null

    // Timestamps
    @Column(name = "failed_at", nullable = false)
    var failedAt: Instant = Instant.now()

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    // Additional context
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: String? = null

    companion object : PanacheCompanion<FailedEvent> {

        fun findUnresolved(): List<FailedEvent> {
            return list("resolvedAt is null order by failedAt desc")
        }

        fun findPendingRetries(): List<FailedEvent> {
            return list(
                "resolvedAt is null and nextRetryAt is not null and nextRetryAt <= ?1",
                Instant.now()
            )
        }

        fun findByThreadId(threadId: UUID): List<FailedEvent> {
            return list("threadId = ?1 order by failedAt desc", threadId)
        }

        fun findByType(eventType: String): List<FailedEvent> {
            return list("eventType = ?1 order by failedAt desc", eventType)
        }

        fun countUnresolvedByType(eventType: String): Long {
            return count("eventType = ?1 and resolvedAt is null", eventType)
        }
    }
}

enum class ErrorCategory {
    TRANSIENT,      // Temporary error, can retry (network, timeout)
    PERMANENT,      // Will never succeed (invalid data, not found)
    TIMEOUT,        // Specific timeout error
    RATE_LIMIT,     // Rate limiting, should retry with backoff
    UNKNOWN         // Unclassified error
}