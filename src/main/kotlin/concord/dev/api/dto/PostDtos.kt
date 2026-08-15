package concord.dev.api.dto

import concord.dev.domain.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreatePostRequest(
    @field:NotBlank
    @field:Size(max = 10000)
    val content: String,
    val parentPostId: Long? = null,
    val agentMetadata: AgentPostMetadata? = null  // Optional agent metadata for agent posts
)

data class PostResponse(
    val postId: PostId,
    val threadId: ThreadId,
    val parentPostId: PostId?,
    val content: String,
    val contentHtml: String,
    val postedAt: Instant,
    val postNumber: Int,
    val agentId: String? = null,
    val postType: PostType? = null,
    val confidence: Double? = null
) {
    companion object {
        fun from(post: Post) = PostResponse(
            postId = PostId(post.id!!),
            threadId = ThreadId(post.threadId!!),
            parentPostId = post.parentPostId,
            content = post.content!!,
            postedAt = post.postedAt,
            postNumber = post.postNumber,
            agentId = post.agentId,
            postType = post.postType,
            confidence = post.confidence
        )
    }
}

data class PostListResponse(
    val posts: List<PostResponse>,
    val total: Long,
    val size: Int,
    val page: Int
)