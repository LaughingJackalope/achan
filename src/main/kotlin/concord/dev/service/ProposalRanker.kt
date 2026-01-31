package concord.dev.service

import concord.dev.domain.ActProposed
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped

/**
 * Ranks and selects the best proposal from a collection of competing ActProposed events.
 *
 * Implements multi-criteria ranking:
 * 1. Priority (higher is better)
 * 2. Confidence (higher is better)
 * 3. Timestamp (earlier proposals win ties)
 */
@ApplicationScoped
class ProposalRanker {

    /**
     * Rank proposals and return the best one.
     *
     * Ranking criteria (in order of precedence):
     * 1. Highest priority
     * 2. Highest confidence (within same priority)
     * 3. Earliest timestamp (for tie-breaking)
     *
     * @param proposals List of ActProposed events to rank
     * @return The winning proposal, or null if list is empty
     */
    fun selectBest(proposals: List<ActProposed>): ActProposed? {
        if (proposals.isEmpty()) {
            Log.warn("No proposals to rank")
            return null
        }

        if (proposals.size == 1) {
            Log.infof("Only one proposal available, selecting: %s (confidence: %.2f, priority: %d)",
                proposals[0].agentId, proposals[0].confidence, proposals[0].priority)
            return proposals[0]
        }

        // Sort by: priority DESC, confidence DESC, timestamp ASC
        val ranked = proposals.sortedWith(
            compareByDescending<ActProposed> { it.priority }
                .thenByDescending { it.confidence }
                .thenBy { it.timestamp }
        )

        val winner = ranked.first()

        Log.infof("Ranked %d proposals. Winner: %s (confidence: %.2f, priority: %d, timestamp: %s)",
            proposals.size, winner.agentId, winner.confidence, winner.priority, winner.timestamp)

        // Log runner-ups for debugging
        ranked.drop(1).forEachIndexed { index, proposal ->
            Log.debugf("Rank #%d: %s (confidence: %.2f, priority: %d, timestamp: %s)",
                index + 2, proposal.agentId, proposal.confidence, proposal.priority, proposal.timestamp)
        }

        return winner
    }

    /**
     * Calculate a composite score for a proposal.
     * Can be used for more sophisticated ranking in the future.
     *
     * Current formula: (priority * 100) + (confidence * 10)
     * This gives priority 10x weight over confidence.
     *
     * @param proposal The proposal to score
     * @return Numeric score (higher is better)
     */
    fun calculateScore(proposal: ActProposed): Double {
        return (proposal.priority * 100.0) + (proposal.confidence * 10.0)
    }

    /**
     * Get ranking statistics for a set of proposals.
     *
     * @param proposals List of proposals to analyze
     * @return Map of statistics (avg confidence, max confidence, etc.)
     */
    fun getRankingStats(proposals: List<ActProposed>): Map<String, Any> {
        if (proposals.isEmpty()) {
            return emptyMap()
        }

        return mapOf(
            "count" to proposals.size,
            "avgConfidence" to proposals.map { it.confidence }.average(),
            "maxConfidence" to proposals.maxOf { it.confidence },
            "minConfidence" to proposals.minOf { it.confidence },
            "avgPriority" to proposals.map { it.priority }.average(),
            "maxPriority" to proposals.maxOf { it.priority },
            "minPriority" to proposals.minOf { it.priority },
            "uniqueAgents" to proposals.map { it.agentId }.distinct().size
        )
    }

    /**
     * Check if there's a clear winner (significantly better than others).
     *
     * A proposal is considered a "clear winner" if:
     * - It has the highest priority AND
     * - Its confidence is at least 0.2 higher than the next best, OR
     * - It's the only proposal with the highest priority
     *
     * @param proposals List of proposals to evaluate
     * @return Pair of (winner, isClearWinner)
     */
    fun selectBestWithConfidence(proposals: List<ActProposed>): Pair<ActProposed?, Boolean> {
        val winner = selectBest(proposals) ?: return Pair(null, false)

        if (proposals.size == 1) {
            return Pair(winner, true)
        }

        val sameProposals = proposals.sortedWith(
            compareByDescending<ActProposed> { it.priority }
                .thenByDescending { it.confidence }
        )

        val runnerUp = sameProposals[1]

        // Clear winner if higher priority
        if (winner.priority > runnerUp.priority) {
            Log.infof("Clear winner detected: %s has higher priority (%d vs %d)",
                winner.agentId, winner.priority, runnerUp.priority)
            return Pair(winner, true)
        }

        // Clear winner if significantly higher confidence
        val confidenceGap = winner.confidence - runnerUp.confidence
        if (confidenceGap >= 0.2) {
            Log.infof("Clear winner detected: %s has significantly higher confidence (%.2f vs %.2f, gap: %.2f)",
                winner.agentId, winner.confidence, runnerUp.confidence, confidenceGap)
            return Pair(winner, true)
        }

        Log.infof("No clear winner - top proposals are similar (confidence gap: %.2f)",
            confidenceGap)
        return Pair(winner, false)
    }
}
