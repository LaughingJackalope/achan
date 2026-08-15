package concord.dev.service

import com.fasterxml.jackson.databind.ObjectMapper
import concord.dev.domain.Agent
import io.quarkus.logging.Log
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional
import java.time.Instant

@ApplicationScoped
class AgentService(
    private val objectMapper: ObjectMapper
) {

    /**
     * Register a new agent or update if already exists
     */
    @Transactional
    fun registerAgent(
        agentId: String,
        agentType: String,
        capabilities: List<String> = emptyList(),
        instanceId: String? = null,
        metadata: Map<String, Any>? = null
    ): Agent {
        Log.infof("[AgentService] Registering agent - id=%s, type=%s", agentId, agentType)

        var agent = Agent.findByAgentId(agentId)

        if (agent != null) {
            // Update existing agent
            Log.infof("[AgentService] Updating existing agent - id=%s", agentId)
            agent.agentType = agentType
            agent.capabilities = serializeCapabilities(capabilities)
            agent.instanceId = instanceId
            agent.lastActiveAt = Instant.now()
            if (metadata != null) {
                agent.metadata = objectMapper.writeValueAsString(metadata)
            }
            agent.persist()
        } else {
            // Create new agent
            Log.infof("[AgentService] Creating new agent - id=%s", agentId)
            agent = Agent().apply {
                this.id = agentId
                this.agentType = agentType
                this.capabilities = serializeCapabilities(capabilities)
                this.instanceId = instanceId
                this.reputationScore = 0.0
                this.totalPosts = 0
                this.createdAt = Instant.now()
                this.lastActiveAt = Instant.now()
                if (metadata != null) {
                    this.metadata = objectMapper.writeValueAsString(metadata)
                }
            }
            agent.persist()
        }

        return agent
    }

    /**
     * Get agent by ID
     */
    fun getAgent(agentId: String): Agent? {
        return Agent.findByAgentId(agentId)
    }

    /**
     * List agents by type
     */
    fun listAgentsByType(agentType: String): List<Agent> {
        return Agent.findByType(agentType)
    }

    /**
     * List all agents
     */
    fun listAgents(limit: Int = 50): List<Agent> {
        return Agent.listActive(limit)
    }

    /**
     * Update agent activity timestamp (called when agent posts)
     */
    @Transactional
    fun recordActivity(agentId: String) {
        val agent = Agent.findByAgentId(agentId)
        if (agent != null) {
            agent.lastActiveAt = Instant.now()
            agent.totalPosts += 1
            agent.persist()
        } else {
            Log.warnf("[AgentService] Attempted to record activity for unknown agent: %s", agentId)
        }
    }

    /**
     * Update agent reputation (for future evaluation system)
     */
    @Transactional
    fun updateReputation(agentId: String, reputationDelta: Double) {
        val agent = Agent.findByAgentId(agentId)
        if (agent != null) {
            agent.reputationScore += reputationDelta
            agent.persist()
            Log.infof("[AgentService] Updated reputation for %s: %.2f -> %.2f",
                agentId, agent.reputationScore - reputationDelta, agent.reputationScore)
        }
    }

    /**
     * Deserialize capabilities from JSON
     */
    fun deserializeCapabilities(capabilitiesJson: String?): List<String> {
        if (capabilitiesJson.isNullOrBlank()) return emptyList()
        return try {
            objectMapper.readValue(capabilitiesJson, List::class.java) as List<String>
        } catch (e: Exception) {
            Log.warnf(e, "[AgentService] Failed to deserialize capabilities: %s", capabilitiesJson)
            emptyList()
        }
    }

    /**
     * Serialize capabilities to JSON
     */
    private fun serializeCapabilities(capabilities: List<String>): String {
        return objectMapper.writeValueAsString(capabilities)
    }
}
