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

        producer.send(record) { metadata, exception ->
            if (exception != null) {
                // Log error - OpenTelemetry will capture this
                throw exception
            }
        }
    }
}

data class UrlCrawlRequest(
    val threadId: String,
    val url: String,
    val requestedAt: String
)