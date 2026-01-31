package concord.dev.api.dto

import concord.dev.domain.CrawlStatus
import concord.dev.service.SearchResult
import java.time.Instant
import java.util.UUID

/**
 * Request parameters for thread search
 */
data class ThreadSearchRequest(
    val query: String,
    val limit: Int = 10,
    val page: Int = 0,
    val threshold: Double = 0.7,
    val filters: SearchFilters? = null
)

/**
 * Optional filters for search results
 */
data class SearchFilters(
    val dateFrom: Instant? = null,
    val dateTo: Instant? = null,
    val minPostCount: Int? = null,
    val maxPostCount: Int? = null,
    val crawlStatus: CrawlStatus? = null,
    val hasAgentPosts: Boolean? = null  // Filter threads with/without agent participation
)

/**
 * Search result for a single thread
 */
data class ThreadSearchResultDto(
    val threadId: UUID,
    val url: String,
    val title: String?,
    val description: String?,
    val similarity: Double,
    val postCount: Int,
    val snippet: String?,
    val crawlStatus: String,
    val createdAt: Instant,
    val lastActivityAt: Instant,
    val hasAgentPosts: Boolean = false
) {
    companion object {
        fun from(
            searchResult: SearchResult,
            postCount: Int,
            crawlStatus: CrawlStatus,
            createdAt: Instant,
            updatedAt: Instant,
            hasAgentPosts: Boolean = false
        ): ThreadSearchResultDto {
            return ThreadSearchResultDto(
                threadId = searchResult.threadId,
                url = searchResult.url,
                title = searchResult.title,
                description = searchResult.description,
                similarity = searchResult.similarity,
                postCount = postCount,
                snippet = searchResult.snippet,
                crawlStatus = crawlStatus.name,
                createdAt = createdAt,
                lastActivityAt = updatedAt,
                hasAgentPosts = hasAgentPosts
            )
        }
    }
}

/**
 * Paginated search results response
 */
data class ThreadSearchResponse(
    val query: String,
    val results: List<ThreadSearchResultDto>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val hasNextPage: Boolean,
    val threshold: Double
)
