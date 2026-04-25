package concord.dev.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.service.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.util.UUID

@ApplicationScoped
class CrawlConsumer(
    private val crawlerService: CrawlerService,
    private val pageContentService: PageContentService,
    private val failedEventService: FailedEventService,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(CrawlConsumer::class.java)
    @Inject
    @Channel("ai-enrichment-out")
    lateinit var aiEnrichmentEmitter: Emitter<String>

    @Incoming("url-crawl-requests")
    fun consume(message: String) {
        var threadId: UUID? = null

        try {
            // Parse the crawl request
            val request = objectMapper.readValue(message, UrlCrawlRequest::class.java)
            threadId = UUID.fromString(request.threadId)

            log.info("Crawling URL for thread ${request.threadId}: ${request.url}")

            // Crawl the URL
            val result = crawlerService.crawl(request.url)

            // Calculate content hash for change detection
            val contentHash = result.articleText?.let { crawlerService.calculateContentHash(it) }

            // Serialize metadata to JSON
            val metadataJson = objectMapper.writeValueAsString(
                mapOf(
                    "openGraph" to result.metadata.openGraph,
                    "twitterCard" to result.metadata.twitterCard,
                    "canonical" to result.metadata.canonical,
                    "author" to result.metadata.author,
                    "publishedAt" to result.metadata.publishedAt,
                    "siteName" to result.metadata.siteName,
                    "language" to result.metadata.language,
                    "images" to result.images,
                    "durationMs" to result.durationMs
                )
            )

            // Store the content (retry briefly if the thread hasn't been committed yet)
            val maxAttempts = 3
            val retryDelayMs = 250L
            var attempt = 0
            while (true) {
                try {
                    pageContentService.storeContent(
                        threadId = concord.dev.domain.ThreadId(threadId),
                        url = result.url,
                        title = result.title,
                        description = result.description,
                        articleText = result.articleText,
                        rawHtml = result.rawHtml,
                        contentType = result.contentType,
                        statusCode = result.statusCode,
                        contentHash = contentHash,
                        metadata = metadataJson
                    )
                    break
                } catch (e: MissingThreadException) {
                    attempt += 1
                    if (attempt >= maxAttempts) {
                        throw e
                    }
                    log.warn("Thread not found yet for crawl ${request.threadId}; retrying in ${retryDelayMs}ms (attempt $attempt/$maxAttempts)")
                    Thread.sleep(retryDelayMs)
                }
            }

            log.info("Successfully crawled and stored content for thread ${request.threadId}")

            // Trigger AI enrichment if we have content to enrich
            if (result.articleText != null || result.description != null || result.title != null) {
                val enrichmentRequest = AIEnrichmentRequest(
                    threadId = threadId,
                    url = request.url
                )
                val enrichmentMessage = objectMapper.writeValueAsString(enrichmentRequest)
                aiEnrichmentEmitter.send(enrichmentMessage)
                log.info("Sent AI enrichment request for thread ${request.threadId}")
            }

            if (result.error != null) {
                log.warn("Crawl completed with partial error for thread $threadId: ${result.error}")
            }

        } catch (e: Exception) {
            log.error("Error processing crawl request", e)

            // Record failure in DLQ for tracking and potential retry
            failedEventService.recordFailure(
                eventType = "URL_CRAWL",
                eventPayload = message,
                error = e,
                sourceConsumer = "CrawlConsumer",
                threadId = threadId,
                kafkaTopic = "url-crawl-requests"
            )

            // Don't rethrow - we've recorded it for retry
        }
    }
}
