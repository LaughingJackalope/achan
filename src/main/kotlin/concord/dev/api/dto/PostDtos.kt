package concord.dev.api.dto

import concord.dev.domain.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class CreatePostRequest(
    @field:NotBlank
    @field:Size(max = 10000)
    val content: String,
    val parentPostId: Long? = null
)

data class PostResponse(
    val postId: PostId,
    val threadId: ThreadId,
    val parentPostId: PostId?,
    val content: Content,
    val postedAt: Instant,
    val postNumber: PostNumber
) {
    companion object {
        fun from(post: Post) = PostResponse(
            postId = post.id!!,
            threadId = post.threadId!!,
            parentPostId = post.parentPostId,
            content = post.content!!,
            postedAt = post.postedAt,
            postNumber = post.postNumber
        )
    }
}

data class PostListResponse(
    val posts: List<PostResponse>,
    val total: Long,
    val size: Int,
    val page: Int
)