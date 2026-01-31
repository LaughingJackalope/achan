package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import concord.dev.domain.ThreadId
import io.mockk.*
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KafkaProducerServiceTest {

    private lateinit var emitter: Emitter<String>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var service: KafkaProducerService

    @BeforeEach
    fun setup() {
        emitter = mockk(relaxed = true)
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        service = KafkaProducerService(emitter, objectMapper)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `sendUrlCrawlRequest - sends message with correct key metadata`() {
        val threadId = ThreadId.random()
        val url = "https://example.com/article"

        val msgSlot = slot<Message<String>>()
        every { emitter.send(capture(msgSlot)) } just Runs

        service.sendUrlCrawlRequest(threadId, url)

        // Verify send was called
        verify(exactly = 1) { emitter.send(any<Message<String>>()) }

        // Verify key metadata
        val captured = msgSlot.captured
        val metaRaw = captured.getMetadata(OutgoingKafkaRecordMetadata::class.java).orElse(null)
        assertNotNull(metaRaw)
        @Suppress("UNCHECKED_CAST")
        val meta = metaRaw as OutgoingKafkaRecordMetadata<String>
        assertEquals(threadId.toString(), meta.key)
    }

    @Test
    fun `sendUrlCrawlRequest - message contains correct JSON structure`() {
        val threadId = ThreadId.random()
        val url = "https://example.com/test"

        val msgSlot = slot<Message<String>>()
        every { emitter.send(capture(msgSlot)) } just Runs

        service.sendUrlCrawlRequest(threadId, url)

        val captured = msgSlot.captured
        val messageJson = captured.payload

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
    fun `sendUrlCrawlRequest - handles emitter errors gracefully`() {
        val threadId = ThreadId.random()
        val url = "https://example.com/error"

        every { emitter.send(any<Message<String>>()) } throws RuntimeException("Kafka send failed")

        // Should not throw - service catches and logs
        service.sendUrlCrawlRequest(threadId, url)

        verify(exactly = 1) { emitter.send(any<Message<String>>()) }
    }

    @Test
    fun `sendUrlCrawlRequest - handles special characters in URL`() {
        val threadId = ThreadId.random()
        val url = "https://example.com/path?query=value&foo=bar#fragment"

        val msgSlot = slot<Message<String>>()
        every { emitter.send(capture(msgSlot)) } just Runs

        service.sendUrlCrawlRequest(threadId, url)

        val captured = msgSlot.captured
        val messageJson = captured.payload
        val messageMap = objectMapper.readValue(messageJson, Map::class.java)

        assertEquals(url, messageMap["url"])
    }

    @Test
    fun `sendUrlCrawlRequest - handles multiple concurrent sends`() {
        val threadId1 = ThreadId.random()
        val threadId2 = ThreadId.random()
        val url1 = "https://example.com/first"
        val url2 = "https://example.com/second"

        val messages = mutableListOf<Message<String>>()
        every { emitter.send(capture(messages)) } just Runs

        service.sendUrlCrawlRequest(threadId1, url1)
        service.sendUrlCrawlRequest(threadId2, url2)

        verify(exactly = 2) { emitter.send(any<Message<String>>()) }
        assertEquals(2, messages.size)

        // Verify first message
        val message1 = objectMapper.readValue(messages[0].payload, Map::class.java)
        assertEquals(threadId1.toString(), message1["threadId"])
        assertEquals(url1, message1["url"])

        // Verify second message
        val message2 = objectMapper.readValue(messages[1].payload, Map::class.java)
        assertEquals(threadId2.toString(), message2["threadId"])
        assertEquals(url2, message2["url"])
    }

    @Test
    fun `UrlCrawlRequest - data class serializes correctly`() {
        val threadId = ThreadId.random().value.toString()
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
