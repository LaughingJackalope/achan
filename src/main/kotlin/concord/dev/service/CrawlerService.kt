package concord.dev.service

import jakarta.enterprise.context.ApplicationScoped
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import kotlin.system.measureTimeMillis

@ApplicationScoped
class CrawlerService {

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    private val userAgent = "AChan/1.0 (+https://github.com/yourusername/achan) Mozilla/5.0"

    /**
     * Crawl a URL and extract content with metadata
     */
    fun crawl(url: String): CrawlResult {
        var statusCode = 0
        var contentType: String? = null
        var error: String? = null
        var rawHtml: String? = null

        val duration = measureTimeMillis {
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build()

                val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
                statusCode = response.statusCode()
                contentType = response.headers().firstValue("content-type").orElse(null)
                rawHtml = response.body()

                // Limit HTML size to avoid memory issues (10MB limit)
                if (rawHtml != null && rawHtml!!.length > 10_000_000) {
                    rawHtml = rawHtml!!.substring(0, 10_000_000)
                }

            } catch (e: Exception) {
                error = e.message ?: e::class.simpleName
                statusCode = if (statusCode == 0) 999 else statusCode // 999 = client error
            }
        }

        // Parse HTML if we got a successful response
        return if (statusCode in 200..299 && rawHtml != null) {
            parseHtml(url, statusCode, contentType, rawHtml!!, duration)
        } else {
            CrawlResult(
                url = url,
                statusCode = statusCode,
                contentType = contentType,
                title = null,
                description = null,
                articleText = null,
                rawHtml = null,
                images = emptyList(),
                metadata = CrawlMetadata(),
                error = error,
                durationMs = duration
            )
        }
    }

    /**
     * Parse HTML and extract structured content
     */
    private fun parseHtml(url: String, statusCode: Int, contentType: String?, html: String, duration: Long): CrawlResult {
        val doc = Jsoup.parse(html, url)

        // Extract metadata
        val metadata = extractMetadata(doc)

        // Extract title (priority: og:title > twitter:title > <title>)
        val title = metadata.openGraph["title"]
            ?: metadata.twitterCard["title"]
            ?: doc.title()

        // Extract description
        val description = metadata.openGraph["description"]
            ?: metadata.twitterCard["description"]
            ?: doc.select("meta[name=description]").attr("content")

        // Extract article text (main content)
        val articleText = extractArticleText(doc)

        // Extract images
        val images = extractImages(doc, metadata)

        return CrawlResult(
            url = url,
            statusCode = statusCode,
            contentType = contentType,
            title = title.takeIf { it.isNotBlank() },
            description = description.takeIf { it.isNotBlank() },
            articleText = articleText.takeIf { it.isNotBlank() },
            rawHtml = html,
            images = images,
            metadata = metadata,
            error = null,
            durationMs = duration
        )
    }

    /**
     * Extract OpenGraph, Twitter Card, and other metadata
     */
    private fun extractMetadata(doc: Document): CrawlMetadata {
        val openGraph = mutableMapOf<String, String>()
        val twitterCard = mutableMapOf<String, String>()

        // Extract OpenGraph tags
        doc.select("meta[property^=og:]").forEach { element ->
            val property = element.attr("property").removePrefix("og:")
            val content = element.attr("content")
            if (content.isNotBlank()) {
                openGraph[property] = content
            }
        }

        // Extract Twitter Card tags
        doc.select("meta[name^=twitter:]").forEach { element ->
            val name = element.attr("name").removePrefix("twitter:")
            val content = element.attr("content")
            if (content.isNotBlank()) {
                twitterCard[name] = content
            }
        }

        // Extract other useful metadata
        val canonical = doc.select("link[rel=canonical]").attr("href")
        val author = doc.select("meta[name=author]").attr("content")
        val publishedAt = doc.select("meta[property=article:published_time]").attr("content")
            .ifBlank { doc.select("meta[name=publication_date]").attr("content") }
        val siteName = openGraph["site_name"]
        val language = doc.select("html").attr("lang")

        return CrawlMetadata(
            openGraph = openGraph,
            twitterCard = twitterCard,
            canonical = canonical.takeIf { it.isNotBlank() },
            author = author.takeIf { it.isNotBlank() },
            publishedAt = publishedAt.takeIf { it.isNotBlank() },
            siteName = siteName,
            language = language.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Extract main article text using common content selectors
     */
    private fun extractArticleText(doc: Document): String {
        // Try common article containers (in priority order)
        val selectors = listOf(
            "article",
            "[role=main]",
            "main",
            ".article-content",
            ".post-content",
            ".entry-content",
            "#content",
            ".content"
        )

        for (selector in selectors) {
            val element = doc.select(selector).firstOrNull()
            if (element != null) {
                val text = element.text()
                if (text.length > 100) { // Minimum length to be considered main content
                    return text.take(50000) // Limit to 50k chars
                }
            }
        }

        // Fallback: get body text but remove nav/footer/header
        doc.select("nav, footer, header, aside, script, style, iframe").remove()
        return doc.body().text().take(50000)
    }

    /**
     * Extract image URLs from metadata and content
     */
    private fun extractImages(doc: Document, metadata: CrawlMetadata): List<String> {
        val images = mutableListOf<String>()

        // Priority: OpenGraph image > Twitter card image
        metadata.openGraph["image"]?.let { images.add(it) }
        metadata.twitterCard["image"]?.let { images.add(it) }

        // Extract images from article content
        doc.select("article img, main img, .article-content img").forEach { img ->
            val src = img.attr("abs:src")
            if (src.isNotBlank() && !images.contains(src)) {
                images.add(src)
            }
        }

        return images.take(10) // Limit to 10 images
    }

    /**
     * Calculate SHA-256 hash of content for change detection
     */
    fun calculateContentHash(content: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}