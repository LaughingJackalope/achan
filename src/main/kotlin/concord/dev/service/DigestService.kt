package concord.dev.service

import concord.dev.domain.Post
import concord.dev.domain.ThreadDigest
import concord.dev.domain.ThreadId
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import org.jboss.logging.Logger
import java.time.Instant
import java.util.UUID

/**
 * Service for generating and managing thread digests.
 * 
 * Creates AI-powered summaries of thread discussions including:
 * - Overall summary of the conversation
 * - Key claims with support/rebuttal tracking
 * - Open questions that remain unanswered
 * - Related threads for context
 */
@ApplicationScoped
class DigestService(
    private val ollamaService: OllamaService,
    private val searchService: SearchService,
    private val postService: PostService,
    private val objectMapper: ObjectMapper
) {
    private val log: Logger = Logger.getLogger(DigestService::class.java)

    /**
     * Generate or update a digest for a thread.
     * Returns existing digest if recently updated (within 1 hour).
     */
    @Transactional
    fun generateDigest(threadId: ThreadId, forceRefresh: Boolean = false): ThreadDigest {
        log.infof("Generating digest for thread %s (forceRefresh=%s)", threadId, forceRefresh)
        
        // Check for existing digest
        val existing = ThreadDigest.findByThreadId(threadId)
        if (existing != null && !forceRefresh) {
            val age = Instant.now().epochSecond - existing.lastSynthesizedAt.epochSecond
            if (age < 3600) { // Less than 1 hour old
                log.infof("Using cached digest (age: %d seconds)", age)
                return existing
            }
        }

        // Fetch all posts for the thread
        val posts = postService.getPosts(threadId, size = 500)
        if (posts.isEmpty()) {
            log.warnf("Thread %s has no posts, cannot generate digest", threadId)
            throw IllegalStateException("Cannot generate digest for thread with no posts")
        }

        log.infof("Generating digest from %d posts", posts.size)

        // Generate summary using LLM
        val summary = generateSummary(posts)
        
        // Extract key claims from posts
        val keyClaims = extractKeyClaims(posts)
        
        // Identify open questions
        val openQuestions = extractOpenQuestions(posts)
        
        // Find related threads
        val relatedThreads = findRelatedThreads(threadId)

        // Create or update digest
        val digest = existing ?: ThreadDigest().apply {
            this.threadId = threadId.value
            this.createdAt = Instant.now()
        }

        digest.apply {
            this.summary = summary
            this.keyClaims = objectMapper.writeValueAsString(keyClaims)
            this.openQuestions = objectMapper.writeValueAsString(openQuestions)
            this.relatedThreads = objectMapper.writeValueAsString(relatedThreads)
            this.lastSynthesizedAt = Instant.now()
            this.updatedAt = Instant.now()
        }

        if (existing == null) {
            digest.persist()
        }

        log.infof("Digest generated successfully for thread %s", threadId)
        return digest
    }

    /**
     * Generate a summary of the thread discussion.
     */
    private fun generateSummary(posts: List<Post>): String {
        log.debugf("Generating summary from %d posts", posts.size)
        
        // Combine all post content
        val combinedText = posts.joinToString("\n\n") { post ->
            val author = if (post.agentId != null) "[Agent: ${post.agentId}]" else "[Human]"
            "$author ${post.content}"
        }

        // Use LLM to generate summary
        val prompt = """
            Summarize the following discussion thread concisely. Focus on:
            1. Main topic and purpose of the discussion
            2. Key points and arguments made
            3. Current state of the conversation
            
            Keep the summary to 2-3 paragraphs maximum.
            
            Discussion:
            ${combinedText.take(8000)} // Limit to prevent token overflow
        """.trimIndent()

        return try {
            ollamaService.summarize(prompt)
        } catch (e: Exception) {
            log.errorf(e, "Failed to generate summary, using fallback")
            generateFallbackSummary(posts)
        }
    }

    /**
     * Fallback summary when LLM is unavailable.
     */
    private fun generateFallbackSummary(posts: List<Post>): String {
        val totalPosts = posts.size
        val agentPosts = posts.count { it.agentId != null }
        val humanPosts = totalPosts - agentPosts
        
        return "Discussion thread with $totalPosts posts ($humanPosts from humans, $agentPosts from agents). " +
               "First post: ${posts.firstOrNull()?.content?.take(100)}..."
    }

    /**
     * Extract key claims from posts with agent metadata.
     */
    private fun extractKeyClaims(posts: List<Post>): List<Map<String, Any>> {
        log.debugf("Extracting key claims from %d posts", posts.size)
        
        val claims = mutableListOf<Map<String, Any>>()
        
        // Focus on agent posts with HYPOTHESIS or EVIDENCE type
        val claimPosts = posts.filter { 
            it.agentId != null && 
            (it.postType?.name in listOf("HYPOTHESIS", "EVIDENCE", "SYNTHESIS"))
        }

        claimPosts.take(10).forEach { post ->  // Limit to top 10 claims
            val claim = mutableMapOf<String, Any>(
                "claim_id" to UUID.randomUUID().toString(),
                "text" to (post.content?.take(200) ?: ""),
                "support_score" to (post.confidence ?: 0.5),
                "citations" to emptyList<Int>(),  // TODO: Parse from content
                "rebuttals" to emptyList<Int>()   // TODO: Find REBUTTAL posts
            )
            claims.add(claim)
        }

        log.debugf("Extracted %d key claims", claims.size)
        return claims
    }

    /**
     * Extract open questions from posts.
     */
    private fun extractOpenQuestions(posts: List<Post>): List<String> {
        log.debugf("Extracting open questions from %d posts", posts.size)
        
        val questions = posts
            .filter { it.postType?.name == "QUESTION" || it.content?.contains("?") == true }
            .mapNotNull { it.content }
            .filter { it.contains("?") }
            .map { it.substringBefore("?") + "?" }
            .distinct()
            .take(5)  // Limit to 5 most recent questions

        log.debugf("Found %d open questions", questions.size)
        return questions
    }

    /**
     * Find threads related to this one using semantic search.
     */
    private fun findRelatedThreads(threadId: ThreadId): List<Map<String, Any>> {
        log.debugf("Finding related threads for %s", threadId)
        
        val related = try {
            searchService.findSimilarThreads(threadId, limit = 5, similarityThreshold = 0.7)
        } catch (e: Exception) {
            log.warnf(e, "Failed to find similar threads")
            emptyList()
        }

        val relatedThreads = related.map { result ->
            mapOf(
                "thread_id" to result.threadId.toString(),
                "similarity" to result.similarity,
                "relationship" to determineRelationship(result.similarity)
            )
        }

        log.debugf("Found %d related threads", relatedThreads.size)
        return relatedThreads
    }

    /**
     * Determine relationship type based on similarity score.
     */
    private fun determineRelationship(similarity: Double): String {
        return when {
            similarity >= 0.9 -> "duplicate"
            similarity >= 0.8 -> "provides_evidence"
            similarity >= 0.7 -> "related_topic"
            else -> "tangentially_related"
        }
    }

    /**
     * Get existing digest for a thread, or null if not found.
     */
    fun getDigest(threadId: ThreadId): ThreadDigest? {
        return ThreadDigest.findByThreadId(threadId)
    }

    /**
     * Delete digest for a thread.
     */
    @Transactional
    fun deleteDigest(threadId: ThreadId): Boolean {
        val deleted = ThreadDigest.deleteByThreadId(threadId)
        return deleted > 0
    }
}
