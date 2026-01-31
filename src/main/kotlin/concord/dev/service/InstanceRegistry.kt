package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.config.InstanceConfig
import io.quarkus.logging.Log
import io.quarkus.redis.datasource.RedisDataSource
import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped
import java.time.Duration
import java.time.Instant

/**
 * Registry for tracking active AChan instances in a federated deployment.
 *
 * Uses Redis to maintain a distributed view of all active instances:
 * - Heartbeat mechanism (instances announce themselves every 10s)
 * - Health tracking (detect failed instances)
 * - Capability discovery (know which agents each instance runs)
 *
 * This enables:
 * - Federation awareness (how many instances are active)
 * - Consensus coordination (quorum calculation)
 * - Load balancing decisions
 * - Instance-specific routing
 */
@ApplicationScoped
class InstanceRegistry(
    private val redisDataSource: RedisDataSource,
    private val instanceConfig: InstanceConfig,
    private val objectMapper: ObjectMapper
) {

    private val keyCommands = redisDataSource.key()
    private val valueCommands = redisDataSource.value(String::class.java)

    companion object {
        private const val INSTANCE_KEY_PREFIX = "instance:"
        private const val HEARTBEAT_INTERVAL_SECONDS = 10L
        private val INSTANCE_TTL = Duration.ofSeconds(30) // 3x heartbeat interval

        fun instanceKey(instanceId: String): String = "$INSTANCE_KEY_PREFIX$instanceId"
    }

    /**
     * Data class representing instance information stored in Redis.
     */
    data class InstanceInfo(
        val instanceId: String,
        val region: String,
        val capabilities: Set<String>,
        val lastHeartbeat: Instant,
        val startedAt: Instant = Instant.now()
    )

    /**
     * Register this instance on startup and send periodic heartbeats.
     */
    @Scheduled(every = "${HEARTBEAT_INTERVAL_SECONDS}s")
    fun sendHeartbeat() {
        try {
            val info = InstanceInfo(
                instanceId = instanceConfig.instanceId,
                region = instanceConfig.region,
                capabilities = instanceConfig.capabilities,
                lastHeartbeat = Instant.now()
            )

            val key = instanceKey(instanceConfig.instanceId)
            val json = objectMapper.writeValueAsString(info)

            valueCommands.set(key, json)
            keyCommands.expire(key, INSTANCE_TTL)

            Log.debugf("Heartbeat sent for instance %s", instanceConfig.instanceId)
        } catch (e: Exception) {
            Log.errorf(e, "Failed to send heartbeat for instance %s", instanceConfig.instanceId)
        }
    }

    /**
     * Get information about all active instances.
     *
     * @return Map of instanceId -> InstanceInfo for all instances with valid heartbeats
     */
    fun getActiveInstances(): Map<String, InstanceInfo> {
        try {
            val pattern = "$INSTANCE_KEY_PREFIX*"
            val keys = keyCommands.keys(pattern)

            return keys.mapNotNull { key ->
                try {
                    val json = valueCommands.get(key)
                    if (json != null) {
                        val info = objectMapper.readValue(json, InstanceInfo::class.java)
                        info.instanceId to info
                    } else null
                } catch (e: Exception) {
                    Log.warnf(e, "Failed to parse instance info for key %s", key)
                    null
                }
            }.toMap()
        } catch (e: Exception) {
            Log.errorf(e, "Failed to get active instances")
            return emptyMap()
        }
    }

    /**
     * Get count of active instances.
     */
    fun getActiveInstanceCount(): Int {
        return getActiveInstances().size
    }

    /**
     * Get instances that have a specific capability.
     *
     * @param capability The capability to search for (e.g., "llm", "summary", "fact-check")
     * @return List of instance IDs with that capability
     */
    fun getInstancesWithCapability(capability: String): List<String> {
        return getActiveInstances().values
            .filter { it.capabilities.contains(capability) }
            .map { it.instanceId }
    }

    /**
     * Check if this is a multi-instance deployment (federation active).
     */
    fun isFederationActive(): Boolean {
        return getActiveInstanceCount() > 1
    }

    /**
     * Get quorum size for consensus (simple majority).
     */
    fun getQuorumSize(): Int {
        val totalInstances = getActiveInstanceCount()
        return (totalInstances / 2) + 1
    }

    /**
     * Check if a specific instance is healthy (heartbeat within TTL).
     */
    fun isInstanceHealthy(instanceId: String): Boolean {
        return getActiveInstances().containsKey(instanceId)
    }

    /**
     * Get federation statistics for monitoring.
     */
    fun getFederationStats(): Map<String, Any> {
        val instances = getActiveInstances()
        val capabilitiesCount = instances.values
            .flatMap { it.capabilities }
            .groupingBy { it }
            .eachCount()

        return mapOf(
            "activeInstances" to instances.size,
            "quorumSize" to getQuorumSize(),
            "federationActive" to isFederationActive(),
            "thisInstance" to instanceConfig.instanceId,
            "instances" to instances.keys,
            "capabilityDistribution" to capabilitiesCount
        )
    }

    /**
     * Log current federation status (useful for debugging).
     */
    @Scheduled(every = "60s")
    fun logFederationStatus() {
        val stats = getFederationStats()
        val instanceCount = stats["activeInstances"] as Int

        if (instanceCount > 1) {
            Log.infof("Federation Status: %d active instances, quorum: %d",
                instanceCount, stats["quorumSize"])
        }
    }
}
