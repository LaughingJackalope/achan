package concord.dev.api

import concord.dev.api.dto.CreateSubscriptionRequest
import concord.dev.api.dto.SubscriptionListResponse
import concord.dev.api.dto.SubscriptionResponse
import concord.dev.domain.ThreadId
import concord.dev.service.SubscriptionService
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.logging.Log
import jakarta.validation.Valid
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

/**
 * REST API for agent subscription management.
 * 
 * Enables agents to:
 * - Register subscriptions (semantic, URL pattern, thread-specific)
 * - List their subscriptions
 * - Delete subscriptions
 */
@Path("/api/v1/agents/subscriptions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class SubscriptionResource(
    private val subscriptionService: SubscriptionService,
    private val objectMapper: ObjectMapper
) {

    /**
     * Create a new subscription.
     */
    @POST
    fun createSubscription(@Valid request: CreateSubscriptionRequest?): Response {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Request body is required"))
                .build()
        }

        Log.infof("[SUBSCRIPTION] Creating subscription for agent %s (type=%s)", 
            request.agentId, request.subscriptionType)

        return try {
            // Parse subscription type
            val subscriptionType = request.toSubscriptionType()
            
            // Parse notify events
            val notifyOn = request.toNotifyOnList()
            
            // Parse thread ID if present
            val threadId = request.threadId?.let { ThreadId(UUID.fromString(it)) }
            
            // Create subscription
            val subscription = subscriptionService.createSubscription(
                agentId = request.agentId,
                subscriptionType = subscriptionType,
                query = request.query,
                threadId = threadId,
                similarityThreshold = request.similarityThreshold,
                notifyOn = notifyOn,
                capabilities = request.capabilities
            )

            val response = SubscriptionResponse.from(subscription, objectMapper)
            
            Log.infof("[SUBSCRIPTION] Subscription created: id=%d", subscription.id)
            Response.status(Response.Status.CREATED)
                .entity(response)
                .build()

        } catch (e: IllegalArgumentException) {
            Log.warnf(e, "[SUBSCRIPTION] Invalid request")
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to (e.message ?: "Invalid request")))
                .build()

        } catch (e: Exception) {
            Log.errorf(e, "[SUBSCRIPTION] Failed to create subscription")
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to create subscription"))
                .build()
        }
    }

    /**
     * List subscriptions for an agent.
     */
    @GET
    fun listSubscriptions(@QueryParam("agentId") agentId: String?): Response {
        if (agentId.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "agentId query parameter is required"))
                .build()
        }

        Log.infof("[SUBSCRIPTION] Listing subscriptions for agent %s", agentId)

        return try {
            val subscriptions = subscriptionService.getSubscriptions(agentId)
            
            val response = SubscriptionListResponse(
                subscriptions = subscriptions.map { SubscriptionResponse.from(it, objectMapper) },
                total = subscriptions.size
            )

            Log.infof("[SUBSCRIPTION] Found %d subscriptions for agent %s", subscriptions.size, agentId)
            Response.ok(response).build()

        } catch (e: Exception) {
            Log.errorf(e, "[SUBSCRIPTION] Failed to list subscriptions for agent %s", agentId)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to list subscriptions"))
                .build()
        }
    }

    /**
     * Delete a specific subscription.
     */
    @DELETE
    @Path("/{subscriptionId}")
    fun deleteSubscription(@PathParam("subscriptionId") subscriptionId: Long): Response {
        Log.infof("[SUBSCRIPTION] Deleting subscription %d", subscriptionId)

        return try {
            val deleted = subscriptionService.deleteSubscription(subscriptionId)

            if (deleted) {
                Log.infof("[SUBSCRIPTION] Subscription %d deleted", subscriptionId)
                Response.noContent().build()
            } else {
                Log.warnf("[SUBSCRIPTION] Subscription %d not found", subscriptionId)
                Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Subscription not found"))
                    .build()
            }

        } catch (e: Exception) {
            Log.errorf(e, "[SUBSCRIPTION] Failed to delete subscription %d", subscriptionId)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to delete subscription"))
                .build()
        }
    }

    /**
     * Delete all subscriptions for an agent.
     */
    @DELETE
    fun deleteAllSubscriptions(@QueryParam("agentId") agentId: String?): Response {
        if (agentId.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "agentId query parameter is required"))
                .build()
        }

        Log.infof("[SUBSCRIPTION] Deleting all subscriptions for agent %s", agentId)

        return try {
            val count = subscriptionService.deleteAllSubscriptions(agentId)

            Log.infof("[SUBSCRIPTION] Deleted %d subscriptions for agent %s", count, agentId)
            Response.ok(mapOf("deleted" to count)).build()

        } catch (e: Exception) {
            Log.errorf(e, "[SUBSCRIPTION] Failed to delete subscriptions for agent %s", agentId)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to delete subscriptions"))
                .build()
        }
    }
}
