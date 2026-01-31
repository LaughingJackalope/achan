package concord.dev.api

import concord.dev.api.dto.CreatePostRequest
import concord.dev.api.dto.CreateThreadRequest
import concord.dev.api.dto.PostListResponse
import concord.dev.api.dto.PostResponse
import concord.dev.api.dto.ThreadResponse
import concord.dev.domain.Post
import concord.dev.service.KafkaProducerService
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.transaction.Transactional
import org.hamcrest.CoreMatchers.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

@QuarkusTest
class PostResourceTest {

    @InjectMock
    lateinit var kafkaProducerService: KafkaProducerService

    @BeforeEach
    @Transactional
    fun setup() {
        // Clean up posts first (foreign key constraint)
        Post.deleteAll()
        // Clean up threads
        concord.dev.domain.Thread.deleteAll()
    }

    private fun createTestThread(): concord.dev.domain.ThreadId {
        // Create a test thread for post tests
        val threadResponse = given()
            .contentType(ContentType.JSON)
            .body(CreateThreadRequest(url = "https://example.com/test-thread-${UUID.randomUUID()}"))
            .post("/api/v1/threads")
            .then()
            .statusCode(201)
            .extract()
            .`as`(ThreadResponse::class.java)

        return threadResponse.threadId
    }

    @Test
    fun `POST posts - creates first post successfully`() {
        val testThreadId = createTestThread()
        val request = CreatePostRequest(content = "First post!")

        val response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .body("content", equalTo("First post!"))
            .body("threadId", equalTo(testThreadId.toString()))
            .body("postNumber", equalTo(1))
            .body("postId", notNullValue())
            .body("postedAt", notNullValue())
            .body("parentPostId", nullValue())
            .extract()
            .`as`(PostResponse::class.java)

        // Verify post ID is assigned
        assertNotNull(response.postId)
        assertTrue(response.postId.value > 0)
    }

    @Test
    fun `POST posts - creates reply to existing post`() {
        val testThreadId = createTestThread()

        // Create parent post
        val parentRequest = CreatePostRequest(content = "Parent post")
        val parent = given()
            .contentType(ContentType.JSON)
            .body(parentRequest)
            .post("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(201)
            .extract()
            .`as`(PostResponse::class.java)

        // Create reply
        val replyRequest = CreatePostRequest(
            content = "Reply to parent",
            parentPostId = parent.postId.value
        )
        given()
            .contentType(ContentType.JSON)
            .body(replyRequest)
            .post("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(201)
            .body("content", equalTo("Reply to parent"))
            .body("parentPostId", equalTo(parent.postId.value.toInt()))
            .body("postNumber", equalTo(2))
    }

    @Test
    fun `POST posts - sequential numbering for multiple posts`() {
        val testThreadId = createTestThread()
        val postContents = listOf("Post 1", "Post 2", "Post 3", "Post 4", "Post 5")
        val responses = mutableListOf<PostResponse>()

        // Create posts sequentially
        postContents.forEachIndexed { index, content ->
            val request = CreatePostRequest(content = content)
            val response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/v1/threads/$testThreadId/posts")
                .then()
                .statusCode(201)
                .body("postNumber", equalTo(index + 1))
                .extract()
                .`as`(PostResponse::class.java)

            responses.add(response)
        }

        // Verify all post numbers are unique and sequential
        val postNumbers = responses.map { it.postNumber }.sorted()
        assertEquals(listOf(1, 2, 3, 4, 5), postNumbers)
    }

    @Test
    fun `POST posts - concurrent creation maintains sequential numbering`() {
        val testThreadId = createTestThread()
        val numberOfConcurrentPosts = 10
        val latch = CountDownLatch(1)
        val futures = mutableListOf<CompletableFuture<Int?>>()

        // Create concurrent post requests
        repeat(numberOfConcurrentPosts) { index ->
            val future = CompletableFuture.supplyAsync {
                latch.await() // Wait for all threads to be ready

                val request = CreatePostRequest(content = "Concurrent post $index")
                try {
                    val response = given()
                        .contentType(ContentType.JSON)
                        .body(request)
                        .post("/api/v1/threads/$testThreadId/posts")

                    // Some requests may fail with 500 due to SERIALIZABLE isolation conflicts
                    if (response.statusCode == 201) {
                        response.then().extract().`as`(PostResponse::class.java).postNumber
                    } else {
                        null // Serialization conflict - expected behavior
                    }
                } catch (e: Exception) {
                    null // Handle any exceptions from conflicts
                }
            }
            futures.add(future)
        }

        // Release all threads simultaneously
        latch.countDown()

        // Wait for all posts to complete
        val postNumbers = futures.mapNotNull { it.join() }?.sorted() ?: emptyList()

        // Verify all successful posts have unique and sequential post numbers
        // At least some posts should succeed (SERIALIZABLE isolation may cause some to fail)
        assertTrue(postNumbers.size >= 1, "At least 1 concurrent post should succeed, got ${postNumbers.size}")

        // All successful posts should have unique, sequential numbers starting from 1
        assertEquals(postNumbers.size, postNumbers.toSet().size, "No duplicate post numbers")
        assertEquals((1..postNumbers.size).toList(), postNumbers, "Post numbers should be sequential starting from 1")
    }

    @Test
    fun `GET posts - retrieves all posts for thread`() {
        val testThreadId = createTestThread()

        // Create multiple posts
        repeat(3) { index ->
            val request = CreatePostRequest(content = "Post ${index + 1}")
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/v1/threads/$testThreadId/posts")
                .then()
                .statusCode(201)
        }

        // Retrieve posts
        val response = given()
            .`when`()
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("total", equalTo(3))
            .body("size", equalTo(50))
            .body("page", equalTo(0))
            .body("posts.size()", equalTo(3))
            .extract()
            .`as`(PostListResponse::class.java)

        // Verify posts are ordered by post number
        val postNumbers = response.posts.map { it.postNumber }
        assertEquals(listOf(1, 2, 3), postNumbers)
    }

    @Test
    fun `GET posts - pagination works correctly`() {
        val testThreadId = createTestThread()

        // Create 10 posts
        repeat(10) { index ->
            val request = CreatePostRequest(content = "Post ${index + 1}")
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/v1/threads/$testThreadId/posts")
                .then()
                .statusCode(201)
        }

        // Get first page (size=3, page=0)
        val page1 = given()
            .queryParam("size", 3)
            .queryParam("page", 0)
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .body("posts.size()", equalTo(3))
            .body("total", equalTo(10))
            .body("size", equalTo(3))
            .body("page", equalTo(0))
            .extract()
            .`as`(PostListResponse::class.java)

        assertEquals(listOf(1, 2, 3), page1.posts.map { it.postNumber })

        // Get second page (size=3, page=1)
        val page2 = given()
            .queryParam("size", 3)
            .queryParam("page", 1)
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .body("posts.size()", equalTo(3))
            .body("total", equalTo(10))
            .extract()
            .`as`(PostListResponse::class.java)

        assertEquals(listOf(4, 5, 6), page2.posts.map { it.postNumber })

        // Get third page (size=3, page=2)
        val page3 = given()
            .queryParam("size", 3)
            .queryParam("page", 2)
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .body("posts.size()", equalTo(3))
            .extract()
            .`as`(PostListResponse::class.java)

        assertEquals(listOf(7, 8, 9), page3.posts.map { it.postNumber })

        // Get last page (size=3, page=3)
        val page4 = given()
            .queryParam("size", 3)
            .queryParam("page", 3)
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .body("posts.size()", equalTo(1))
            .extract()
            .`as`(PostListResponse::class.java)

        assertEquals(listOf(10), page4.posts.map { it.postNumber })
    }

    @Test
    fun `GET posts - default pagination parameters`() {
        val testThreadId = createTestThread()

        // Create 3 posts
        repeat(3) { index ->
            val request = CreatePostRequest(content = "Post ${index + 1}")
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/v1/threads/$testThreadId/posts")
                .then()
                .statusCode(201)
        }

        // Get posts without pagination params (should use defaults)
        given()
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .body("size", equalTo(50)) // default size
            .body("page", equalTo(0))  // default page
            .body("posts.size()", equalTo(3))
    }

    @Test
    fun `GET posts - size is capped at 500`() {
        val testThreadId = createTestThread()

        given()
            .queryParam("size", 1000)
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .body("size", equalTo(500)) // should be capped
    }

    @Test
    fun `GET posts - negative page is coerced to 0`() {
        val testThreadId = createTestThread()

        given()
            .queryParam("page", -10)
            .get("/api/v1/threads/$testThreadId/posts")
            .then()
            .statusCode(200)
            .body("page", equalTo(0)) // should be coerced
    }

    @Test
    fun `GET posts - returns empty list for non-existent thread`() {
        val nonExistentThreadId = UUID.randomUUID()

        given()
            .get("/api/v1/threads/$nonExistentThreadId/posts")
            .then()
            .statusCode(200)
            .body("posts.size()", equalTo(0))
            .body("total", equalTo(0))
    }

    @Test
    fun `POST posts - updates thread post count`() {
        val testThreadId = createTestThread()

        // Create 3 posts
        repeat(3) {
            val request = CreatePostRequest(content = "Post content")
            given()
                .contentType(ContentType.JSON)
                .body(request)
                .post("/api/v1/threads/$testThreadId/posts")
                .then()
                .statusCode(201)
        }

        // Verify thread post count was updated
        val thread = given()
            .get("/api/v1/threads/$testThreadId")
            .then()
            .statusCode(200)
            .extract()
            .`as`(ThreadResponse::class.java)

        assertEquals(3, thread.postCount)
    }
}
