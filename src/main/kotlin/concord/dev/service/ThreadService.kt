package concord.dev.service

import concord.dev.domain.*
import concord.dev.util.URLNormalizer
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import jakarta.transaction.TransactionSynchronizationRegistry
import jakarta.transaction.Synchronization
import jakarta.transaction.Status
import java.time.Instant

@ApplicationScoped
class ThreadService(
    private val kafkaProducer: KafkaProducerService,
    private val urlNormalizer: URLNormalizer
) {

    @jakarta.inject.Inject
    lateinit var tsr: TransactionSynchronizationRegistry

    @Transactional
    fun createOrGetThread(url: String, slug: String?): Thread {
        val normalizedUrlString = urlNormalizer.normalize(url) ?: url
        val normalizedUrl = Url(normalizedUrlString)

        val existing = Thread.findByUrl(normalizedUrl)
        if (existing != null) {
            // Update timestamp to indicate this thread was accessed again
            existing.updatedAt = Instant.now()
            existing.persist()
            return existing
        }

        val now = Instant.now()
        val thread = Thread().apply {
            this.id = ThreadId.random()
            this.url = normalizedUrl
            this.slug = slug
            this.createdAt = now
            this.updatedAt = now
            this.postCount = PostCount(0)
            this.crawlStatus = CrawlStatus.PENDING
        }

        thread.persist()

        // After successful commit, emit crawl request to avoid dual-write inconsistencies
        val createdThreadId = thread.id
        val createdThreadUrl = thread.url!!
        tsr.registerInterposedSynchronization(object : Synchronization {
            override fun beforeCompletion() {}
            override fun afterCompletion(status: Int) {
                if (status == Status.STATUS_COMMITTED) {
                    // Safe to emit now
                    kafkaProducer.sendUrlCrawlRequest(createdThreadId, createdThreadUrl)
                }
            }
        })

        return thread
    }

    fun getThread(threadId: ThreadId): Thread? {
        return Thread.find("id", threadId).firstResult()
    }

    fun getThreadByUrl(url: String): Thread? {
        val normalizedUrlString = urlNormalizer.normalize(url) ?: url
        val normalizedUrl = Url(normalizedUrlString)
        return Thread.findByUrl(normalizedUrl)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    fun incrementPostCount(threadId: ThreadId) {
        val thread = Thread.find("id", threadId).firstResult() ?: throw IllegalArgumentException("Thread not found: $threadId")
        thread.postCount = thread.postCount.inc()
        thread.updatedAt = Instant.now()
        thread.persist()
    }
}