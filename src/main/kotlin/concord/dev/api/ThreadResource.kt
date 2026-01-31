package concord.dev.api

import concord.dev.api.dto.CreateThreadRequest
import concord.dev.api.dto.ThreadResponse
import concord.dev.domain.PostCount
import concord.dev.domain.ThreadId
import concord.dev.service.ThreadService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/api/v1/threads")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ThreadResource(
    private val threadService: ThreadService
) {

    @POST
    fun createThread(request: CreateThreadRequest?): Response {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "Request body is required"))
                .build()
        }
        
        val thread = threadService.createOrGetThread(request.url, request.slug)
        val response = ThreadResponse.from(thread)

        // Return 201 if newly created, 200 if existing
        val status = if ((thread.postCount ?: PostCount(0)) == PostCount(0) && thread.createdAt == thread.updatedAt) {
            Response.Status.CREATED
        } else {
            Response.Status.OK
        }

        return Response.status(status).entity(response).build()
    }

    @GET
    @Path("/{threadId}")
    fun getThread(@PathParam("threadId") threadIdString: String): Response {
        val threadId = ThreadId(UUID.fromString(threadIdString))
        val thread = threadService.getThread(threadId)
            ?: return Response.status(Response.Status.NOT_FOUND).build()

        return Response.ok(ThreadResponse.from(thread)).build()
    }

    @GET
    @Path("/by-url")
    fun getThreadByUrl(@QueryParam("url") url: String?): Response {
        if (url.isNullOrBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(mapOf("error" to "URL parameter is required"))
                .build()
        }

        val thread = threadService.getThreadByUrl(url)
            ?: return Response.status(Response.Status.NOT_FOUND).build()

        return Response.ok(ThreadResponse.from(thread)).build()
    }
}