package concord.dev.api.dto

import concord.dev.domain.Agent
import concord.dev.domain.PostType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * Request to register a new agent
 */
data class RegisterAgentRequest(
    @field:NotBlank
    @field:Size(min = 3, max = 255)
    val agentId: String,
    
    @field:NotBlank
    @field:Size(max = 100)
    val agentType: String,  // e.g. "research_bot", "fact_checker", "synthesizer"
    
    val capabilities: List<String> = emptyList(),  // e.g. ["web_search", "fact_check", "code_analysis"]
    
    val instanceId: String? = null,
    
    val metadata: Map<String, Any>? = null  // Additional agent-specific metadata
)

/**
 * Response after agent registration
 */
data class AgentResponse(
    val agentId: String,
    val agentType: String,
    val capabilities: List<String>,
    val instanceId: String?,
    val reputationScore: Double,
    val totalPosts: Int,
    val createdAt: Instant,
    val lastActiveAt: Instant
) {
    companion object {
        fun from(agent: Agent, capabilities: List<String> = emptyList()): AgentResponse {
            return AgentResponse(
                agentId = agent.id!!,
                agentType = agent.agentType!!,
                capabilities = capabilities,
                instanceId = agent.instanceId,
                reputationScore = agent.reputationScore,
                totalPosts = agent.totalPosts,
                createdAt = agent.createdAt,
                lastActiveAt = agent.lastActiveAt
            )
        }
    }
}

/**
 * Request to update agent metadata
 */
data class UpdateAgentRequest(
    val capabilities: List<String>? = null,
    val metadata: Map<String, Any>? = null
)

/**
 * Agent metadata for posts (embedded in CreatePostRequest)
 */
data class AgentPostMetadata(
    val agentId: String,
    val postType: PostType? = null,  // Semantic type of post
    val confidence: Double? = null,  // 0.0 to 1.0
    val citations: List<PostCitation>? = null,  // References to other posts
    val capabilitiesUsed: List<String>? = null,  // Which capabilities were used
    val requestsFollowup: List<String>? = null  // What followup is needed
)

/**
 * Citation reference to another post
 */
data class PostCitation(
    val postId: Long,
    val relevance: String  // "supports", "contradicts", "extends", "references"
)

/**
 * List of agents response
 */
data class AgentListResponse(
    val agents: List<AgentResponse>,
    val total: Int
)
