package concord.dev.service

import concord.dev.domain.ActProposed
import concord.dev.domain.ConsensusVote
import io.quarkus.logging.Log
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages proposal evaluation windows and triggers voting.
 *
 * In federated mode, this service coordinates the multi-proposal collection and voting process:
 * 1. Tracks active proposal windows
 * 2. Triggers evaluation when windows close (timeout or manual)
 * 3. Uses ProposalRanker to select the best proposal locally
 * 4. Emits ConsensusVote for this instance's preferred proposal
 * 5. ConsensusAgent then aggregates votes and commits the winning proposal
 *
 * In single-instance mode, falls back to direct commit for simplicity.
 */
@ApplicationScoped
class ProposalDeciderService(
    private val proposalCollector: ProposalCollector,
    private val proposalRanker: ProposalRanker,
    private val cognitiveEventProducerService: CognitiveEventProducerService,
    private val instanceConfig: concord.dev.config.InstanceConfig,
    private val instanceRegistry: InstanceRegistry
) {

    // Track intents that have had their first proposal added (to schedule window closure)
    private val activeWindows = ConcurrentHashMap<UUID, Instant>()

    /**
     * Process a newly received proposal.
     * Adds it to the collection and checks if we should trigger immediate evaluation.
     *
     * @param proposal The ActProposed event to process
     */
    fun processProposal(proposal: ActProposed) {
        val intentId = proposal.intentEventId

        // Add proposal to Redis collection
        val count = proposalCollector.addProposal(proposal)

        // Track this window for scheduled cleanup
        activeWindows.putIfAbsent(intentId, Instant.now())

        Log.infof("Proposal from %s added for intent %s. Total proposals: %d",
            proposal.agentId, intentId, count)

        // Note: We don't immediately evaluate here - we wait for the window to expire
        // This is handled by the scheduled evaluateExpiredWindows() method
    }

    /**
     * Periodically check for expired proposal windows and evaluate them.
     * Runs every 2 seconds to catch windows that have expired.
     */
    @Scheduled(every = "2s")
    fun evaluateExpiredWindows() {
        val intentsToEvaluate = activeWindows.keys.filter { intentId ->
            !proposalCollector.isWindowActive(intentId)
        }

        if (intentsToEvaluate.isEmpty()) {
            return
        }

        Log.infof("Found %d expired proposal windows to evaluate", intentsToEvaluate.size)

        intentsToEvaluate.forEach { intentId ->
            try {
                evaluateAndCommit(intentId)
                activeWindows.remove(intentId)
            } catch (e: Exception) {
                Log.errorf(e, "Failed to evaluate proposals for intent %s", intentId)
                activeWindows.remove(intentId) // Remove to prevent retry storms
            }
        }
    }

    /**
     * Immediately evaluate proposals and emit a vote (or direct commit in single-instance mode).
     *
     * @param intentId The UUID of the intent to evaluate
     */
    fun evaluateAndCommit(intentId: UUID) {
        // Get and remove all proposals for this intent
        val proposals = proposalCollector.popProposals(intentId)

        if (proposals.isEmpty()) {
            Log.warnf("No proposals found for intent %s, nothing to vote on", intentId)
            return
        }

        Log.infof("Evaluating %d proposals for intent %s", proposals.size, intentId)

        // Rank and select the best proposal
        val (winner, isClearWinner) = proposalRanker.selectBestWithConfidence(proposals)

        if (winner == null) {
            Log.errorf("Ranking failed for intent %s despite having proposals", intentId)
            return
        }

        // Log ranking statistics
        val stats = proposalRanker.getRankingStats(proposals)
        Log.infof("Proposal ranking stats for intent %s: %s", intentId, stats)

        // Check if federation is active
        val isFederated = instanceRegistry.isFederationActive()

        if (isFederated) {
            // FEDERATED MODE: Emit vote instead of direct commit
            val vote = ConsensusVote(
                objectId = winner.objectId,
                causalityChain = winner.causalityChain + winner.eventId,
                agentId = "advanced-decider-agent",
                intentEventId = winner.intentEventId,
                votedProposalId = winner.eventId,
                votedAction = winner.action,
                voteReason = "Ranked best by priority=${winner.priority}, confidence=${winner.confidence}, clearWinner=$isClearWinner",
                votingInstance = instanceConfig.instanceId,
                rankingMetadata = mapOf(
                    "totalProposals" to proposals.size,
                    "isClearWinner" to isClearWinner,
                    "winnerAgent" to winner.agentId,
                    "winnerConfidence" to winner.confidence,
                    "winnerPriority" to winner.priority
                ),
                sourceInstance = instanceConfig.instanceId
            )

            cognitiveEventProducerService.sendConsensusVote(vote)

            Log.infof("Emitted consensus vote for intent %s: votedFor=%s, agent=%s, confidence=%.2f",
                intentId, winner.eventId, winner.agentId, winner.confidence)

        } else {
            // SINGLE-INSTANCE MODE: Direct commit (backwards compatibility)
            val actCommitted = concord.dev.domain.ActCommitted(
                objectId = winner.objectId,
                causalityChain = winner.causalityChain + winner.eventId,
                agentId = "advanced-decider-agent",
                intentEventId = winner.intentEventId,
                proposalEventId = winner.eventId,
                action = winner.action,
                sourceInstance = instanceConfig.instanceId
            )

            cognitiveEventProducerService.sendActCommitted(actCommitted)

            Log.infof("Direct committed (single-instance) winning proposal for intent %s: agent=%s, confidence=%.2f, priority=%d",
                intentId, winner.agentId, winner.confidence, winner.priority)
        }

        // Log if winner was not clear
        if (!isClearWinner && proposals.size > 1) {
            Log.infof("Winner was not clear for intent %s. In federated mode, consensus will aggregate votes.",
                intentId)
        }
    }

    /**
     * Manually trigger evaluation for a specific intent (bypasses window timeout).
     *
     * @param intentId The UUID of the intent
     */
    fun forceEvaluation(intentId: UUID) {
        Log.infof("Forcing immediate evaluation for intent %s", intentId)
        evaluateAndCommit(intentId)
        activeWindows.remove(intentId)
    }

    /**
     * Get the number of active proposal windows being tracked.
     */
    fun getActiveWindowCount(): Int {
        return activeWindows.size
    }

    /**
     * Get statistics about all active windows.
     */
    fun getActiveWindowStats(): Map<UUID, Map<String, Any>> {
        return activeWindows.mapValues { (intentId, startTime) ->
            mapOf(
                "startTime" to startTime,
                "proposalCount" to proposalCollector.getProposalCount(intentId),
                "timeRemaining" to proposalCollector.getWindowTimeRemaining(intentId),
                "isActive" to proposalCollector.isWindowActive(intentId)
            )
        }
    }
}
