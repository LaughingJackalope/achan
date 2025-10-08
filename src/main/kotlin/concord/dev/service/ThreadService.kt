package concord.dev.service

import concord.dev.domain.CrawlStatus
import concord.dev.domain.Thread
import concord.dev.util.URLNormalizer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.UUID

@ApplicationScoped
class ThreadService(
    private val kafkaProducer: KafkaProducerService,
    private val urlNormalizer: URLNormalizer
) {

    @Transactional
    fun createOrGetThread(url: String, slug: String?): Thread {
        val normalizedUrl = urlNormalizer.normalize(url) ?: url

        val existing = Thread.findByUrl(normalizedUrl)
        if (existing != null) {
            return existing
        }

        val thread = Thread().apply {
            this.id = UUID.randomUUID()
            this.url = normalizedUrl
            this.slug = slug
            this.createdAt = Instant.now()
            this.updatedAt = Instant.now()
            this.postCount = 0
            this.crawlStatus = CrawlStatus.PENDING
        }

        thread.persist()

        kafkaProducer.sendUrlCrawlRequest(thread.id, normalizedUrl)

        return thread
    }

    fun getThread(threadId: UUID): Thread? {
        return Thread.find("id", threadId).firstResult()
    }

    fun getThreadByUrl(url: String): Thread? {
        val normalizedUrl = urlNormalizer.normalize(url) ?: url
        return Thread.findByUrl(normalizedUrl)
    }

    @Transactional
    fun incrementPostCount(threadId: UUID) {
        val thread = Thread.find("id", threadId).firstResult() ?: throw IllegalArgumentException("Thread not found: $threadId")
        thread.postCount++
        thread.updatedAt = Instant.now()
        thread.persist()
    }
}