package concord.dev.api

import concord.dev.api.dto.CreatePostRequest
import concord.dev.api.dto.PostListResponse
import concord.dev.api.dto.PostResponse
import concord.dev.domain.PostId
import concord.dev.domain.ThreadId
import concord.dev.service.AgentService
import concord.dev.service.PostCreatedEvent
import concord.dev.service.PostService
import concord.dev.service.ThreadService
import io.quarkus.logging.Log
import jakarta.enterprise.event.Event
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.validation.Valid
import java.util.UUID

@Path("/api/v1/threads/{threadId}/posts")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class PostResource(
    private val postService: PostService,
    private val agentService: AgentService,
    private val threadService: ThreadService,
    private val postCreatedEvent: Event<PostCreatedEvent>
) {

    @POST
    fun createPost(
        @PathParam("threadId") threadIdString: String,
        @Valid request: CreatePostRequest
    ): Response {
        Log.infof("[POST_CREATE] Starting post creation - threadId=%s, contentLength=%d", 
            threadIdString, request.content.length)
        
        try {
            // TODO: Extract metadata from request context (IP hash, user agent, session token)
            val metadata: String? = null

            val threadId = ThreadId(UUID.fromString(threadIdString))
            val parentPostId = request.parentPostId?.let { PostId(it) }
            
            Log.infof("[POST_CREATE] Parsed request - threadId=%s, parentPostId=%s", 
                threadId, parentPostId)

            // Extract agent metadata if present
            val agentId = request.agentMetadata?.agentId
            val postType = request.agentMetadata?.postType
            val confidence = request.agentMetadata?.confidence
            
            // Validate agent exists if agentId provided
            if (agentId != null) {
                val agent = agentService.getAgent(agentId)
                if (agent == null) {
                    Log.warnf("[POST_CREATE] Unknown agent ID: %s", agentId)
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(mapOf("error" to "Agent not registered: $agentId"))
                        .build()
                }
            }
            
            val post = postService.createPost(
                threadId = threadId,
                content = request.content,
                parentPostId = parentPostId,
                metadata = metadata,
                agentId = agentId,
                postType = postType,
                confidence = confidence
            )
            
            // Record agent activity
            if (agentId != null) {
                agentService.recordActivity(agentId)
            }
            
            Log.infof("[POST_CREATE] Post created successfully - postId=%d, postNumber=%d", 
                post.id, post.postNumber)

            // Fire event for subscription matching
            try {
                val thread = threadService.getThread(threadId)
                if (thread != null) {
                    postCreatedEvent.fire(PostCreatedEvent(post, thread))
                    Log.debugf("[POST_CREATE] PostCreatedEvent fired for post %d", post.id)
                }
            } catch (e: Exception) {
                Log.warnf(e, "[POST_CREATE] Failed to fire PostCreatedEvent, continuing")
                // Don't fail the request if event firing fails
            }

            return Response.status(Response.Status.CREATED)
                .entity(PostResponse.from(post))
                .build()
        } catch (e: Exception) {
            Log.errorf(e, "[POST_CREATE] Failed to create post - threadId=%s", threadIdString)
            throw e
        }
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
            posts = posts.map { post ->
                val contentHtml = markdownService.render(post.content)
                PostResponse.from(post, contentHtml)
            },
            total = total,
            size = validatedSize,
            page = validatedPage
        )

        return Response.ok(response).build()
    }
}