package concord.dev.api

import concord.dev.api.dto.PageContentResponse
import concord.dev.service.PageContentService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/api/v1/threads/{threadId}/content")
@Produces(MediaType.APPLICATION_JSON)
class PageContentResource(
    private val pageContentService: PageContentService
) {

    @GET
    fun getContent(@PathParam("threadId") threadId: UUID): Response {
        val content = pageContentService.getContent(threadId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "No content found for this thread"))
                .build()

        return Response.ok(PageContentResponse.from(content)).build()
    }

    @GET
    @Path("/raw-html")
    fun getRawHtml(@PathParam("threadId") threadId: UUID): Response {
        val content = pageContentService.getContent(threadId)
            ?: return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "No content found for this thread"))
                .build()

        if (content.rawHtml == null) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(mapOf("error" to "Raw HTML not available for this thread"))
                .build()
        }

        return Response.ok(mapOf("rawHtml" to content.rawHtml)).build()
    }
}