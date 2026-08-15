package concord.dev.service

import concord.dev.domain.ActCommitted
import concord.dev.domain.ConsensusReached
import concord.dev.domain.ConsensusVote
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Consensus Agent - Aggregates votes from instances and commits winning proposals.
 *
 * In a federated deployment, each instance independently ranks proposals and emits
 * a ConsensusVote. This agent collects those votes and determines when quorum
 * has been reached.
 *
 * Consensus Protocol:
 * 1. Collect ConsensusVote events from all instances
 * 2. Check if quorum reached (>50% of active instances)
 * 3. Determine winning proposal (most votes)
 * 4. Emit ConsensusReached event
 * 5. Emit ActCommitted for the winner
 *
 * Only ONE instance should emit ActCommitted for a given intent (to prevent duplicates).
 * We use a distributed lock pattern: first instance to see quorum wins.
 */
@ApplicationScoped
class ConsensusAgent(
    private val consensusCollector: ConsensusCollector,
    private val cognitiveEventProducerService: CognitiveEventProducerService,
    private val instanceRegistry: InstanceRegistry,
    private val instanceConfig: concord.dev.config.InstanceConfig
) {

    // Track intents we've already processed to prevent duplicate commits
    private val processedIntents = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Process a consensus vote.
     * Called when a ConsensusVote event is consumed from Kafka.
     *
     * @param vote The ConsensusVote event
     */
    fun processVote(vote: ConsensusVote) {
        val intentId = vote.intentEventId

        // Skip if we've already processed this intent
        if (processedIntents.containsKey(intentId)) {
            Log.debugf("Already processed intent %s, skipping vote from %s",
                intentId, vote.votingInstance)
            return
        }

        // Check for duplicate votes from same instance
        if (consensusCollector.hasInstanceVoted(intentId, vote.votingInstance)) {
            Log.warnf("Instance %s attempted to vote twice for intent %s. Ignoring duplicate.",
                vote.votingInstance, intentId)
            return
        }

        // Add vote to collection
        val voteCount = consensusCollector.addVote(vote)

        Log.infof("Vote received from %s for intent %s. Total votes: %d (voted for proposal %s)",
            vote.votingInstance, intentId, voteCount, vote.votedProposalId)

        // Check if quorum reached
        val (hasQuorum, quorumSize) = consensusCollector.hasQuorum(intentId)

        if (!hasQuorum) {
            Log.debugf("Quorum not yet reached for intent %s. Votes: %d, Quorum: %d",
                intentId, voteCount, quorumSize)
            return
        }

        // Quorum reached! Try to be the first to process
        val wasFirst = processedIntents.putIfAbsent(intentId, true)
        if (wasFirst != null) {
            Log.debugf("Another instance already processed intent %s, skipping", intentId)
            return
        }

        // We're the first to see quorum - commit the result
        commitConsensus(intentId)
    }

    /**
     * Commit the consensus result for an intent.
     *
     * @param intentId The intent that reached consensus
     */
    private fun commitConsensus(intentId: UUID) {
        // Get final vote statistics
        val voteStats = consensusCollector.getVoteStats(intentId)
        val votes = consensusCollector.popVotes(intentId)

        if (votes.isEmpty()) {
            Log.errorf("No votes found for intent %s despite reaching quorum!", intentId)
            return
        }

        // Determine the winner
        val breakdown = consensusCollector.getVoteBreakdown(intentId)
        val winnerEntry = breakdown.maxByOrNull { it.value }

        if (winnerEntry == null) {
            Log.errorf("Could not determine winner for intent %s", intentId)
            return
        }

        val winningProposalId = UUID.fromString(winnerEntry.key)
        val winningVotes = winnerEntry.value

        // Find the winning vote to get the action content
        val winningVote = votes.find { it.votedProposalId == winningProposalId }
        if (winningVote == null) {
            Log.errorf("Could not find winning vote for proposal %s", winningProposalId)
            return
        }

        val consensusType = consensusCollector.getConsensusType(intentId)
        val (_, quorumSize) = consensusCollector.hasQuorum(intentId)

        Log.infof("CONSENSUS REACHED for intent %s: winner=%s, votes=%d/%d, type=%s",
            intentId, winningProposalId, winningVotes, votes.size, consensusType)

        // Emit ConsensusReached event
        val consensusReached = ConsensusReached(
            objectId = winningVote.objectId,
            causalityChain = winningVote.causalityChain + winningVote.eventId,
            agentId = "consensus-agent",
            intentEventId = intentId,
            winningProposalId = winningProposalId,
            votesReceived = votes.size,
            votesRequired = quorumSize,
            voteBreakdown = breakdown.mapValues { it.value },
            consensusType = consensusType,
            sourceInstance = instanceConfig.instanceId
        )

        cognitiveEventProducerService.sendConsensusReached(consensusReached)

        // Emit ActCommitted with the winning action
        val actCommitted = ActCommitted(
            objectId = winningVote.objectId,
            causalityChain = consensusReached.causalityChain + consensusReached.eventId,
            agentId = "consensus-agent",
            intentEventId = intentId,
            proposalEventId = winningProposalId,
            action = winningVote.votedAction,
            sourceInstance = instanceConfig.instanceId
        )

        cognitiveEventProducerService.sendActCommitted(actCommitted)

        Log.infof("Consensus commit complete for intent %s. Action: %s",
            intentId, winningVote.votedAction.take(100))

        // Log vote breakdown for debugging
        Log.debugf("Vote breakdown for intent %s: %s", intentId, breakdown)
        votes.forEach { vote ->
            Log.debugf("  - %s voted for %s: %s",
                vote.votingInstance, vote.votedProposalId, vote.voteReason)
        }
    }

    /**
     * Get statistics about consensus processing.
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "processedIntents" to processedIntents.size,
            "activeInstances" to instanceRegistry.getActiveInstanceCount(),
            "quorumSize" to instanceRegistry.getQuorumSize(),
            "isFederationActive" to instanceRegistry.isFederationActive()
        )
    }

    /**
     * Clear processed intents (for testing/debugging).
     */
    fun clearProcessedIntents() {
        processedIntents.clear()
        Log.info("Cleared processed intents cache")
    }

    /**
     * Check if an intent has been processed.
     */
    fun hasProcessedIntent(intentId: UUID): Boolean {
        return processedIntents.containsKey(intentId)
    }
}
