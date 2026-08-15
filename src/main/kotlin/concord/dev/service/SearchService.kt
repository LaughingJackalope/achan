package concord.dev.service

import com.pgvector.PGvector
import concord.dev.domain.PageContent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.persistence.EntityManager
import org.jboss.logging.Logger
import java.util.UUID

/**
 * Service for semantic search using pgvector embeddings
 * Uses cosine similarity for finding related content
 */
@ApplicationScoped
class SearchService(
    private val ollamaService: OllamaService,
    private val entityManager: EntityManager
) {
    private val log: Logger = Logger.getLogger(SearchService::class.java)

    /**
     * Search for content similar to a text query
     * Returns threads ranked by semantic similarity
     */
    fun searchByText(
        query: String,
        limit: Int = 10,
        similarityThreshold: Double = 0.7
    ): List<SearchResult> {
        // Generate embedding for the query
        log.info("Generating embedding for search query: ${query.take(50)}...")
        val queryEmbedding = ollamaService.generateEmbedding(query)
        val queryVector = PageContent.listToVector(queryEmbedding)

        // Query using cosine similarity (1 - cosine distance)
        // pgvector's <=> operator returns cosine distance (0 = identical, 2 = opposite)
        // We convert to similarity: 1 - (distance / 2) to get range [0, 1]
        val sql = """
            SELECT
                pc.id,
                pc.thread_id,
                pc.url,
                pc.title,
                pc.description,
                pc.article_text,
                (1 - (pc.embedding <=> CAST(:queryVector AS vector)) / 2) as similarity
            FROM page_content pc
            WHERE pc.embedding IS NOT NULL
              AND (1 - (pc.embedding <=> CAST(:queryVector AS vector)) / 2) >= :threshold
            ORDER BY pc.embedding <=> CAST(:queryVector AS vector)
            LIMIT :limit
        """.trimIndent()

        val results = entityManager.createNativeQuery(sql)
            .setParameter("queryVector", queryVector.toString())  // Pass as string, PostgreSQL will parse it
            .setParameter("threshold", similarityThreshold)
            .setParameter("limit", limit)
            .resultList as List<Array<Any>>

        return results.map { row ->
            SearchResult(
                contentId = (row[0] as Number).toLong(),
                threadId = when (val id = row[1]) {
                    is UUID -> id
                    is String -> UUID.fromString(id)
                    else -> throw IllegalStateException("Unexpected thread_id type: ${id?.javaClass?.name}")
                },
                url = row[2] as String,
                title = row[3] as? String,
                description = row[4] as? String,
                snippet = (row[5] as? String)?.take(200),
                similarity = (row[6] as Number).toDouble()
            )
        }
    }

    /**
     * Find threads similar to a given thread
     * Useful for "related content" features
     */
    fun findSimilarThreads(
        threadId: concord.dev.domain.ThreadId,
        limit: Int = 5,
        similarityThreshold: Double = 0.7
    ): List<SearchResult> {
        // Get the source thread's embedding
        val sourceContent = PageContent.findByThreadId(threadId)
        if (sourceContent?.embedding == null) {
            log.warn("Thread $threadId has no embedding, cannot find similar threads")
            return emptyList()
        }

        val sql = """
            SELECT
                pc.id,
                pc.thread_id,
                pc.url,
                pc.title,
                pc.description,
                pc.article_text,
                (1 - (pc.embedding <=> CAST(:sourceEmbedding AS vector)) / 2) as similarity
            FROM page_content pc
            WHERE pc.embedding IS NOT NULL
              AND pc.thread_id != :threadId
              AND (1 - (pc.embedding <=> CAST(:sourceEmbedding AS vector)) / 2) >= :threshold
            ORDER BY pc.embedding <=> CAST(:sourceEmbedding AS vector)
            LIMIT :limit
        """.trimIndent()

        val results = entityManager.createNativeQuery(sql)
            .setParameter("sourceEmbedding", sourceContent.embedding.toString())  // Pass as string, PostgreSQL will parse it
            .setParameter("threadId", threadId)
            .setParameter("threshold", similarityThreshold)
            .setParameter("limit", limit)
            .resultList as List<Array<Any>>

        return results.map { row ->
            SearchResult(
                contentId = (row[0] as Number).toLong(),
                threadId = when (val id = row[1]) {
                    is UUID -> id
                    is String -> UUID.fromString(id)
                    else -> throw IllegalStateException("Unexpected thread_id type: ${id?.javaClass?.name}")
                },
                url = row[2] as String,
                title = row[3] as? String,
                description = row[4] as? String,
                snippet = (row[5] as? String)?.take(200),
                similarity = (row[6] as Number).toDouble()
            )
        }
    }

    /**
     * Get embedding statistics for monitoring
     */
    fun getEmbeddingStats(): EmbeddingStats {
        val sql = """
            SELECT
                COUNT(*) as total_content,
                COUNT(embedding) as embedded_content,
                COUNT(*) FILTER (WHERE embedding IS NULL) as missing_embeddings
            FROM page_content
        """.trimIndent()

        val result = entityManager.createNativeQuery(sql).singleResult as Array<Any>

        return EmbeddingStats(
            totalContent = (result[0] as Number).toLong(),
            embeddedContent = (result[1] as Number).toLong(),
            missingEmbeddings = (result[2] as Number).toLong()
        )
    }
}

data class SearchResult(
    val contentId: Long,
    val threadId: UUID,
    val url: String,
    val title: String?,
    val description: String?,
    val snippet: String?,
    val similarity: Double
)

data class EmbeddingStats(
    val totalContent: Long,
    val embeddedContent: Long,
    val missingEmbeddings: Long
)