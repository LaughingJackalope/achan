package concord.dev.api

import concord.dev.api.dto.SearchFilters
import concord.dev.api.dto.ThreadSearchResponse
import concord.dev.api.dto.ThreadSearchResultDto
import concord.dev.domain.CrawlStatus
import concord.dev.domain.Thread
import concord.dev.domain.ThreadId
import concord.dev.service.SearchService
import io.quarkus.logging.Log
import jakarta.persistence.EntityManager
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.time.Instant

@Path("/api/v1/threads/search")
@Produces(MediaType.APPLICATION_JSON)
class ThreadSearchResource(
    private val searchService: SearchService,
    private val entityManager: EntityManager
) {

    /**
     * Semantic search for threads
     * GET /api/v1/threads/search?query=machine+learning&limit=10&threshold=0.7
     */
    @GET
    fun searchThreads(
        @QueryParam("query") query: String?,
        @QueryParam("limit") @DefaultValue("10") limit: Int,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("threshold") @DefaultValue("0.7") threshold: Double,
        @QueryParam("dateFrom") dateFrom: String?,
        @QueryParam("dateTo") dateTo: String?,
        @QueryParam("minPostCount") minPostCount: Int?,
        @QueryParam("maxPostCount") maxPostCount: Int?,
        @QueryParam("crawlStatus") crawlStatusStr: String?,
        @QueryParam("hasAgentPosts") hasAgentPosts: Boolean?
    ): Response {
        // Validate query parameter
        if (query.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Query parameter is required"))
                .build()
        }

        Log.infof("[ThreadSearch] query=%s, limit=%d, page=%d, threshold=%.2f", 
            query, limit, page, threshold)

        // Validate and coerce parameters
        val validatedLimit = limit.coerceIn(1, 100)
        val validatedPage = page.coerceAtLeast(0)
        val validatedThreshold = threshold.coerceIn(0.0, 1.0)

        try {
            // Parse filters
            val filters = SearchFilters(
                dateFrom = dateFrom?.let { Instant.parse(it) },
                dateTo = dateTo?.let { Instant.parse(it) },
                minPostCount = minPostCount,
                maxPostCount = maxPostCount,
                crawlStatus = crawlStatusStr?.let { CrawlStatus.valueOf(it.uppercase()) },
                hasAgentPosts = hasAgentPosts
            )

            // Perform semantic search
            // Note: pagination is done in-memory for MVP, could be optimized later
            val searchResults = searchService.searchByText(
                query = query,
                limit = validatedLimit * (validatedPage + 1) + 50, // Fetch extra for filtering
                similarityThreshold = validatedThreshold
            )

            Log.infof("[ThreadSearch] Found %d initial results", searchResults.size)

            // Enrich results with thread metadata and apply filters
            val enrichedResults = searchResults.mapNotNull { searchResult ->
                val thread = Thread.find("id", ThreadId(searchResult.threadId)).firstResult()
                if (thread == null) {
                    Log.warnf("[ThreadSearch] Thread not found: %s", searchResult.threadId)
                    return@mapNotNull null
                }

                // Apply filters
                if (!matchesFilters(thread, filters)) {
                    return@mapNotNull null
                }

                // Check for agent posts if filter is set
                val hasAgentPostsActual = if (filters.hasAgentPosts != null) {
                    checkHasAgentPosts(thread.id)
                } else false

                if (filters.hasAgentPosts == true && !hasAgentPostsActual) {
                    return@mapNotNull null
                }
                if (filters.hasAgentPosts == false && hasAgentPostsActual) {
                    return@mapNotNull null
                }

                ThreadSearchResultDto.from(
                    searchResult = searchResult,
                    postCount = thread.postCount ?: 0,
                    crawlStatus = thread.crawlStatus,
                    createdAt = thread.createdAt,
                    updatedAt = thread.updatedAt,
                    hasAgentPosts = hasAgentPostsActual
                )
            }

            Log.infof("[ThreadSearch] After filtering: %d results", enrichedResults.size)

            // Apply pagination
            val startIndex = validatedPage * validatedLimit
            val paginatedResults = enrichedResults
                .drop(startIndex)
                .take(validatedLimit)

            val response = ThreadSearchResponse(
                query = query,
                results = paginatedResults,
                total = enrichedResults.size,
                page = validatedPage,
                limit = validatedLimit,
                hasNextPage = enrichedResults.size > (startIndex + validatedLimit),
                threshold = validatedThreshold
            )

            return Response.ok(response).build()

        } catch (e: IllegalArgumentException) {
            Log.errorf(e, "[ThreadSearch] Invalid parameter")
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid parameter: ${e.message}"))
                .build()
        } catch (e: Exception) {
            Log.errorf(e, "[ThreadSearch] Search failed")
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Search failed: ${e.message}"))
                .build()
        }
    }

    /**
     * Check if thread matches filters
     */
    private fun matchesFilters(thread: Thread, filters: SearchFilters): Boolean {
        // Date filters
        if (filters.dateFrom != null && thread.createdAt.isBefore(filters.dateFrom)) {
            return false
        }
        if (filters.dateTo != null && thread.createdAt.isAfter(filters.dateTo)) {
            return false
        }

        // Post count filters
        val postCount = thread.postCount ?: 0
        if (filters.minPostCount != null && postCount < filters.minPostCount) {
            return false
        }
        if (filters.maxPostCount != null && postCount > filters.maxPostCount) {
            return false
        }

        // Crawl status filter
        if (filters.crawlStatus != null && thread.crawlStatus != filters.crawlStatus) {
            return false
        }

        return true
    }

    /**
     * Check if thread has any agent posts
     */
    private fun checkHasAgentPosts(threadId: java.util.UUID): Boolean {
        val sql = """
            SELECT COUNT(*) > 0
            FROM post
            WHERE thread_id = :threadId
              AND agent_id IS NOT NULL
        """.trimIndent()

        val result = entityManager.createNativeQuery(sql)
            .setParameter("threadId", threadId)
            .singleResult

        return result as Boolean
    }
}
