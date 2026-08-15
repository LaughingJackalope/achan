package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import org.jboss.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

data class OllamaEmbedRequest(
    val model: String,
    val prompt: String
)

data class OllamaEmbedResponse(
    val embedding: List<Double>
)

data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: Map<String, Any>? = null
)

data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean
)

/**
 * Service for interacting with local Ollama API
 * Provides methods for embeddings and text generation
 *
 * Default endpoint: http://localhost:11434
 */
@ApplicationScoped
class OllamaService(
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(OllamaService::class.java)

    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val baseUrl = System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"

    /**
     * Generate embeddings using nomic-embed-text model
     * Returns 768-dimensional vector
     */
    fun generateEmbedding(text: String): List<Double> {
        val request = OllamaEmbedRequest(
            model = "nomic-embed-text",
            prompt = text
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/embeddings"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
            .build()

        try {
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                log.error("Ollama embeddings API returned ${response.statusCode()}: ${response.body()}")
                throw RuntimeException("Failed to generate embedding: ${response.statusCode()}")
            }

            val embedResponse = objectMapper.readValue(response.body(), OllamaEmbedResponse::class.java)
            log.debug("Generated embedding with ${embedResponse.embedding.size} dimensions")

            return embedResponse.embedding
        } catch (e: Exception) {
            log.error("Error calling Ollama embeddings API", e)
            throw RuntimeException("Failed to generate embedding", e)
        }
    }

    /**
     * Generate text using Llama 3.2 3B model
     * Used for summarization and metadata extraction
     */
    fun generate(prompt: String, model: String = "llama3.2:3b", options: Map<String, Any>? = null): String {
        val request = OllamaGenerateRequest(
            model = model,
            prompt = prompt,
            stream = false,
            options = options
        )

        val httpRequest = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/generate"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(120))
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
            .build()

        try {
            val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() != 200) {
                log.error("Ollama generate API returned ${response.statusCode()}: ${response.body()}")
                throw RuntimeException("Failed to generate text: ${response.statusCode()}")
            }

            val generateResponse = objectMapper.readValue(response.body(), OllamaGenerateResponse::class.java)
            log.debug("Generated ${generateResponse.response.length} characters using ${generateResponse.model}")

            return generateResponse.response
        } catch (e: Exception) {
            log.error("Error calling Ollama generate API", e)
            throw RuntimeException("Failed to generate text", e)
        }
    }

    /**
     * Generate a concise summary of article text
     */
    fun summarize(text: String, maxLength: Int = 500): String {
        if (text.length < maxLength) {
            return text
        }

        val prompt = """
            |Summarize the following article in 2-3 sentences, focusing on the main points.
            |Keep the summary under $maxLength characters.
            |
            |Article:
            |${text.take(4000)}
            |
            |Summary:
        """.trimMargin()

        return generate(prompt, options = mapOf("temperature" to 0.3))
    }

    /**
     * Extract topics/tags from article text
     * Returns comma-separated list of topics
     */
    fun extractTopics(text: String, title: String?): List<String> {
        val prompt = """
            |Extract 3-5 main topics or keywords from this article.
            |Return only the topics as a comma-separated list, no explanations.
            |
            |${if (title != null) "Title: $title\n" else ""}
            |Article:
            |${text.take(2000)}
            |
            |Topics:
        """.trimMargin()

        val response = generate(prompt, options = mapOf("temperature" to 0.1))

        return response
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(5)
    }
}