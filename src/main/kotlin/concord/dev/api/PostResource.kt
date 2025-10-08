package concord.dev.api

import concord.dev.api.dto.CreatePostRequest
import concord.dev.api.dto.PostListResponse
import concord.dev.api.dto.PostResponse
import concord.dev.service.PostService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import java.util.UUID

@Path("/api/v1/threads/{threadId}/posts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class PostResource(
    private val postService: PostService
) {

    @POST
    fun createPost(
        @PathParam("threadId") threadId: UUID,
        request: CreatePostRequest
    ): Response {
        // TODO: Extract metadata from request context (IP hash, user agent, session token)
        val metadata: String? = null

        val post = postService.createPost(
            threadId = threadId,
            content = request.content,
            parentPostId = request.parentPostId,
            metadata = metadata
        )

        return Response.status(Response.Status.CREATED)
            .entity(PostResponse.from(post))
            .build()
    }

    @GET
    fun getPosts(
        @PathParam("threadId") threadId: UUID,
        @QueryParam("limit") @DefaultValue("50") limit: Int,
        @QueryParam("offset") @DefaultValue("0") offset: Int,
        @QueryParam("parent_id") parentId: Long?
    ): Response {
        // Validate pagination params
        val validatedLimit = limit.coerceIn(1, 500)
        val validatedOffset = offset.coerceAtLeast(0)

        val posts = postService.getPosts(
            threadId = threadId,
            limit = validatedLimit,
            offset = validatedOffset,
            parentId = parentId
        )

        val total = postService.getPostCount(threadId)

        val response = PostListResponse(
            posts = posts.map { PostResponse.from(it) },
            total = total,
            limit = validatedLimit,
            offset = validatedOffset
        )

        return Response.ok(response).build()
    }
}