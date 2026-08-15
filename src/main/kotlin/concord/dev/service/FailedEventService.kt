package concord.dev.service

import concord.dev.domain.ErrorCategory
import concord.dev.domain.FailedEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.pow

/**
 * Service for tracking failed events and managing retry logic
 */
@ApplicationScoped
class FailedEventService {
    private val log: Logger = Logger.getLogger(FailedEventService::class.java)

    /**
     * Record a failed event for tracking and potential retry
     */
    @Transactional
    fun recordFailure(
        eventType: String,
        eventPayload: String,
        error: Exception,
        sourceConsumer: String,
        threadId: UUID? = null,
        kafkaTopic: String? = null,
        kafkaPartition: Int? = null,
        kafkaOffset: Long? = null,
        metadata: String? = null
    ): FailedEvent {
        val errorCategory = classifyError(error)
        val maxRetries = getMaxRetries(errorCategory)

        val failedEvent = FailedEvent().apply {
            this.eventType = eventType
            this.eventPayload = eventPayload
            this.errorMessage = error.message ?: "Unknown error"
            this.errorStacktrace = error.stackTraceToString()
            this.errorCategory = errorCategory
            this.sourceConsumer = sourceConsumer
            this.threadId = threadId
            this.kafkaTopic = kafkaTopic
            this.kafkaPartition = kafkaPartition
            this.kafkaOffset = kafkaOffset
            this.metadata = metadata
            this.maxRetries = maxRetries
            this.retryCount = 0

            // Schedule first retry if error is retriable
            if (errorCategory in RETRIABLE_CATEGORIES) {
                this.nextRetryAt = calculateNextRetry(0)
            }
        }

        failedEvent.persist()

        log.warn(
            "Recorded failed event: type=$eventType, category=$errorCategory, " +
                    "threadId=$threadId, error=${error.message}"
        )

        return failedEvent
    }

    /**
     * Mark a failed event as resolved
     */
    @Transactional
    fun markResolved(failedEventId: Long) {
        val event = FailedEvent.findById(failedEventId)
        if (event != null) {
            event.resolvedAt = Instant.now()
            event.persist()
            log.info("Marked failed event $failedEventId as resolved")
        }
    }

    /**
     * Increment retry count and schedule next retry
     */
    @Transactional
    fun scheduleRetry(failedEventId: Long): Boolean {
        val event = FailedEvent.findById(failedEventId) ?: return false

        if (event.retryCount >= event.maxRetries) {
            log.warn("Failed event $failedEventId has exceeded max retries (${event.maxRetries})")
            return false
        }

        event.retryCount++
        event.nextRetryAt = calculateNextRetry(event.retryCount)
        event.persist()

        log.info("Scheduled retry ${event.retryCount}/${event.maxRetries} for failed event $failedEventId at ${event.nextRetryAt}")

        return true
    }

    /**
     * Get events ready for retry
     */
    fun getPendingRetries(): List<FailedEvent> {
        return FailedEvent.findPendingRetries()
    }

    /**
     * Get failure statistics by event type
     */
    fun getFailureStats(): Map<String, FailureStats> {
        val eventTypes = listOf("URL_CRAWL", "AI_ENRICHMENT")
        return eventTypes.associateWith { type ->
            val total = FailedEvent.countUnresolvedByType(type)
            val events = FailedEvent.findByType(type).take(10)

            FailureStats(
                eventType = type,
                unresolvedCount = total,
                recentErrors = events.map { event ->
                    ErrorSummary(
                        id = event.id!!,
                        errorMessage = event.errorMessage.take(200),
                        errorCategory = event.errorCategory,
                        retryCount = event.retryCount,
                        failedAt = event.failedAt
                    )
                }
            )
        }
    }

    /**
     * Classify error to determine retry strategy
     */
    private fun classifyError(error: Exception): ErrorCategory {
        return when {
            error is java.net.SocketTimeoutException -> ErrorCategory.TIMEOUT
            error is java.net.UnknownHostException -> ErrorCategory.PERMANENT
            error is java.io.IOException -> ErrorCategory.TRANSIENT
            error.message?.contains("timeout", ignoreCase = true) == true -> ErrorCategory.TIMEOUT
            error.message?.contains("rate limit", ignoreCase = true) == true -> ErrorCategory.RATE_LIMIT
            error.message?.contains("not found", ignoreCase = true) == true -> ErrorCategory.PERMANENT
            error.message?.contains("invalid", ignoreCase = true) == true -> ErrorCategory.PERMANENT
            else -> ErrorCategory.UNKNOWN
        }
    }

    /**
     * Get max retry attempts based on error category
     */
    private fun getMaxRetries(category: ErrorCategory?): Int {
        return when (category) {
            ErrorCategory.TRANSIENT -> 5
            ErrorCategory.TIMEOUT -> 3
            ErrorCategory.RATE_LIMIT -> 5
            ErrorCategory.PERMANENT -> 0  // Don't retry permanent errors
            ErrorCategory.UNKNOWN -> 3
            null -> 3
        }
    }

    /**
     * Calculate next retry time with exponential backoff
     * Base delay: 1 minute, max delay: 1 hour
     */
    private fun calculateNextRetry(retryCount: Int): Instant {
        val baseDelaySeconds = 60L // 1 minute
        val maxDelaySeconds = 3600L // 1 hour

        // Exponential backoff: 1min, 2min, 4min, 8min, 16min, 32min, 60min (capped)
        val delaySeconds = minOf(
            baseDelaySeconds * (2.0.pow(retryCount.toDouble())).toLong(),
            maxDelaySeconds
        )

        return Instant.now().plus(delaySeconds, ChronoUnit.SECONDS)
    }

    companion object {
        private val RETRIABLE_CATEGORIES = setOf(
            ErrorCategory.TRANSIENT,
            ErrorCategory.TIMEOUT,
            ErrorCategory.RATE_LIMIT,
            ErrorCategory.UNKNOWN
        )
    }
}

data class FailureStats(
    val eventType: String,
    val unresolvedCount: Long,
    val recentErrors: List<ErrorSummary>
)

data class ErrorSummary(
    val id: Long,
    val errorMessage: String,
    val errorCategory: ErrorCategory?,
    val retryCount: Int,
    val failedAt: Instant
)