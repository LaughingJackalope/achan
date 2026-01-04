package concord.dev.service

/**
 * Result of crawling a URL with extracted content and metadata
 */
data class CrawlResult(
    val url: String,
    val statusCode: Int,
    val contentType: String?,
    val title: String?,
    val description: String?,
    val articleText: String?,
    val rawHtml: String?,
    val images: List<String>,
    val metadata: CrawlMetadata,
    val error: String? = null,
    val durationMs: Long
)

/**
 * Structured metadata extracted from HTML (OpenGraph, Twitter cards, etc.)
 */
data class CrawlMetadata(
    val openGraph: Map<String, String> = emptyMap(),
    val twitterCard: Map<String, String> = emptyMap(),
    val canonical: String? = null,
    val author: String? = null,
    val publishedAt: String? = null,
    val siteName: String? = null,
    val language: String? = null
)