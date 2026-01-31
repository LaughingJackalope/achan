package concord.dev.api

import concord.dev.domain.ThreadId
import concord.dev.service.ThreadService
import jakarta.transaction.Transactional
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import org.jboss.logging.Logger

/**
 * Admin endpoints for managing the achan instance
 *
 * WARNING: These endpoints should be protected in production!
 * Consider adding authentication/authorization before deploying.
 */
@Path("/api/v1/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AdminResource(
    private val threadService: ThreadService
) {
    private val log: Logger = Logger.getLogger(AdminResource::class.java)

    /**
     * Bulk seed URLs to populate the database and trigger crawling/AI enrichment
     *
     * POST /api/v1/admin/seed-urls
     * {
     *   "urls": [
     *     "https://news.ycombinator.com/item?id=123",
     *     "https://github.com/anthropics/claude",
     *     ...
     *   ]
     * }
     *
     * This endpoint:
     * 1. Creates threads for each URL (or returns existing thread)
     * 2. Triggers crawl pipeline (URL normalization → crawl → AI enrichment)
     * 3. Returns thread IDs for tracking
     *
     * Use this for:
     * - Demonstrating semantic search with curated content
     * - Testing search quality across different topics
     * - Seeding initial data for development/testing
     */
    @POST
    @Path("/seed-urls")
    @Transactional
    fun seedUrls(request: SeedUrlsRequest): SeedUrlsResponse {
        if (request.urls.isEmpty()) {
            throw BadRequestException("URLs list cannot be empty")
        }

        if (request.urls.size > 100) {
            throw BadRequestException("Maximum 100 URLs per request")
        }

        log.info("Seeding ${request.urls.size} URLs")

        val results = request.urls.map { url ->
            try {
                val thread = threadService.createOrGetThread(url, null)

                UrlSeedResult(
                    url = url,
                    threadId = ThreadId(thread.id),
                    status = if (thread.createdAt == thread.updatedAt) "created" else "existing",
                    error = null
                )
            } catch (e: Exception) {
                log.error("Error seeding URL: $url", e)
                UrlSeedResult(
                    url = url,
                    threadId = null,
                    status = "failed",
                    error = e.message
                )
            }
        }

        val successCount = results.count { it.status != "failed" }
        val createdCount = results.count { it.status == "created" }
        val existingCount = results.count { it.status == "existing" }
        val failedCount = results.count { it.status == "failed" }

        log.info("Seeding complete: $createdCount created, $existingCount existing, $failedCount failed")

        return SeedUrlsResponse(
            totalRequested = request.urls.size,
            created = createdCount,
            existing = existingCount,
            failed = failedCount,
            results = results
        )
    }
}

// DTOs
data class SeedUrlsRequest(
    val urls: List<String>
)

data class SeedUrlsResponse(
    val totalRequested: Int,
    val created: Int,
    val existing: Int,
    val failed: Int,
    val results: List<UrlSeedResult>
)

data class UrlSeedResult(
    val url: String,
    val threadId: ThreadId?,
    val status: String,  // "created", "existing", "failed"
    val error: String?
)