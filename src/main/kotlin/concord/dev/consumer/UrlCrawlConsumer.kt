package concord.dev.consumer

import concord.dev.domain.CrawlStatus
import concord.dev.domain.Thread
import concord.dev.util.URLNormalizer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import java.net.URL
import java.time.Duration
import java.util.*
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.lang.Thread as JavaThread

/**
 * Consumes URLCrawlRequest messages from the Kafka topic and processes each URL.
 * After crawling, updates the associated thread's crawl_status and metadata.
 *
 * Design notes:
 * - Uses Quarkus Kafka client (quarkus-kafka-client) for consumption
 * - Normalizes the URL before database lookup (deduplication)
 * - Handles crawl success/failure with proper status updates
 * - Metadata JSONB stores extracted page data (title, description, etc.)
 * - Consumer must be started/stopped manually or via Quarkus lifecycle
 */
@ApplicationScoped
class UrlCrawlConsumer(

    // Inject URLNormalizer for normalizing URLs before database lookup
    private val urlNormalizer: URLNormalizer,

    // Kafka bootstrap servers
    @Inject
    private val bootstrapServers: String = "localhost:9092",

    // Kafka consumer group ID
    @Inject
    private val groupId: String = "achan-crawler-group"
) {

    private lateinit var consumer: KafkaConsumer<String, String>

    /** Start the Kafka consumer polling loop. Call from Quarkus startup or a scheduled task. */
    @Transactional
    fun startConsuming() {
        val props = Properties().apply {
            put("bootstrap.servers", bootstrapServers)
            put("group.id", groupId)
            put("key.deserializer", StringDeserializer::class.java.name)
            put("value.deserializer", StringDeserializer::class.java.name)
            put("auto.offset.reset", "earliest")
            put("max.poll.records", "10")
        }
        consumer = KafkaConsumer(props)
        consumer.subscribe(listOf("url-crawl-requests"))

        while (true) {
            try {
                val records = consumer.poll(Duration.ofMillis(1000))
                for (record in records) {
                    processSingleMessage(record.value())
                }
            } catch (e: Exception) {
                JavaThread.sleep(5000)
            }
        }
    }

    private fun processSingleMessage(jsonMessage: String) {
        val request = parseUrlCrawlRequest(jsonMessage)
        if (request == null) {
            println("Invalid URL crawl request: $jsonMessage")
            return
        }

        val normalizedUrl = urlNormalizer.normalize(request.url) ?: request.url
        val thread = Thread.find("url", normalizedUrl).firstResult()
            ?: throw IllegalStateException("Thread not found for url $normalizedUrl")

        try {
            val crawled = crawlUrl(normalizedUrl)
            thread.crawlStatus = CrawlStatus.COMPLETE
            thread.metadata = crawlDataToJson(crawled)
            thread.updatedAt = java.time.Instant.now()
            thread.persist()
            println("Crawl completed for thread ${thread.id} url ${thread.url}")
        } catch (e: Exception) {
            println("Crawl failed for thread ${thread.id} url ${thread.url}: ${e.message}")
            thread.crawlStatus = CrawlStatus.FAILED
            thread.updatedAt = java.time.Instant.now()
            thread.persist()
        }
    }

    private fun parseUrlCrawlRequest(json: String): UrlCrawlRequest? {
        try {
            val threadId = extractJsonField(json, "threadId")
            val url = extractJsonField(json, "url")
            val requestedAt = extractJsonField(json, "requestedAt")
            if (threadId.isNullOrEmpty() || url.isNullOrEmpty()) return null
            return UrlCrawlRequest(threadId = threadId, url = url, requestedAt = requestedAt)
        } catch (e: Exception) {
            println("Failed to parse URL crawl request: ${e.message}")
            return null
        }
    }

    private fun extractJsonField(json: String, field: String): String {
        val pattern = Pattern.quote(field) + "\\s*:\\s*\"([^\"]+)\""
        val regex = Pattern.compile(pattern)
        val matcher = regex.matcher(json)
        return if (matcher.find()) matcher.group(1) else ""
    }

    /** Simple HTTP crawl that extracts title and meta description. */
    @Transactional
    private fun crawlUrl(url: String): CrawlResult {
        return try {
            val connection = URL(url).openConnection() as java.net.HttpURLConnection
            connection.setRequestMethod("GET")
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.doInput = true
            val input = connection.inputStream
            val content = input.bufferedReader().use { reader ->
                reader.lines().reduce("") { acc, line -> acc + "\n$line" }
            }
            input.close()
            val title = extractTitle(content)
            val description = extractMetaDescription(content)
            CrawlResult(title = title, description = description)
        } catch (e: Exception) {
            CrawlResult()
        }
    }

    /** Extract the <title>...</title> content from HTML. */
    private fun extractTitle(html: String): String {
        val pattern = Pattern.compile("<title[^>]*>([^<]+)</title>")
        val matcher: Matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else ""
    }

    /** Extract the content of <meta name="description" ...> from HTML. */
    private fun extractMetaDescription(html: String): String {
        val pattern = Pattern.compile("<meta[^>]*name=[\"']description[\"'][^>]*content=[\"']([^\"']+)[\"']")
        val matcher: Matcher = pattern.matcher(html)
        return if (matcher.find()) matcher.group(1) else ""
    }

    private fun crawlDataToJson(crawlData: CrawlResult): String {
        val title = if (crawlData.title.isEmpty()) "Untitled" else crawlData.title
        val description = if (crawlData.description.isEmpty()) "" else crawlData.description
        return """{"title":"$title","description":"$description"}"""
    }
}

/** Kafka message format for crawl requests (must match producer). */
data class UrlCrawlRequest(
    val threadId: String,
    val url: String,
    val requestedAt: String
)

/** Result of crawling a URL. */
data class CrawlResult(
    val title: String = "",
    val description: String = ""
)

/** Optional response message for observability/consumers downstream. */
data class CrawlResponse(
    val threadId: String,
    val url: String,
    val status: CrawlStatus,
    val title: String?,
    val description: String?
)