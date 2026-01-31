package concord.dev.service

import concord.dev.domain.ActProposed
import concord.dev.domain.ContextMaterialized
import concord.dev.domain.PageContent
import concord.dev.domain.ThreadId
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import java.util.UUID

/**
 * Summarization Agent - Competing cognitive agent that produces concise summaries.
 *
 * This agent subscribes to the same `context.materialized` events as the main LLM agent
 * but generates brief, actionable summaries instead of detailed responses.
 *
 * Competition strategy:
 * - Lower priority than main agent (to prefer detailed answers by default)
 * - Higher confidence when context is clear and concise
 * - Emits proposals with "summary" type metadata
 */
@ApplicationScoped
class SummarizationAgent(
    private val postService: PostService,
    private val ollamaService: OllamaService,
    private val cognitiveEventProducerService: CognitiveEventProducerService,
    private val instanceConfig: concord.dev.config.InstanceConfig
) {

    companion object {
        const val AGENT_ID = "summarization-agent"
        const val TARGET_WORD_LIMIT = 200
        const val DEFAULT_PRIORITY = 0 // Lower than typical detailed agent priority
        const val HIGH_CLARITY_CONFIDENCE = 0.85
        const val MEDIUM_CLARITY_CONFIDENCE = 0.70
        const val LOW_CLARITY_CONFIDENCE = 0.60
    }

    /**
     * Process a context materialization event and generate a concise summary proposal.
     *
     * @param context The ContextMaterialized event with the user's intent and gathered context
     */
    fun processContext(context: ContextMaterialized) {
        Log.infof("Summarization agent processing context for intent %s", context.intentEventId)

        // 1. Fetch and prepare context content
        val contextContent = buildContextContent(context)

        if (contextContent.isBlank()) {
            Log.warnf("No context available for intent %s. Skipping summarization.", context.intentEventId)
            return
        }

        // 2. Determine confidence based on context quality
        val confidence = assessContextClarity(contextContent, context.intentContent)

        // 3. Generate concise summary using LLM
        val summary = generateSummary(context.intentContent, contextContent)

        // 4. Validate summary meets criteria
        if (!isValidSummary(summary)) {
            Log.warnf("Generated summary for intent %s failed validation. Skipping proposal.",
                context.intentEventId)
            return
        }

        // 5. Emit competing proposal
        val proposal = ActProposed(
            objectId = context.objectId,
            causalityChain = context.causalityChain + context.eventId,
            agentId = AGENT_ID,
            intentEventId = context.intentEventId,
            action = summary,
            confidence = confidence,
            priority = DEFAULT_PRIORITY,
            sourceInstance = instanceConfig.instanceId
        )

        cognitiveEventProducerService.sendActProposed(proposal)

        Log.infof("Summarization agent emitted proposal for intent %s (confidence: %.2f, words: %d)",
            context.intentEventId, confidence, summary.split("\\s+".toRegex()).size)
    }

    /**
     * Build context content from the materialized context object IDs.
     */
    private fun buildContextContent(context: ContextMaterialized): String {
        return context.contextObjectIds.joinToString("\n\n---\n\n") { threadIdStr ->
            val threadId = ThreadId(UUID.fromString(threadIdStr))
            val pageContent = PageContent.findByThreadId(threadId)
            val posts = postService.getPosts(threadId, size = 10) // Fewer posts for summarization

            val contentBuilder = StringBuilder()
            if (pageContent != null) {
                contentBuilder.append("Title: ${pageContent.title}\n")
                // Shorter article excerpt for summaries
                contentBuilder.append("Content: ${pageContent.articleText?.take(1000) ?: ""}\n")
            }
            if (posts.isNotEmpty()) {
                contentBuilder.append("Top Comments:\n")
                posts.take(5).forEach { post -> // Only top 5 comments
                    contentBuilder.append("- ${post.content?.take(150) ?: ""}\n")
                }
            }
            contentBuilder.toString()
        }
    }

    /**
     * Assess context clarity to determine confidence level.
     *
     * High clarity = straightforward question + clear context → high confidence summary
     * Low clarity = complex question + ambiguous context → lower confidence
     */
    private fun assessContextClarity(contextContent: String, intentContent: String): Double {
        // Simple heuristics for clarity assessment
        val contextLength = contextContent.length
        val intentLength = intentContent.length

        return when {
            // Clear, concise intent with substantial context
            intentLength < 100 && contextLength > 500 -> HIGH_CLARITY_CONFIDENCE

            // Moderate complexity
            intentLength < 200 && contextLength > 300 -> MEDIUM_CLARITY_CONFIDENCE

            // Complex or unclear
            else -> LOW_CLARITY_CONFIDENCE
        }
    }

    /**
     * Generate a concise summary using the LLM.
     */
    private fun generateSummary(intentContent: String, contextContent: String): String {
        val prompt = buildSummarizationPrompt(intentContent, contextContent)
        return ollamaService.generate(prompt)
    }

    /**
     * Build the summarization prompt template.
     */
    private fun buildSummarizationPrompt(intent: String, context: String): String {
        return """
            User query: "$intent"

            Based on the following context, provide a CONCISE summary that answers the query.

            Requirements:
            - Maximum 2-3 sentences (under $TARGET_WORD_LIMIT words)
            - Focus on the most important information
            - Be direct and actionable
            - Avoid unnecessary details

            Context:
            ---
            $context
            ---

            Concise Summary:
        """.trimIndent()
    }

    /**
     * Validate that the generated summary meets our criteria.
     *
     * @return true if summary is valid, false otherwise
     */
    private fun isValidSummary(summary: String): Boolean {
        val wordCount = summary.split("\\s+".toRegex()).size

        return when {
            summary.isBlank() -> {
                Log.warn("Summary is blank")
                false
            }
            wordCount > TARGET_WORD_LIMIT * 1.5 -> { // Allow 50% buffer
                Log.warnf("Summary too long: %d words (target: %d)", wordCount, TARGET_WORD_LIMIT)
                false
            }
            wordCount < 10 -> {
                Log.warnf("Summary too short: %d words", wordCount)
                false
            }
            else -> true
        }
    }

    /**
     * Get statistics about this agent's operation.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "agentId" to AGENT_ID,
            "targetWordLimit" to TARGET_WORD_LIMIT,
            "defaultPriority" to DEFAULT_PRIORITY,
            "confidenceLevels" to mapOf(
                "high" to HIGH_CLARITY_CONFIDENCE,
                "medium" to MEDIUM_CLARITY_CONFIDENCE,
                "low" to LOW_CLARITY_CONFIDENCE
            )
        )
    }
}
