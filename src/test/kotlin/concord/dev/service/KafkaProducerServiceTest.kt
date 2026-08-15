package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.*
import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KafkaProducerServiceTest {

    private lateinit var mockProducer: KafkaProducer<String, String>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var service: KafkaProducerService

    private val topic = "test-url-crawl-requests"

    @BeforeEach
    fun setup() {
        mockProducer = mockk<KafkaProducer<String, String>>(relaxed = true)
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        service = KafkaProducerService(topic, mockProducer, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `sendUrlCrawlRequest - sends message to Kafka with correct topic and key`() {
        val threadId = UUID.randomUUID()
        val url = "https://example.com/article"

        val recordSlot = slot<ProducerRecord<String, String>>()
        val callbackSlot = slot<Callback>()

        // Mock the send method
        every {
            mockProducer.send(capture(recordSlot), capture(callbackSlot))
        } returns mockk<Future<RecordMetadata>>(relaxed = true)

        service.sendUrlCrawlRequest(threadId, url)

        // Verify send was called
        verify(exactly = 1) { mockProducer.send(any(), any()) }

        // Verify the record
        val capturedRecord = recordSlot.captured
        assertEquals(topic, capturedRecord.topic())
        assertEquals(threadId.toString(), capturedRecord.key())
    }

    @Test
    fun `sendUrlCrawlRequest - message contains correct JSON structure`() {
        val threadId = UUID.randomUUID()
        val url = "https://example.com/test"

        val recordSlot = slot<ProducerRecord<String, String>>()

        every {
            mockProducer.send(capture(recordSlot), any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)

        service.sendUrlCrawlRequest(threadId, url)

        val capturedRecord = recordSlot.captured
        val messageJson = capturedRecord.value()

        // Parse the JSON message
        val messageMap = objectMapper.readValue(messageJson, Map::class.java)

        assertEquals(threadId.toString(), messageMap["threadId"])
        assertEquals(url, messageMap["url"])
        assertTrue(messageMap.containsKey("requestedAt"))

        // Verify requestedAt is a valid ISO-8601 timestamp
        val requestedAt = messageMap["requestedAt"] as String
        Instant.parse(requestedAt) // Will throw if invalid
    }

    @Test
    fun `sendUrlCrawlRequest - callback handles success`() {
        val threadId = UUID.randomUUID()
        val url = "https://example.com/success"

        val callbackSlot = slot<Callback>()

        every {
            mockProducer.send(any(), capture(callbackSlot))
        } answers {
            // Simulate successful send by invoking callback with metadata and no exception
            val callback = callbackSlot.captured
            callback.onCompletion(mockk<RecordMetadata>(relaxed = true), null)
            mockk<Future<RecordMetadata>>(relaxed = true)
        }

        // Should not throw
        service.sendUrlCrawlRequest(threadId, url)

        verify(exactly = 1) { mockProducer.send(any(), any()) }
    }

    @Test
    fun `sendUrlCrawlRequest - callback handles error gracefully`() {
        val threadId = UUID.randomUUID()
        val url = "https://example.com/error"

        val callbackSlot = slot<Callback>()

        every {
            mockProducer.send(any(), capture(callbackSlot))
        } answers {
            // Simulate error by invoking callback with exception
            val callback = callbackSlot.captured
            callback.onCompletion(null, RuntimeException("Kafka send failed"))
            mockk<Future<RecordMetadata>>(relaxed = true)
        }

        // Should not throw - errors are logged but not propagated
        service.sendUrlCrawlRequest(threadId, url)

        verify(exactly = 1) { mockProducer.send(any(), any()) }
    }

    @Test
    fun `sendUrlCrawlRequest - handles synchronous producer failure gracefully`() {
        every {
            mockProducer.send(any(), any())
        } throws org.apache.kafka.common.errors.TimeoutException("Kafka metadata unavailable")

        service.sendUrlCrawlRequest(UUID.randomUUID(), "https://example.com/unavailable")

        verify(exactly = 1) { mockProducer.send(any(), any()) }
    }

    @Test
    fun `sendUrlCrawlRequest - handles special characters in URL`() {
        val threadId = UUID.randomUUID()
        val url = "https://example.com/path?query=value&foo=bar#fragment"

        val recordSlot = slot<ProducerRecord<String, String>>()

        every {
            mockProducer.send(capture(recordSlot), any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)

        service.sendUrlCrawlRequest(threadId, url)

        val capturedRecord = recordSlot.captured
        val messageJson = capturedRecord.value()
        val messageMap = objectMapper.readValue(messageJson, Map::class.java)

        assertEquals(url, messageMap["url"])
    }

    @Test
    fun `sendUrlCrawlRequest - handles multiple concurrent sends`() {
        val threadId1 = UUID.randomUUID()
        val threadId2 = UUID.randomUUID()
        val url1 = "https://example.com/first"
        val url2 = "https://example.com/second"

        val recordSlots = mutableListOf<ProducerRecord<String, String>>()

        every {
            mockProducer.send(capture(recordSlots), any())
        } returns mockk<Future<RecordMetadata>>(relaxed = true)

        service.sendUrlCrawlRequest(threadId1, url1)
        service.sendUrlCrawlRequest(threadId2, url2)

        verify(exactly = 2) { mockProducer.send(any(), any()) }
        assertEquals(2, recordSlots.size)

        // Verify first message
        val message1 = objectMapper.readValue(recordSlots[0].value(), Map::class.java)
        assertEquals(threadId1.toString(), message1["threadId"])
        assertEquals(url1, message1["url"])

        // Verify second message
        val message2 = objectMapper.readValue(recordSlots[1].value(), Map::class.java)
        assertEquals(threadId2.toString(), message2["threadId"])
        assertEquals(url2, message2["url"])
    }

    @Test
    fun `UrlCrawlRequest - data class serializes correctly`() {
        val threadId = UUID.randomUUID().toString()
        val url = "https://example.com/test"
        val requestedAt = Instant.now().toString()

        val request = UrlCrawlRequest(
            threadId = threadId,
            url = url,
            requestedAt = requestedAt
        )

        val json = objectMapper.writeValueAsString(request)
        val deserialized = objectMapper.readValue(json, UrlCrawlRequest::class.java)

        assertEquals(threadId, deserialized.threadId)
        assertEquals(url, deserialized.url)
        assertEquals(requestedAt, deserialized.requestedAt)
    }
}
