package concord.dev.api.dto

import concord.dev.domain.PageContent
import concord.dev.domain.ThreadId
import java.time.Instant

data class PageContentResponse(
    val threadId: ThreadId,
    val url: String,
    val title: String?,
    val description: String?,
    val articleText: String?,
    val contentType: String?,
    val statusCode: Int,
    val fetchedAt: Instant,
    val contentHash: String?,
    val metadata: String?, // JSON string with OpenGraph, images, etc.
    val hasRawHtml: Boolean // Indicates if raw HTML is available (but don't send it by default)
) {
    companion object {
        fun from(content: PageContent) = PageContentResponse(
            threadId = content.threadId!!,
            url = content.url,
            title = content.title,
            description = content.description,
            articleText = content.articleText,
            contentType = content.contentType,
            statusCode = content.statusCode,
            fetchedAt = content.fetchedAt,
            contentHash = content.contentHash,
            metadata = content.metadata,
            hasRawHtml = content.rawHtml != null
        )
    }
}