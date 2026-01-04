package concord.dev.api

import concord.dev.api.dto.CreatePostRequest
import concord.dev.api.dto.PostListResponse
import concord.dev.api.dto.PostResponse
import concord.dev.domain.PostId
import concord.dev.domain.ThreadId
import concord.dev.service.PostService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.validation.Valid
import java.util.UUID

@Path("/api/v1/threads/{threadId}/posts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class PostResource(
    private val postService: PostService
) {

    @POST
    fun createPost(
        @PathParam("threadId") threadIdString: String,
        @Valid request: CreatePostRequest
    ): Response {
        // TODO: Extract metadata from request context (IP hash, user agent, session token)
        val metadata: String? = null

        val threadId = ThreadId(UUID.fromString(threadIdString))
        val parentPostId = request.parentPostId?.let { PostId(it) }

        val post = postService.createPost(
            threadId = threadId,
            content = request.content,
            parentPostId = parentPostId,
            metadata = metadata
        )

        return Response.status(Response.Status.CREATED)
            .entity(PostResponse.from(post))
            .build()
    }

    @GET
    fun getPosts(
        @PathParam("threadId") threadIdString: String,
        @QueryParam("size") @DefaultValue("50") size: Int,
        @QueryParam("page") @DefaultValue("0") page: Int,
        @QueryParam("parent_id") parentId: Long?
    ): Response {
        // Validate pagination params
        val validatedSize = size.coerceIn(1, 500)
        val validatedPage = page.coerceAtLeast(0)

        val threadId = ThreadId(UUID.fromString(threadIdString))
        val parentPostId = parentId?.let { PostId(it) }

        val posts = postService.getPosts(
            threadId = threadId,
            size = validatedSize,
            page = validatedPage,
            parentId = parentPostId
        )

        val total = postService.getPostCount(threadId)

        val response = PostListResponse(
            posts = posts.map { PostResponse.from(it) },
            total = total,
            size = validatedSize,
            page = validatedPage
        )

        return Response.ok(response).build()
    }
}