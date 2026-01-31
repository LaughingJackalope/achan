package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.domain.ConsensusVote
import io.quarkus.logging.Log
import io.quarkus.redis.datasource.RedisDataSource
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.util.*

/**
 * Redis-based collector for consensus votes from federated instances.
 *
 * When multiple instances evaluate proposals, each instance ranks them locally
 * and emits a ConsensusVote. This service aggregates these votes to determine
 * if a quorum has been reached.
 *
 * Key Features:
 * - Store votes in Redis with TTL
 * - Track which instances have voted
 * - Calculate vote tallies by proposalId
 * - Detect when quorum is reached
 *
 * Redis Data Structure:
 * - votes:{intentId} -> List<ConsensusVote> (JSON strings)
 * - TTL: 10 seconds (votes expire if consensus not reached)
 */
@ApplicationScoped
class ConsensusCollector(
    private val redisDataSource: RedisDataSource,
    private val objectMapper: ObjectMapper,
    private val instanceRegistry: InstanceRegistry
) {

    private val listCommands = redisDataSource.list(String::class.java)
    private val keyCommands = redisDataSource.key()

    companion object {
        private const val VOTE_KEY_PREFIX = "votes:"
        private val DEFAULT_VOTE_WINDOW = Duration.ofSeconds(10)

        fun voteKey(intentId: UUID): String = "$VOTE_KEY_PREFIX$intentId"
    }

    /**
     * Add a vote to the collection for a given intent.
     *
     * @param vote The ConsensusVote to add
     * @param windowDuration How long to keep votes before expiring (default 10s)
     * @return Total number of votes collected for this intent
     */
    fun addVote(vote: ConsensusVote, windowDuration: Duration = DEFAULT_VOTE_WINDOW): Long {
        val key = voteKey(vote.intentEventId)
        val voteJson = objectMapper.writeValueAsString(vote)

        // Add vote to Redis list
        val count = listCommands.rpush(key, voteJson)

        // Set TTL on first vote
        if (count == 1L) {
            keyCommands.expire(key, windowDuration)
        }

        Log.debugf("Vote added from %s for intent %s. Total votes: %d",
            vote.votingInstance, vote.intentEventId, count)

        return count
    }

    /**
     * Get all votes for a given intent.
     *
     * @param intentId The intent to get votes for
     * @return List of ConsensusVote objects
     */
    fun getVotes(intentId: UUID): List<ConsensusVote> {
        val key = voteKey(intentId)
        val voteJsons = listCommands.lrange(key, 0, -1)

        return voteJsons.mapNotNull { json ->
            try {
                objectMapper.readValue(json, ConsensusVote::class.java)
            } catch (e: Exception) {
                Log.warnf(e, "Failed to parse vote JSON: %s", json)
                null
            }
        }
    }

    /**
     * Remove and return all votes for a given intent.
     *
     * @param intentId The intent to pop votes for
     * @return List of ConsensusVote objects
     */
    fun popVotes(intentId: UUID): List<ConsensusVote> {
        val votes = getVotes(intentId)
        keyCommands.del(voteKey(intentId))
        return votes
    }

    /**
     * Check if quorum has been reached for a given intent.
     *
     * Quorum is defined as: votes >= ceil(activeInstances / 2) + 1
     *
     * @param intentId The intent to check
     * @return Pair of (hasQuorum, votesNeeded)
     */
    fun hasQuorum(intentId: UUID): Pair<Boolean, Int> {
        val votes = getVotes(intentId)
        val quorumSize = instanceRegistry.getQuorumSize()
        val hasQuorum = votes.size >= quorumSize

        return Pair(hasQuorum, quorumSize)
    }

    /**
     * Get vote breakdown by proposalId.
     *
     * @param intentId The intent to analyze
     * @return Map of proposalId -> vote count
     */
    fun getVoteBreakdown(intentId: UUID): Map<String, Int> {
        val votes = getVotes(intentId)
        return votes.groupingBy { it.votedProposalId.toString() }
            .eachCount()
    }

    /**
     * Find the winning proposal based on vote tally.
     *
     * @param intentId The intent to find winner for
     * @return Pair of (winningProposalId, voteCount) or null if no votes
     */
    fun getWinner(intentId: UUID): Pair<UUID, Int>? {
        val breakdown = getVoteBreakdown(intentId)
        if (breakdown.isEmpty()) return null

        val winner = breakdown.maxByOrNull { it.value }
            ?: return null

        return Pair(UUID.fromString(winner.key), winner.value)
    }

    /**
     * Check if all votes are unanimous (all for same proposal).
     *
     * @param intentId The intent to check
     * @return true if all votes agree
     */
    fun isUnanimous(intentId: UUID): Boolean {
        val breakdown = getVoteBreakdown(intentId)
        return breakdown.size <= 1 && breakdown.isNotEmpty()
    }

    /**
     * Get instances that have already voted for this intent.
     *
     * @param intentId The intent to check
     * @return Set of instance IDs that have voted
     */
    fun getVotedInstances(intentId: UUID): Set<String> {
        val votes = getVotes(intentId)
        return votes.map { it.votingInstance }.toSet()
    }

    /**
     * Check if a specific instance has already voted.
     *
     * @param intentId The intent to check
     * @param instanceId The instance to check
     * @return true if instance has already voted
     */
    fun hasInstanceVoted(intentId: UUID, instanceId: String): Boolean {
        return getVotedInstances(intentId).contains(instanceId)
    }

    /**
     * Get current vote count for an intent.
     *
     * @param intentId The intent to check
     * @return Number of votes received
     */
    fun getVoteCount(intentId: UUID): Int {
        val key = voteKey(intentId)
        return listCommands.llen(key).toInt()
    }

    /**
     * Get consensus type based on vote distribution.
     *
     * @param intentId The intent to analyze
     * @return "unanimous", "majority", or "quorum"
     */
    fun getConsensusType(intentId: UUID): String {
        return when {
            isUnanimous(intentId) -> "unanimous"
            else -> {
                val breakdown = getVoteBreakdown(intentId)
                val totalVotes = getVoteCount(intentId)
                val winner = breakdown.maxByOrNull { it.value }

                if (winner != null && winner.value > totalVotes / 2) {
                    "majority"
                } else {
                    "quorum"
                }
            }
        }
    }

    /**
     * Get statistics about votes for an intent.
     *
     * @param intentId The intent to analyze
     * @return Map of statistics
     */
    fun getVoteStats(intentId: UUID): Map<String, Any> {
        val votes = getVotes(intentId)
        val (hasQuorum, quorumSize) = hasQuorum(intentId)
        val breakdown = getVoteBreakdown(intentId)
        val winner = getWinner(intentId)

        return mapOf(
            "intentId" to intentId,
            "totalVotes" to votes.size,
            "quorumSize" to quorumSize,
            "hasQuorum" to hasQuorum,
            "voteBreakdown" to breakdown,
            "winningProposalId" to (winner?.first?.toString() ?: "none"),
            "winningVotes" to (winner?.second ?: 0),
            "consensusType" to (if (hasQuorum) getConsensusType(intentId) else "pending"),
            "isUnanimous" to isUnanimous(intentId),
            "votedInstances" to getVotedInstances(intentId)
        )
    }
}
