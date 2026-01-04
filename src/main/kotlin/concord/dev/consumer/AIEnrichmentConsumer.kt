package concord.dev.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.domain.PageContent
import concord.dev.service.OllamaService
import concord.dev.service.PageContentService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.eclipse.microprofile.reactive.messaging.Incoming
import org.jboss.logging.Logger
import java.time.Instant
import java.util.UUID

data class AIEnrichmentRequest(
    val threadId: UUID,
    val url: String
)

data class AIEnrichmentMetadata(
    val summary: String? = null,
    val topics: List<String>? = null,
    val enrichedAt: String = Instant.now().toString()
)

/**
 * Kafka consumer that processes crawled content and adds AI enrichments:
 * - Generates semantic embeddings using nomic-embed-text
 * - Creates summaries using Llama 3.2
 * - Extracts topics/keywords
 * - Stores results in page_content table
 */
@ApplicationScoped
class AIEnrichmentConsumer(
    private val ollamaService: OllamaService,
    private val pageContentService: PageContentService,
    private val failedEventService: concord.dev.service.FailedEventService,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(AIEnrichmentConsumer::class.java)

    @Incoming("ai-enrichment-requests")
    @Transactional
    fun consume(message: String) {
        var threadId: UUID? = null

        try {
            val request = objectMapper.readValue(message, AIEnrichmentRequest::class.java)
            threadId = request.threadId
            log.info("Processing AI enrichment for thread ${request.threadId}")

            val pageContent = PageContent.findByThreadId(request.threadId)
            if (pageContent == null) {
                log.warn("Page content not found for thread ${request.threadId}, skipping enrichment")
                return
            }

            // Skip if already enriched (has embedding)
            if (pageContent.embedding != null) {
                log.info("Page content for thread ${request.threadId} already enriched, skipping")
                return
            }

            // Generate embedding from article text or description or title
            val textToEmbed = when {
                !pageContent.articleText.isNullOrBlank() -> pageContent.articleText!!
                !pageContent.description.isNullOrBlank() -> pageContent.description!!
                !pageContent.title.isNullOrBlank() -> pageContent.title!!
                else -> {
                    log.warn("No text content available for embedding for thread ${request.threadId}")
                    return
                }
            }

            log.info("Generating embedding for ${textToEmbed.length} characters of text")
            val embedding = ollamaService.generateEmbedding(textToEmbed)
            pageContent.embedding = PageContent.listToVector(embedding)

            // Generate summary if article text is available
            var summary: String? = null
            if (!pageContent.articleText.isNullOrBlank() && pageContent.articleText!!.length > 500) {
                log.info("Generating summary for article")
                try {
                    summary = ollamaService.summarize(pageContent.articleText!!)
                } catch (e: Exception) {
                    log.error("Failed to generate summary", e)
                }
            }

            // Extract topics if we have enough text
            var topics: List<String>? = null
            if (!pageContent.articleText.isNullOrBlank() && pageContent.articleText!!.length > 200) {
                log.info("Extracting topics from article")
                try {
                    topics = ollamaService.extractTopics(pageContent.articleText!!, pageContent.title)
                } catch (e: Exception) {
                    log.error("Failed to extract topics", e)
                }
            }

            // Store AI metadata in internal_notes
            val aiMetadata = AIEnrichmentMetadata(
                summary = summary,
                topics = topics
            )
            pageContent.internalNotes = objectMapper.writeValueAsString(aiMetadata)

            // Persist changes
            pageContent.persist()

            log.info("Successfully enriched page content for thread ${request.threadId}: " +
                    "embedding=${embedding.size}d, " +
                    "summary=${summary != null}, " +
                    "topics=${topics?.size ?: 0}")

        } catch (e: Exception) {
            log.error("Error processing AI enrichment", e)

            // Record failure in DLQ for tracking and potential retry
            failedEventService.recordFailure(
                eventType = "AI_ENRICHMENT",
                eventPayload = message,
                error = e,
                sourceConsumer = "AIEnrichmentConsumer",
                threadId = threadId,
                kafkaTopic = "ai-enrichment-requests"
            )

            // Don't rethrow - we've recorded it for retry
        }
    }
}