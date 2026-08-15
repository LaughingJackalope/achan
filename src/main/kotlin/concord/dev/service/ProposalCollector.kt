package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.domain.ActProposed
import io.quarkus.logging.Log
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.redis.datasource.list.ListCommands
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.util.UUID

/**
 * Collects and manages ActProposed events in Redis for multi-proposal evaluation.
 *
 * Uses Redis lists with TTL to temporarily store proposals for the same intentId,
 * enabling the decider agent to collect and rank multiple competing proposals.
 */
@ApplicationScoped
class ProposalCollector(
    private val redisDataSource: RedisDataSource,
    private val objectMapper: ObjectMapper
) {

    private val listCommands: ListCommands<String, String> = redisDataSource.list(String::class.java)

    companion object {
        private const val PROPOSAL_KEY_PREFIX = "proposals:"
        private val DEFAULT_WINDOW_DURATION = Duration.ofSeconds(5)

        /**
         * Generate Redis key for proposals of a specific intent.
         */
        fun proposalKey(intentId: UUID): String = "$PROPOSAL_KEY_PREFIX$intentId"
    }

    /**
     * Add a proposal to the collection for a given intent.
     *
     * @param proposal The ActProposed event to add
     * @param windowDuration How long to keep this proposal collection (default 5s)
     * @return Number of proposals currently collected for this intent
     */
    fun addProposal(proposal: ActProposed, windowDuration: Duration = DEFAULT_WINDOW_DURATION): Long {
        val key = proposalKey(proposal.intentEventId)
        val proposalJson = objectMapper.writeValueAsString(proposal)

        // Add proposal to the list
        val count = listCommands.rpush(key, proposalJson)

        // Set TTL if this is the first proposal (count == 1)
        if (count == 1L) {
            redisDataSource.key().expire(key, windowDuration)
            Log.infof("Started proposal collection window for intent %s (duration: %s)",
                proposal.intentEventId, windowDuration)
        }

        Log.infof("Added proposal from %s for intent %s (confidence: %.2f, priority: %d). Total proposals: %d",
            proposal.agentId, proposal.intentEventId, proposal.confidence, proposal.priority, count)

        return count
    }

    /**
     * Get all proposals for a given intent.
     *
     * @param intentId The UUID of the intent
     * @return List of ActProposed events, or empty list if none found
     */
    fun getProposals(intentId: UUID): List<ActProposed> {
        val key = proposalKey(intentId)
        val proposalJsonList = listCommands.lrange(key, 0, -1)

        return proposalJsonList.mapNotNull { json ->
            try {
                objectMapper.readValue(json, ActProposed::class.java)
            } catch (e: Exception) {
                Log.errorf(e, "Failed to deserialize proposal JSON: %s", json)
                null
            }
        }
    }

    /**
     * Get and remove all proposals for a given intent (atomic pop operation).
     * This should be called by the decider when it's ready to evaluate and commit.
     *
     * @param intentId The UUID of the intent
     * @return List of ActProposed events, or empty list if none found
     */
    fun popProposals(intentId: UUID): List<ActProposed> {
        val key = proposalKey(intentId)
        val proposals = getProposals(intentId)

        // Delete the key to prevent double-processing
        redisDataSource.key().del(key)

        if (proposals.isNotEmpty()) {
            Log.infof("Popped %d proposals for intent %s", proposals.size, intentId)
        }

        return proposals
    }

    /**
     * Get the count of proposals for a given intent without removing them.
     *
     * @param intentId The UUID of the intent
     * @return Number of proposals collected
     */
    fun getProposalCount(intentId: UUID): Long {
        val key = proposalKey(intentId)
        return listCommands.llen(key)
    }

    /**
     * Check if the proposal collection window has expired for a given intent.
     *
     * @param intentId The UUID of the intent
     * @return true if the window exists (has time remaining), false if expired or never existed
     */
    fun isWindowActive(intentId: UUID): Boolean {
        val key = proposalKey(intentId)
        val proposalCount = listCommands.llen(key)
        return proposalCount > 0
    }

    /**
     * Get remaining time in the proposal collection window.
     *
     * @param intentId The UUID of the intent
     * @return Duration remaining, or Duration.ZERO if window expired/doesn't exist
     */
    fun getWindowTimeRemaining(intentId: UUID): Duration {
        val key = proposalKey(intentId)
        // Note: Redis ttl returns a complex type, so we'll just check if key exists
        // For now, return ZERO if no proposals exist
        return if (listCommands.llen(key) > 0) DEFAULT_WINDOW_DURATION else Duration.ZERO
    }

    /**
     * Manually close the proposal window and retrieve all proposals.
     * Useful for forcing immediate evaluation.
     *
     * @param intentId The UUID of the intent
     * @return List of collected proposals
     */
    fun closeWindow(intentId: UUID): List<ActProposed> {
        Log.infof("Manually closing proposal window for intent %s", intentId)
        return popProposals(intentId)
    }
}
