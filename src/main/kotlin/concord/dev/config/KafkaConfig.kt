package concord.dev.config

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.util.Properties

@ApplicationScoped
class KafkaConfig {

    @Produces
    @ApplicationScoped
    fun kafkaProducer(
        @ConfigProperty(name = "kafka.bootstrap.servers") bootstrapServers: String,
        @ConfigProperty(name = "kafka.producer.max-block-ms", defaultValue = "1000") maxBlockMs: Long
    ): KafkaProducer<String, String> {
        val props = Properties().apply {
            put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
            put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
            put(ProducerConfig.ACKS_CONFIG, "all")
            put(ProducerConfig.RETRIES_CONFIG, 3)
            put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
            // KafkaProducer.send() can block while fetching topic metadata. Keep an
            // unavailable broker from holding an HTTP request (and its transaction).
            put(ProducerConfig.MAX_BLOCK_MS_CONFIG, maxBlockMs)
        }
        return KafkaProducer(props)
    }

    @Produces
    @ApplicationScoped
    fun objectMapper(): ObjectMapper {
        return ObjectMapper().apply {
            findAndRegisterModules()
        }
    }
}
