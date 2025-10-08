package concord.dev.api.dto

import concord.dev.domain.Post
import java.time.Instant
import java.util.UUID

data class CreatePostRequest(
    val content: String,
    val parentPostId: Long? = null
)

data class PostResponse(
    val postId: Long,
    val threadId: UUID,
    val parentPostId: Long?,
    val content: String,
    val postedAt: Instant,
    val postNumber: Int
) {
    companion object {
        fun from(post: Post) = PostResponse(
            postId = post.id!!,
            threadId = post.threadId,
            parentPostId = post.parentPostId,
            content = post.content,
            postedAt = post.postedAt,
            postNumber = post.postNumber
        )
    }
}

data class PostListResponse(
    val posts: List<PostResponse>,
    val total: Long,
    val limit: Int,
    val offset: Int
)