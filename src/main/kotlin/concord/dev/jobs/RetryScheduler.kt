package concord.dev.jobs

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.consumer.AIEnrichmentRequest
import concord.dev.service.FailedEventService
import concord.dev.service.UrlCrawlRequest
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.jboss.logging.Logger
import java.time.Instant

/**
 * Scheduled job to process failed events and retry them
 * Runs every minute to check for events ready to retry
 */
@ApplicationScoped
class RetryScheduler(
    private val failedEventService: FailedEventService,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(RetryScheduler::class.java)

    @Inject
    @Channel("url-crawl-out")
    lateinit var crawlEmitter: Emitter<String>

    @Inject
    @Channel("ai-enrichment-out")
    lateinit var enrichmentEmitter: Emitter<String>

    /**
     * Process pending retries every minute
     */
    @Scheduled(every = "1m", identity = "retry-failed-events")
    fun processRetries() {
        try {
            val pendingRetries = failedEventService.getPendingRetries()

            if (pendingRetries.isEmpty()) {
                log.debug("No failed events ready for retry")
                return
            }

            log.info("Processing ${pendingRetries.size} failed events for retry")

            var successCount = 0
            var failureCount = 0

            for (failedEvent in pendingRetries) {
                try {
                    retryEvent(failedEvent.id!!)
                    successCount++
                } catch (e: Exception) {
                    log.error("Error retrying failed event ${failedEvent.id}", e)
                    failureCount++
                }
            }

            log.info("Retry processing complete: $successCount retried, $failureCount failed")

        } catch (e: Exception) {
            log.error("Error in retry scheduler", e)
        }
    }

    /**
     * Retry a specific failed event by sending it back to the appropriate queue
     */
    private fun retryEvent(failedEventId: Long) {
        val failedEvent = concord.dev.domain.FailedEvent.findById(failedEventId)
            ?: throw IllegalArgumentException("Failed event not found: $failedEventId")

        log.info("Retrying failed event $failedEventId (type=${failedEvent.eventType}, attempt ${failedEvent.retryCount + 1}/${failedEvent.maxRetries})")

        // Send to appropriate queue based on event type
        when (failedEvent.eventType) {
            "URL_CRAWL" -> {
                // Validate payload can be parsed
                try {
                    objectMapper.readValue(failedEvent.eventPayload, UrlCrawlRequest::class.java)
                    crawlEmitter.send(failedEvent.eventPayload)
                    log.info("Sent URL_CRAWL retry for event $failedEventId")
                } catch (e: Exception) {
                    log.error("Invalid URL_CRAWL payload for event $failedEventId, marking as permanent failure", e)
                    failedEventService.markResolved(failedEventId)
                    return
                }
            }

            "AI_ENRICHMENT" -> {
                // Validate payload can be parsed
                try {
                    objectMapper.readValue(failedEvent.eventPayload, AIEnrichmentRequest::class.java)
                    enrichmentEmitter.send(failedEvent.eventPayload)
                    log.info("Sent AI_ENRICHMENT retry for event $failedEventId")
                } catch (e: Exception) {
                    log.error("Invalid AI_ENRICHMENT payload for event $failedEventId, marking as permanent failure", e)
                    failedEventService.markResolved(failedEventId)
                    return
                }
            }

            else -> {
                log.warn("Unknown event type ${failedEvent.eventType} for event $failedEventId, skipping")
                return
            }
        }

        // Schedule next retry or mark as exhausted
        val scheduled = failedEventService.scheduleRetry(failedEventId)
        if (!scheduled) {
            log.warn("Failed event $failedEventId has exhausted retries (${failedEvent.maxRetries})")
        }
    }

    /**
     * Generate health metrics for failed events
     * Runs every 5 minutes for monitoring
     */
    @Scheduled(every = "5m", identity = "failed-events-metrics")
    fun reportMetrics() {
        try {
            val stats = failedEventService.getFailureStats()

            stats.forEach { (eventType, failureStats) ->
                if (failureStats.unresolvedCount > 0) {
                    log.warn("Failed events - $eventType: ${failureStats.unresolvedCount} unresolved")
                }
            }

        } catch (e: Exception) {
            log.error("Error generating failure metrics", e)
        }
    }

    /**
     * Clean up old resolved failures
     * Runs daily at 2 AM to prevent table bloat
     */
    @Scheduled(cron = "0 0 2 * * ?", identity = "cleanup-old-failures")
    fun cleanupOldFailures() {
        try {
            val cutoffDate = Instant.now().minusSeconds(30 * 24 * 60 * 60) // 30 days ago

            val deleted = concord.dev.domain.FailedEvent.delete(
                "resolvedAt is not null and resolvedAt < ?1",
                cutoffDate
            )

            if (deleted > 0) {
                log.info("Cleaned up $deleted resolved failed events older than 30 days")
            }

        } catch (e: Exception) {
            log.error("Error cleaning up old failures", e)
        }
    }
}