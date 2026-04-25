package concord.dev.test

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager
import java.time.Duration
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import java.util.concurrent.TimeUnit
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgresTestResource : QuarkusTestResourceLifecycleManager {

    private val postgres = PostgreSQLContainer("pgvector/pgvector:pg16")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")
    private val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))

    override fun start(): Map<String, String> {
        postgres.start()
        kafka.start()
        createKafkaTopics()
        return mapOf(
            "quarkus.datasource.jdbc.url" to postgres.jdbcUrl,
            "quarkus.datasource.username" to postgres.username,
            "quarkus.datasource.password" to postgres.password,
            "kafka.bootstrap.servers" to kafka.bootstrapServers
        )
    }

    override fun stop() {
        kafka.stop()
        postgres.stop()
    }

    private fun createKafkaTopics() {
        val props = mapOf(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers
        )
        val admin = AdminClient.create(props)
        try {
            waitForKafka(admin)
            val topics = listOf(
                "url-crawl-requests-test",
                "ai-enrichment-requests",
                "intent.declared",
                "context.materialized",
                "act.proposed",
                "act.committed",
                "act.failed",
                "consensus.vote",
                "consensus.reached",
                "agent.notifications"
            ).map { NewTopic(it, 1, 1.toShort()) }

            admin.createTopics(topics).all().get(30, TimeUnit.SECONDS)
        } finally {
            admin.close(Duration.ofSeconds(5))
        }
    }

    private fun waitForKafka(admin: AdminClient) {
        val deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos()
        var lastError: Exception? = null
        while (System.nanoTime() < deadline) {
            try {
                admin.describeCluster().nodes().get(5, TimeUnit.SECONDS)
                return
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(500)
            }
        }
        throw RuntimeException("Kafka did not become ready in time", lastError)
    }
}
