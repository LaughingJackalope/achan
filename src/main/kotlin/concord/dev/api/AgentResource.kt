package concord.dev.api

import concord.dev.api.dto.*
import concord.dev.service.AgentService
import io.quarkus.logging.Log
import jakarta.validation.Valid
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response

@Path("/api/v1/agents")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class AgentResource(
    private val agentService: AgentService
) {

    /**
     * Register a new agent or update existing
     * POST /api/v1/agents
     */
    @POST
    fun registerAgent(@Valid request: RegisterAgentRequest): Response {
        Log.infof("[AgentResource] Agent registration request - id=%s, type=%s",
            request.agentId, request.agentType)

        val agent = agentService.registerAgent(
            agentId = request.agentId,
            agentType = request.agentType,
            capabilities = request.capabilities,
            instanceId = request.instanceId,
            metadata = request.metadata
        )

        val response = AgentResponse.from(agent, request.capabilities)

        return Response.status(Response.Status.CREATED)
            .entity(response)
            .build()
    }

    /**
     * Get agent by ID
     * GET /api/v1/agents/{agentId}
     */
    @GET
    @Path("/{agentId}")
    fun getAgent(@PathParam("agentId") agentId: String): Response {
        val agent = agentService.getAgent(agentId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "Agent not found"))
                .build()

        val capabilities = agentService.deserializeCapabilities(agent.capabilities)
        return Response.ok(AgentResponse.from(agent, capabilities)).build()
    }

    /**
     * List all agents
     * GET /api/v1/agents?type=research_bot&limit=50
     */
    @GET
    fun listAgents(
        @QueryParam("type") agentType: String?,
        @QueryParam("limit") @DefaultValue("50") limit: Int
    ): Response {
        val agents = if (agentType != null) {
            agentService.listAgentsByType(agentType)
        } else {
            agentService.listAgents(limit.coerceIn(1, 100))
        }

        val response = AgentListResponse(
            agents = agents.map { agent ->
                val capabilities = agentService.deserializeCapabilities(agent.capabilities)
                AgentResponse.from(agent, capabilities)
            },
            total = agents.size
        )

        return Response.ok(response).build()
    }

    /**
     * Update agent (for future use)
     * PATCH /api/v1/agents/{agentId}
     */
    @PATCH
    @Path("/{agentId}")
    fun updateAgent(
        @PathParam("agentId") agentId: String,
        @Valid request: UpdateAgentRequest
    ): Response {
        val agent = agentService.getAgent(agentId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "Agent not found"))
                .build()

        // Update capabilities if provided
        if (request.capabilities != null) {
            agentService.registerAgent(
                agentId = agentId,
                agentType = agent.agentType!!,
                capabilities = request.capabilities,
                instanceId = agent.instanceId,
                metadata = request.metadata
            )
        }

        val updatedAgent = agentService.getAgent(agentId)!!
        val capabilities = agentService.deserializeCapabilities(updatedAgent.capabilities)
        return Response.ok(AgentResponse.from(updatedAgent, capabilities)).build()
    }
}
