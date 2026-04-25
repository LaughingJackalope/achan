package concord.dev.service

import concord.dev.domain.*
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.hibernate.exception.ConstraintViolationException
import org.jboss.logging.Logger
import java.time.Instant

class MissingThreadException(message: String) : RuntimeException(message)

@ApplicationScoped
class PageContentService {
    private val log: Logger = Logger.getLogger(PageContentService::class.java)

    @Transactional
    fun storeContent(
        threadId: ThreadId,
        url: String,
        title: String?,
        description: String?,
        articleText: String?,
        rawHtml: String?,
        contentType: String?,
        statusCode: Int,
        contentHash: String?,
        metadata: String?
    ): PageContent {
        ensureThreadExists(threadId)

        // Check if content already exists for this thread
        val existing = PageContent.findByThreadId(threadId)

        if (existing != null) {
            // Update existing content
            existing.url = url
            existing.title = title
            existing.description = description
            existing.articleText = articleText
            existing.rawHtml = rawHtml
            existing.contentType = contentType
            existing.statusCode = statusCode
            existing.fetchedAt = Instant.now()
            existing.contentHash = contentHash
            existing.metadata = metadata
            persistSafely(existing, threadId)

            // Update thread status
            updateThreadStatus(threadId, statusCode)

            return existing
        }

        // Create new content
        val content = PageContent().apply {
            this.threadId = threadId
            this.url = url
            this.title = title
            this.description = description
            this.articleText = articleText
            this.rawHtml = rawHtml
            this.contentType = contentType
            this.statusCode = statusCode
            this.fetchedAt = Instant.now()
            this.contentHash = contentHash
            this.metadata = metadata
        }

        persistSafely(content, threadId)

        // Update thread status
        updateThreadStatus(threadId, statusCode)

        return content
    }

    @Transactional
    fun getContent(threadId: ThreadId): PageContent? {
        return PageContent.findByThreadId(threadId)
    }

    @Transactional
    fun deleteContent(threadId: ThreadId): Boolean {
        val content = PageContent.findByThreadId(threadId) ?: return false
        content.delete()
        return true
    }

    private fun updateThreadStatus(threadId: ThreadId, statusCode: Int) {
        val thread = Thread.find("id", threadId.value).firstResult()
            ?: throw IllegalArgumentException("Thread not found: $threadId")

        thread.crawlStatus = when (statusCode) {
            in 200..299 -> CrawlStatus.COMPLETE
            in 400..499 -> CrawlStatus.BLOCKED
            else -> CrawlStatus.FAILED
        }
        thread.updatedAt = Instant.now()
        thread.persist()
    }

    private fun ensureThreadExists(threadId: ThreadId) {
        val exists = Thread.find("id", threadId.value).firstResult() != null
        if (!exists) {
            throw MissingThreadException("Thread not found for page content: $threadId")
        }
    }

    private fun persistSafely(content: PageContent, threadId: ThreadId) {
        try {
            content.persist()
        } catch (e: ConstraintViolationException) {
            if (e.constraintName == "page_content_thread_id_fkey") {
                log.error("Cannot persist PageContent: thread does not exist yet (threadId=$threadId)", e)
            }
            throw e
        }
    }
}
