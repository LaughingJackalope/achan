package concord.dev.consumer

import concord.dev.domain.*
import concord.dev.service.CognitiveEventProducerService
import concord.dev.service.OllamaService
import concord.dev.service.PostService
import concord.dev.service.SearchService
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.reactive.messaging.Incoming
import jakarta.transaction.Transactional
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@ApplicationScoped
class CognitiveEventConsumer(
    private val producerService: CognitiveEventProducerService,
    private val searchService: SearchService,
    private val postService: PostService,
    private val ollamaService: OllamaService
) {

    @Incoming("intent-declared-in")
    fun consumeIntentDeclared(intent: IntentDeclared): CompletionStage<Void> {
        Log.infof("Received intent: %s", intent)

        // 1. Materialize context
        val searchResults = searchService.searchByText(intent.content)
        val contextObjectIds = searchResults.map { it.threadId.toString() }

        // 2. Emit ContextMaterialized event
        val contextMaterializedEvent = ContextMaterialized(
            objectId = intent.objectId,
            causalityChain = listOf(intent.eventId),
            agentId = "context-agent", // Or some other identifier
            intentEventId = intent.eventId,
            intentContent = intent.content,
            contextObjectIds = contextObjectIds
        )
        producerService.sendContextMaterialized(contextMaterializedEvent)
        Log.infof("Emitted ContextMaterialized event for intent %s", intent.eventId)

        return CompletableFuture.completedFuture(null)
    }

    @Incoming("context-materialized-in")
    fun consumeContextMaterialized(context: ContextMaterialized): CompletionStage<Void> {
        Log.infof("Received context: %s", context)

        // 1. Fetch content for the materialized context
        val contextString = context.contextObjectIds.joinToString("\n\n---\n\n") { threadIdStr ->
            val threadId = ThreadId(UUID.fromString(threadIdStr))
            val pageContent = PageContent.findByThreadId(threadId)
            val posts = postService.getPosts(threadId, size = 20) // Get top 20 posts

            val contentBuilder = StringBuilder()
            if (pageContent != null) {
                contentBuilder.append("Article Title: ${pageContent.title}\n")
                contentBuilder.append("Article Content: ${pageContent.articleText?.take(2000)}\n")
            }
            if (posts.isNotEmpty()) {
                contentBuilder.append("Comments:\n")
                posts.forEach { post ->
                    contentBuilder.append("- ${post.content.value.take(200)}\n")
                }
            }
            contentBuilder.toString()
        }

        if (contextString.isBlank()) {
            Log.warnf("No context found for intent %s. Aborting.", context.intentEventId)
            return CompletableFuture.completedFuture(null)
        }

        // 2. Construct a prompt
        val prompt = """
            User query: "${context.intentContent}"

            Based on the following context, provide a comprehensive answer.

            Context:
            ---
            $contextString
            ---

            Answer:
        """.trimIndent()

        // 3. Call LLM to generate a proposal
        val proposedAnswer = ollamaService.generate(prompt)

        // 4. Emit ActProposed event
        val actProposedEvent = ActProposed(
            objectId = context.objectId,
            causalityChain = context.causalityChain + context.eventId,
            agentId = "llm-agent", // Or a more specific model name
            intentEventId = context.intentEventId,
            action = proposedAnswer,
            confidence = 0.9 // Placeholder confidence
        )
        producerService.sendActProposed(actProposedEvent)
        Log.infof("Emitted ActProposed event for intent %s", context.intentEventId)

        return CompletableFuture.completedFuture(null)
    }

    @Incoming("act-proposed-in")
    fun consumeActProposed(proposal: ActProposed): CompletionStage<Void> {
        Log.infof("Received proposal: %s", proposal)

        // For now, we assume the first proposal wins.
        // Future work: Implement a "decider" agent that collects and ranks proposals.

        val actCommittedEvent = ActCommitted(
            objectId = proposal.objectId,
            causalityChain = proposal.causalityChain + proposal.eventId,
            agentId = "decider-agent",
            intentEventId = proposal.intentEventId,
            proposalEventId = proposal.eventId,
            action = proposal.action
        )

        producerService.sendActCommitted(actCommittedEvent)
        Log.infof("Emitted ActCommitted event for proposal %s", proposal.eventId)

        return CompletableFuture.completedFuture(null)
    }

    @Incoming("act-committed-in")
    @Transactional
    fun consumeActCommitted(committed: ActCommitted): CompletionStage<Void> {
        Log.infof("Received committed act: %s", committed)

        val committedAct = CommittedAct(
            intentEventId = committed.intentEventId,
            proposalEventId = committed.proposalEventId,
            objectId = committed.objectId,
            action = committed.action
        )
        committedAct.persist()

        Log.infof("Persisted committed act for intent %s", committed.intentEventId)

        // Future work:
        // 2. Make the result available to the user (e.g., via WebSocket or API poll)
        return CompletableFuture.completedFuture(null)
    }

    @Incoming("act-failed-in")
    fun consumeActFailed(failure: ActFailed): CompletionStage<Void> {
        Log.errorf("Received act failure: %s", failure)
        // Future work:
        // 1. Log failure to a dead-letter queue or database
        // 2. Implement retry logic or alerting
        return CompletableFuture.completedFuture(null)
    }
}
