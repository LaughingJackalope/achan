package concord.dev.service

import jakarta.enterprise.context.ApplicationScoped
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Instant
import java.util.UUID
import com.fasterxml.jackson.databind.ObjectMapper

@ApplicationScoped
class KafkaProducerService(
    @ConfigProperty(name = "kafka.topic.url-crawl-requests")
    private val topic: String,
    private val producer: KafkaProducer<String, String>,
    private val objectMapper: ObjectMapper
) {

    fun sendUrlCrawlRequest(threadId: UUID, url: String) {
        val message = UrlCrawlRequest(
            threadId = threadId.toString(),
            url = url,
            requestedAt = Instant.now().toString()
        )

        val json = objectMapper.writeValueAsString(message)
        val record = ProducerRecord<String, String>(topic, threadId.toString(), json)

        try {
            producer.send(record) { _, exception ->
                if (exception != null) {
                    // Log error but don't throw - this is async callback
                    println("ERROR: Failed to send Kafka message for thread $threadId: ${exception.message}")
                }
            }
        } catch (exception: Exception) {
            // send() itself can time out before the callback is registered when a
            // broker is unavailable. Thread creation must still complete.
            println("ERROR: Failed to queue Kafka message for thread $threadId: ${exception.message}")
        }
    }
}

data class UrlCrawlRequest(
    val threadId: String,
    val url: String,
    val requestedAt: String
)
