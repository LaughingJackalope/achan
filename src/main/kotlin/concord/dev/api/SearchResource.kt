package concord.dev.api

import concord.dev.service.SearchService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Path("/api/v1/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SearchResource(
    private val searchService: SearchService
) {

    /**
     * Semantic search across all content
     * GET /api/v1/search?q=machine+learning&limit=10&threshold=0.7
     */
    @GET
    fun search(
        @QueryParam("q") query: String?,
        @QueryParam("limit") @DefaultValue("10") limit: Int,
        @QueryParam("threshold") @DefaultValue("0.7") threshold: Double
    ): SearchResponse {
        if (query.isNullOrBlank()) {
            throw BadRequestException("Query parameter 'q' is required")
        }

        if (limit < 1 || limit > 100) {
            throw BadRequestException("Limit must be between 1 and 100")
        }

        if (threshold < 0.0 || threshold > 1.0) {
            throw BadRequestException("Threshold must be between 0.0 and 1.0")
        }

        val results = searchService.searchByText(query, limit, threshold)

        return SearchResponse(
            query = query,
            results = results.map { result ->
                SearchResultDto(
                    threadId = result.threadId,
                    url = result.url,
                    title = result.title,
                    description = result.description,
                    snippet = result.snippet,
                    similarity = result.similarity
                )
            },
            count = results.size,
            limit = limit,
            threshold = threshold
        )
    }

    /**
     * Find similar threads
     * GET /api/v1/search/similar/{threadId}?limit=5&threshold=0.7
     */
    @GET
    @Path("/similar/{threadId}")
    fun findSimilar(
        @PathParam("threadId") threadId: UUID,
        @QueryParam("limit") @DefaultValue("5") limit: Int,
        @QueryParam("threshold") @DefaultValue("0.7") threshold: Double
    ): SimilarThreadsResponse {
        if (limit < 1 || limit > 50) {
            throw BadRequestException("Limit must be between 1 and 50")
        }

        if (threshold < 0.0 || threshold > 1.0) {
            throw BadRequestException("Threshold must be between 0.0 and 1.0")
        }

        val results = searchService.findSimilarThreads(threadId, limit, threshold)

        return SimilarThreadsResponse(
            threadId = threadId,
            similarThreads = results.map { result ->
                SearchResultDto(
                    threadId = result.threadId,
                    url = result.url,
                    title = result.title,
                    description = result.description,
                    snippet = result.snippet,
                    similarity = result.similarity
                )
            },
            count = results.size,
            limit = limit,
            threshold = threshold
        )
    }

    /**
     * Get embedding statistics
     * GET /api/v1/search/stats
     */
    @GET
    @Path("/stats")
    fun getStats(): EmbeddingStatsResponse {
        val stats = searchService.getEmbeddingStats()

        return EmbeddingStatsResponse(
            totalContent = stats.totalContent,
            embeddedContent = stats.embeddedContent,
            missingEmbeddings = stats.missingEmbeddings,
            coveragePercent = if (stats.totalContent > 0) {
                (stats.embeddedContent.toDouble() / stats.totalContent * 100)
            } else {
                0.0
            }
        )
    }
}

// DTOs
data class SearchResponse(
    val query: String,
    val results: List<SearchResultDto>,
    val count: Int,
    val limit: Int,
    val threshold: Double
)

data class SimilarThreadsResponse(
    val threadId: UUID,
    val similarThreads: List<SearchResultDto>,
    val count: Int,
    val limit: Int,
    val threshold: Double
)

data class SearchResultDto(
    val threadId: UUID,
    val url: String,
    val title: String?,
    val description: String?,
    val snippet: String?,
    val similarity: Double
)

data class EmbeddingStatsResponse(
    val totalContent: Long,
    val embeddedContent: Long,
    val missingEmbeddings: Long,
    val coveragePercent: Double
)