package concord.dev.api.dto

import concord.dev.domain.CrawlStatus
import concord.dev.domain.Thread
import java.time.Instant
import java.util.UUID

data class CreateThreadRequest(
    val url: String,
    val slug: String? = null
)

data class ThreadResponse(
    val threadId: UUID,
    val url: String,
    val slug: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val postCount: Int,
    val crawlStatus: CrawlStatus,
    val metadata: String?
) {
    companion object {
        fun from(thread: Thread) = ThreadResponse(
            threadId = thread.id,
            url = thread.url,
            slug = thread.slug,
            createdAt = thread.createdAt,
            updatedAt = thread.updatedAt,
            postCount = thread.postCount,
            crawlStatus = thread.crawlStatus,
            metadata = thread.metadata
        )
    }
}