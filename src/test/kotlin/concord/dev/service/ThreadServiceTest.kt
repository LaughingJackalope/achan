package concord.dev.service

import concord.dev.domain.*
import concord.dev.util.URLNormalizer
import io.mockk.every
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import jakarta.transaction.Transactional
import jakarta.transaction.TransactionSynchronizationRegistry
import jakarta.transaction.Synchronization
import jakarta.transaction.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@QuarkusTest
class ThreadServiceTest {

    @Inject
    lateinit var threadService: ThreadService

    private lateinit var mockKafkaProducer: KafkaProducerService
    private lateinit var mockUrlNormalizer: URLNormalizer

    @BeforeEach
    @Transactional
    fun setup() {
        // Clean database
        Thread.deleteAll()

        // Create mocks
        mockKafkaProducer = mockk<KafkaProducerService>(relaxed = true)
        mockUrlNormalizer = mockk<URLNormalizer>()

        // Mock URL normalization to return normalized URLs
        every { mockUrlNormalizer.normalize(any()) } answers { firstArg() }
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    @Transactional
    fun `createOrGetThread - creates new thread successfully`() {
        val url = "https://example.com/article"
        val slug = "test-article"

        // Create service with mocked dependencies
        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()

        val thread = service.createOrGetThread(url, slug)

        assertNotNull(thread)
        assertNotNull(thread.id)
        assertEquals(url, thread.url)
        assertEquals(slug, thread.slug)
        assertEquals(0, thread.postCount)
        assertEquals(CrawlStatus.PENDING, thread.crawlStatus)
        assertNotNull(thread.createdAt)
        assertNotNull(thread.updatedAt)

        // Verify URL normalization was called
        verify(exactly = 1) { mockUrlNormalizer.normalize(url) }

        // Verify Kafka message was sent
        verify(exactly = 1) { mockKafkaProducer.sendUrlCrawlRequest(ThreadId(thread.id), url) }
    }

    @Test
    @Transactional
    fun `createOrGetThread - returns existing thread on duplicate URL`() {
        val url = "https://example.com/duplicate"
        val slug = "original-slug"

        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()

        // Create first thread
        val firstThread = service.createOrGetThread(url, slug)
        val firstCreatedAt = firstThread.createdAt

        // Simulate time passing
        java.lang.Thread.sleep(10)

        // Try to create duplicate with different slug
        val secondThread = service.createOrGetThread(url, "different-slug")

        // Should return the same thread
        assertEquals(firstThread.id, secondThread.id)
        assertEquals(slug, secondThread.slug) // Original slug preserved
        assertEquals(firstCreatedAt, secondThread.createdAt) // createdAt unchanged
        assert(secondThread.updatedAt > firstCreatedAt) // updatedAt should be newer

        // Kafka should only be called once (for first creation)
        verify(exactly = 1) { mockKafkaProducer.sendUrlCrawlRequest(any(), any()) }
    }

    @Test
    @Transactional
    fun `createOrGetThread - handles URL normalization`() {
        val originalUrl = "HTTP://EXAMPLE.COM/Path"
        val normalizedUrl = "https://example.com/path"

        // Mock normalizer to return normalized version
        every { mockUrlNormalizer.normalize(originalUrl) } returns normalizedUrl

        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()
        val thread = service.createOrGetThread(originalUrl, null)

        assertEquals(normalizedUrl, thread.url)
        verify { mockUrlNormalizer.normalize(originalUrl) }
    }

    /**
     * Provide a minimal TransactionSynchronizationRegistry for tests that immediately
     * invokes afterCompletion(COMMITTED) upon registration, simulating a successful commit.
     */
    private fun immediateCommitTsr(): TransactionSynchronizationRegistry = object : TransactionSynchronizationRegistry {
        private val resources = mutableMapOf<Any, Any?>()
        private var rollbackOnly = false
        override fun getResource(key: Any?): Any? = resources[key!!]
        override fun putResource(key: Any?, value: Any?) { resources[key!!] = value }
        override fun registerInterposedSynchronization(sync: Synchronization?) {
            // Simulate commit immediately
            sync?.afterCompletion(Status.STATUS_COMMITTED)
        }
        override fun getTransactionKey(): Any? = Any()
        override fun getTransactionStatus(): Int = if (rollbackOnly) Status.STATUS_MARKED_ROLLBACK else Status.STATUS_ACTIVE
        override fun setRollbackOnly() { rollbackOnly = true }
        override fun getRollbackOnly(): Boolean = rollbackOnly
    }

    @Test
    @Transactional
    fun `createOrGetThread - handles null normalization result`() {
        val url = "invalid://url"

        // Mock normalizer to return null for invalid URL
        every { mockUrlNormalizer.normalize(url) } returns null

        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()
        val thread = service.createOrGetThread(url, null)

        // Should fall back to original URL
        assertEquals(url, thread.url)
    }

    @Test
    @Transactional
    fun `getThread - retrieves thread by ID`() {
        // Create a thread first
        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()
        val created = service.createOrGetThread("https://example.com/get-test", "get-by-id")

        // Retrieve by ID
        val retrieved = service.getThread(ThreadId(created.id))

        assertNotNull(retrieved)
        assertEquals(created.id, retrieved.id)
        assertEquals(created.url, retrieved.url)
        assertEquals(created.slug, retrieved.slug)
    }

    @Test
    @Transactional
    fun `getThread - returns null for non-existent ID`() {
        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()
        val nonExistentId = ThreadId.random()

        val result = service.getThread(nonExistentId)

        assertNull(result)
    }

    @Test
    @Transactional
    fun `getThreadByUrl - retrieves thread by normalized URL`() {
        val url = "https://example.com/url-test"

        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()
        val created = service.createOrGetThread(url, "url-lookup")

        // Retrieve by URL
        val retrieved = service.getThreadByUrl(url)

        assertNotNull(retrieved)
        assertEquals(created.id, retrieved.id)
        assertEquals(url, retrieved.url)
    }

    @Test
    @Transactional
    fun `getThreadByUrl - returns null for non-existent URL`() {
        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()

        val result = service.getThreadByUrl("https://non-existent.com/missing")

        assertNull(result)
    }

    @Test
    @Transactional
    fun `incrementPostCount - increments count and updates timestamp`() {
        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()
        val thread = service.createOrGetThread("https://example.com/increment-test", null)

        val initialCount = thread.postCount
        val initialUpdatedAt = thread.updatedAt

        // Sleep to ensure timestamp difference
        java.lang.Thread.sleep(10)

        // Increment post count
        service.incrementPostCount(ThreadId(thread.id))

        // Refresh thread from database
        val updated = service.getThread(ThreadId(thread.id))

        assertNotNull(updated)
        assertEquals(initialCount + 1, updated.postCount)
        assert(updated.updatedAt > initialUpdatedAt)
    }

    @Test
    @Transactional
    fun `incrementPostCount - throws exception for non-existent thread`() {
        val service = ThreadService(mockKafkaProducer, mockUrlNormalizer)
        service.tsr = immediateCommitTsr()
        val nonExistentId = ThreadId.random()

        try {
            service.incrementPostCount(nonExistentId)
            error("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Thread not found: ${nonExistentId.value}", e.message)
        }
    }
}
