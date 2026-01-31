package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.domain.ThreadId
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Channel
import org.eclipse.microprofile.reactive.messaging.Emitter
import org.eclipse.microprofile.reactive.messaging.Message
import org.jboss.logging.Logger
import java.time.Instant

@ApplicationScoped
class KafkaProducerService(
    @Channel("url-crawl-out")
    private val emitter: Emitter<String>,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(KafkaProducerService::class.java)

    fun sendUrlCrawlRequest(threadId: ThreadId, url: String) {
        val message = UrlCrawlRequest(
            threadId = threadId.value.toString(),
            url = url,
            requestedAt = Instant.now().toString()
        )

        val json = objectMapper.writeValueAsString(message)

        // Attach Kafka key via metadata; topic is configured on the channel
        val metadata = OutgoingKafkaRecordMetadata.builder<String>()
            .withKey(threadId.value.toString())
            .build()

        val msg: Message<String> = Message.of(json).addMetadata(metadata)

        try {
            emitter.send(msg)
        } catch (e: Exception) {
            // Log and swallow to avoid failing caller path; monitoring will catch delivery issues
            log.error("Failed to dispatch crawl request for thread $threadId", e)
        }
    }
}

data class UrlCrawlRequest(
    val threadId: String,
    val url: String,
    val requestedAt: String
)