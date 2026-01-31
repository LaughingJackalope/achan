package concord.dev.api

import concord.dev.api.dto.ThreadDigestResponse
import concord.dev.domain.ThreadId
import concord.dev.service.DigestService
import concord.dev.service.ThreadService
import com.fasterxml.jackson.databind.ObjectMapper
import io.quarkus.logging.Log
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

/**
 * REST API for thread digest operations.
 * 
 * Provides endpoints for:
 * - Getting thread digests (AI-generated summaries)
 * - Triggering digest regeneration
 */
@Path("/api/v1/threads/{threadId}/digest")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class DigestResource(
    private val digestService: DigestService,
    private val threadService: ThreadService,
    private val objectMapper: ObjectMapper
) {

    /**
     * Get digest for a thread.
     * Generates digest if it doesn't exist or is stale (>1 hour old).
     */
    @GET
    fun getDigest(@PathParam("threadId") threadIdString: String): Response {
        Log.infof("[DIGEST] Getting digest for thread %s", threadIdString)
        
        return try {
            val threadId = ThreadId(UUID.fromString(threadIdString))
            
            // Check if thread exists
            val thread = threadService.getThread(threadId)
            if (thread == null) {
                Log.warnf("[DIGEST] Thread not found: %s", threadId)
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Thread not found"))
                    .build()
            }

            // Get or generate digest
            val digest = digestService.getDigest(threadId)
                ?: digestService.generateDigest(threadId)

            val response = ThreadDigestResponse.from(digest, thread.url, objectMapper)
            
            Log.infof("[DIGEST] Digest retrieved successfully for thread %s", threadId)
            Response.ok(response).build()
            
        } catch (e: IllegalArgumentException) {
            Log.warnf(e, "[DIGEST] Invalid thread ID: %s", threadIdString)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid thread ID format"))
                .build()
                
        } catch (e: IllegalStateException) {
            Log.warnf(e, "[DIGEST] Cannot generate digest: %s", e.message)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to e.message))
                .build()
                
        } catch (e: Exception) {
            Log.errorf(e, "[DIGEST] Failed to get digest for thread %s", threadIdString)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to generate digest"))
                .build()
        }
    }

    /**
     * Trigger digest regeneration for a thread.
     * Forces a fresh generation even if cached digest exists.
     */
    @POST
    @Path("/refresh")
    fun refreshDigest(@PathParam("threadId") threadIdString: String): Response {
        Log.infof("[DIGEST] Refreshing digest for thread %s", threadIdString)
        
        return try {
            val threadId = ThreadId(UUID.fromString(threadIdString))
            
            // Check if thread exists
            val thread = threadService.getThread(threadId)
            if (thread == null) {
                Log.warnf("[DIGEST] Thread not found: %s", threadId)
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Thread not found"))
                    .build()
            }

            // Force regeneration
            val digest = digestService.generateDigest(threadId, forceRefresh = true)
            val response = ThreadDigestResponse.from(digest, thread.url, objectMapper)
            
            Log.infof("[DIGEST] Digest refreshed successfully for thread %s", threadId)
            Response.ok(response).build()
            
        } catch (e: IllegalArgumentException) {
            Log.warnf(e, "[DIGEST] Invalid thread ID: %s", threadIdString)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid thread ID format"))
                .build()
                
        } catch (e: IllegalStateException) {
            Log.warnf(e, "[DIGEST] Cannot refresh digest: %s", e.message)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to e.message))
                .build()
                
        } catch (e: Exception) {
            Log.errorf(e, "[DIGEST] Failed to refresh digest for thread %s", threadIdString)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to refresh digest"))
                .build()
        }
    }

    /**
     * Delete digest for a thread.
     * Useful for testing or when digest is no longer needed.
     */
    @DELETE
    fun deleteDigest(@PathParam("threadId") threadIdString: String): Response {
        Log.infof("[DIGEST] Deleting digest for thread %s", threadIdString)
        
        return try {
            val threadId = ThreadId(UUID.fromString(threadIdString))
            
            val deleted = digestService.deleteDigest(threadId)
            if (deleted) {
                Log.infof("[DIGEST] Digest deleted for thread %s", threadId)
                Response.noContent().build()
            } else {
                Log.warnf("[DIGEST] No digest found to delete for thread %s", threadId)
                Response.status(Response.Status.NOT_FOUND)
                    .entity(mapOf("error" to "Digest not found"))
                    .build()
            }
            
        } catch (e: IllegalArgumentException) {
            Log.warnf(e, "[DIGEST] Invalid thread ID: %s", threadIdString)
            Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Invalid thread ID format"))
                .build()
                
        } catch (e: Exception) {
            Log.errorf(e, "[DIGEST] Failed to delete digest for thread %s", threadIdString)
            Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(mapOf("error" to "Failed to delete digest"))
                .build()
        }
    }
}
