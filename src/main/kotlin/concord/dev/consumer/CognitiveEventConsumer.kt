package concord.dev.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.domain.*
import concord.dev.service.CognitiveEventProducerService
import concord.dev.service.OllamaService
import concord.dev.service.PostService
import concord.dev.service.ProposalDeciderService
import concord.dev.service.ResponseSubscriptionManager
import concord.dev.service.SearchService
import concord.dev.service.SummarizationAgent
import io.quarkus.logging.Log
import io.smallrye.reactive.messaging.annotations.Blocking
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
    private val ollamaService: OllamaService,
    private val subscriptionManager: ResponseSubscriptionManager,
    private val objectMapper: ObjectMapper,
    private val proposalDeciderService: ProposalDeciderService,
    private val summarizationAgent: SummarizationAgent,
    private val consensusAgent: concord.dev.service.ConsensusAgent,
    private val instanceConfig: concord.dev.config.InstanceConfig
) {

    @Incoming("intent-declared-in")
    @Blocking
    @Transactional
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
            contextObjectIds = contextObjectIds,
            sourceInstance = instanceConfig.instanceId
        )
        producerService.sendContextMaterialized(contextMaterializedEvent)
        Log.infof("Emitted ContextMaterialized event for intent %s", intent.eventId)

        return CompletableFuture.completedFuture(null)
    }

    @Incoming("context-materialized-in")
    @Blocking
    @Transactional
    fun consumeContextMaterialized(context: ContextMaterialized): CompletionStage<Void> {
        Log.infof("Received context: %s (intentId: %s)", context.eventId, context.intentEventId)

        // Trigger BOTH agents to create competing proposals
        // This enables multi-agent competition with the Advanced Decider

        // 1. Main LLM Agent - Detailed comprehensive answer
        generateDetailedProposal(context)

        // 2. Summarization Agent - Concise summary (competing proposal)
        summarizationAgent.processContext(context)

        Log.infof("Both detailed and summarization agents triggered for intent %s", context.intentEventId)

        return CompletableFuture.completedFuture(null)
    }

    /**
     * Generate a detailed, comprehensive proposal (main LLM agent).
     */
    private fun generateDetailedProposal(context: ContextMaterialized) {
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
                    contentBuilder.append("- ${post.content?.take(200) ?: ""}\n")
                }
            }
            contentBuilder.toString()
        }

        if (contextString.isBlank()) {
            Log.warnf("No context found for intent %s. Skipping detailed proposal.", context.intentEventId)
            return
        }

        // 2. Construct a prompt for detailed answer
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

        // 4. Emit ActProposed event with higher priority than summary agent
        val actProposedEvent = ActProposed(
            objectId = context.objectId,
            causalityChain = context.causalityChain + context.eventId,
            agentId = "detailed-llm-agent",
            intentEventId = context.intentEventId,
            action = proposedAnswer,
            confidence = 0.85, // High confidence for detailed answers
            priority = 1, // Higher priority than summarization agent (0)
            sourceInstance = instanceConfig.instanceId
        )
        producerService.sendActProposed(actProposedEvent)
        Log.infof("Detailed LLM agent emitted proposal for intent %s (confidence: %.2f, priority: %d)",
            context.intentEventId, actProposedEvent.confidence, actProposedEvent.priority)
    }

    @Incoming("act-proposed-in")
    fun consumeActProposed(proposal: ActProposed): CompletionStage<Void> {
        Log.infof("Received proposal from %s: intentId=%s, confidence=%.2f, priority=%d",
            proposal.agentId, proposal.intentEventId, proposal.confidence, proposal.priority)

        // Use the advanced decider service to collect and rank proposals
        // The decider will evaluate all proposals after the collection window expires
        proposalDeciderService.processProposal(proposal)

        return CompletableFuture.completedFuture(null)
    }

    @Incoming("act-committed-in")
    @Transactional
    fun consumeActCommitted(committed: ActCommitted): CompletionStage<Void> {
        Log.infof("Received committed act: %s", committed)

        val committedAct = CommittedAct()
        committedAct.intentEventId = committed.intentEventId
        committedAct.proposalEventId = committed.proposalEventId
        committedAct.objectId = committed.objectId
        committedAct.action = committed.action
        committedAct.persist()

        Log.infof("Persisted committed act for intent %s", committed.intentEventId)

        // Push result to subscribed WebSocket clients in real-time
        val subscriberCount = subscriptionManager.getSubscriberCount(committed.intentEventId)
        if (subscriberCount > 0) {
            try {
                val response = mapOf(
                    "type" to "response",
                    "intentId" to committedAct.intentEventId.toString(),
                    "action" to committedAct.action,
                    "objectId" to committedAct.objectId,
                    "createdAt" to committedAct.createdAt.toString()
                )
                val responseJson = objectMapper.writeValueAsString(response)
                val pushedCount = subscriptionManager.pushResponse(committed.intentEventId, responseJson)
                Log.infof("Pushed response for intent %s to %d/%d subscribers",
                    committed.intentEventId, pushedCount, subscriberCount)
            } catch (e: Exception) {
                Log.errorf(e, "Failed to push WebSocket response for intent %s", committed.intentEventId)
            }
        } else {
            Log.debugf("No active subscribers for intent %s, result available via polling",
                committed.intentEventId)
        }

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

    @Incoming("consensus-vote-in")
    fun consumeConsensusVote(vote: ConsensusVote): CompletionStage<Void> {
        Log.infof("Received consensus vote from %s for intent %s (voted for proposal %s)",
            vote.votingInstance, vote.intentEventId, vote.votedProposalId)

        // Pass to consensus agent for aggregation and quorum checking
        consensusAgent.processVote(vote)

        return CompletableFuture.completedFuture(null)
    }

    @Incoming("consensus-reached-in")
    fun consumeConsensusReached(consensus: ConsensusReached): CompletionStage<Void> {
        Log.infof("Received consensus reached event: intent=%s, winner=%s, votes=%d/%d, type=%s",
            consensus.intentEventId, consensus.winningProposalId,
            consensus.votesReceived, consensus.votesRequired, consensus.consensusType)

        // Log vote breakdown for transparency
        Log.debugf("Vote breakdown: %s", consensus.voteBreakdown)

        // Future work:
        // 1. Persist consensus metadata for auditing
        // 2. Emit metrics for monitoring consensus latency
        // 3. Alert if consensus type is concerning (e.g., split votes)

        return CompletableFuture.completedFuture(null)
    }
}
