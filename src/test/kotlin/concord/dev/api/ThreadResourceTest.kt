package concord.dev.api

import concord.dev.api.dto.CreateThreadRequest
import concord.dev.api.dto.ThreadResponse
import concord.dev.domain.CrawlStatus
import concord.dev.service.KafkaProducerService
import io.quarkus.test.InjectMock
import io.quarkus.test.common.QuarkusTestResource
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import jakarta.transaction.Transactional
import org.hamcrest.CoreMatchers.*
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.greaterThan
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import java.util.UUID
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.notNullValue
import org.hamcrest.core.IsNull.nullValue
import concord.dev.test.PostgresTestResource

@QuarkusTest
@QuarkusTestResource(PostgresTestResource::class)
class ThreadResourceTest {

    @InjectMock
    lateinit var kafkaProducerService: KafkaProducerService

    @BeforeEach
    @Transactional
    fun setup() {
        // Clean up database before each test
        concord.dev.domain.Thread.deleteAll()

        // Note: kafkaProducerService is automatically mocked by @InjectMock
        // and void methods do nothing by default in Mockito
    }

    @Test
    fun `POST threads - creates new thread successfully`() {
        val request = CreateThreadRequest(
            url = "https://example.com/article",
            slug = "test-article"
        )

        val response = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .contentType(ContentType.JSON)
            .body("url", equalTo("https://example.com/article"))
            .body("slug", equalTo("test-article"))
            .body("postCount", equalTo(0))
            .body("crawlStatus", equalTo("PENDING"))
            .body("threadId", notNullValue())
            .body("createdAt", notNullValue())
            .body("updatedAt", notNullValue())
            .extract()
            .`as`(ThreadResponse::class.java)

        // Verify UUID is valid
        UUID.fromString(response.threadId.toString())
    }

    @Test
    fun `POST threads - creates thread without slug`() {
        val request = CreateThreadRequest(
            url = "https://example.com/no-slug"
        )

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .body("url", equalTo("https://example.com/no-slug"))
            .body("slug", nullValue())
    }

    @Test
    fun `POST threads - deduplication returns existing thread with 200`() {
        val url = "https://example.com/duplicate-test"
        val request = CreateThreadRequest(url = url, slug = "original")

        // Create first thread
        val firstResponse = given()
            .contentType(ContentType.JSON)
            .body(request)
            .`when`()
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        // Try to create the same thread again with different slug
        val duplicateRequest = CreateThreadRequest(url = url, slug = "duplicate")
        val secondResponse = given()
            .contentType(ContentType.JSON)
            .body(duplicateRequest)
            .`when`()
            .post("/api/v1/threads")
            .then()
            .statusCode(200) // Should return OK, not CREATED
            .extract()
            .`as`(ThreadResponse::class.java)

        // Should return the same thread ID
        assert(firstResponse.threadId == secondResponse.threadId)
        // Slug should remain unchanged (original)
        assert(secondResponse.slug == "original")
    }

    @Test
    fun `POST threads - URL normalization deduplicates correctly`() {
        // Create thread with normalized URL
        val request1 = CreateThreadRequest(url = "https://example.com/path")
        val response1 = given()
            .contentType(ContentType.JSON)
            .body(request1)
            .`when`()
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        // Try with different variations that normalize to same URL
        val variations = listOf(
            "http://example.com/path/",      // HTTP -> HTTPS, trailing slash
            "https://EXAMPLE.COM/path",      // uppercase host
            "https://example.com:443/path",  // default port
            "https://example.com/path#fragment" // fragment
        )

        variations.forEach { variation ->
            val request = CreateThreadRequest(url = variation)
            val response = given()
                .contentType(ContentType.JSON)
                .body(request)
                .`when`()
                .post("/api/v1/threads")
                .then()
                .statusCode(200) // Should deduplicate
                .extract()
                .`as`(ThreadResponse::class.java)

            // All variations should return the same thread
            assert(response.threadId == response1.threadId) {
                "URL variation '$variation' should deduplicate to same thread"
            }
        }
    }

    @Test
    fun `GET threads by id - retrieves existing thread`() {
        // Create a thread first
        val request = CreateThreadRequest(
            url = "https://example.com/get-test",
            slug = "get-by-id"
        )
        val created = given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        // Retrieve by ID
        given()
            .`when`()
            .get("/api/v1/threads/${created.threadId}")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("threadId", equalTo(created.threadId.toString()))
            .body("url", equalTo("https://example.com/get-test"))
            .body("slug", equalTo("get-by-id"))
    }

    @Test
    fun `GET threads by id - returns 404 for non-existent thread`() {
        val randomUuid = UUID.randomUUID()

        given()
            .`when`()
            .get("/api/v1/threads/$randomUuid")
            .then()
            .statusCode(404)
    }

    @Test
    fun `GET threads by url - retrieves thread by normalized URL`() {
        // Create a thread
        val request = CreateThreadRequest(
            url = "https://example.com/url-lookup",
            slug = "url-test"
        )
        val created = given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        // Retrieve by exact URL
        given()
            .queryParam("url", "https://example.com/url-lookup")
            .`when`()
            .get("/api/v1/threads/by-url")
            .then()
            .statusCode(200)
            .body("threadId", equalTo(created.threadId.toString()))
            .body("slug", equalTo("url-test"))
    }

    @Test
    fun `GET threads by url - finds thread with normalized URL variation`() {
        // Create thread with one URL format
        val request = CreateThreadRequest(url = "https://example.com/normalized")
        val created = given()
            .contentType(ContentType.JSON)
            .body(request)
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        // Query with different URL format that normalizes to same
        given()
            .queryParam("url", "http://EXAMPLE.COM:80/normalized/")
            .`when`()
            .get("/api/v1/threads/by-url")
            .then()
            .statusCode(200)
            .body("threadId", equalTo(created.threadId.toString()))
    }

    @Test
    fun `GET threads by url - returns 404 for non-existent URL`() {
        given()
            .queryParam("url", "https://non-existent.com/missing")
            .`when`()
            .get("/api/v1/threads/by-url")
            .then()
            .statusCode(404)
    }

    @Test
    fun `GET threads by url - returns 400 for missing URL parameter`() {
        given()
            .`when`()
            .get("/api/v1/threads/by-url")
            .then()
            .statusCode(400)
            .body("error", equalTo("URL parameter is required"))
    }

    @Test
    fun `GET threads by url - returns 400 for blank URL parameter`() {
        given()
            .queryParam("url", "   ")
            .`when`()
            .get("/api/v1/threads/by-url")
            .then()
            .statusCode(400)
            .body("error", equalTo("URL parameter is required"))
    }

    @Test
    fun `URL normalization - query parameter sorting`() {
        val url1 = "https://example.com/page?a=1&b=2&c=3"
        val url2 = "https://example.com/page?c=3&a=1&b=2"

        val request1 = CreateThreadRequest(url = url1)
        val response1 = given()
            .contentType(ContentType.JSON)
            .body(request1)
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        val request2 = CreateThreadRequest(url = url2)
        val response2 = given()
            .contentType(ContentType.JSON)
            .body(request2)
            .post("/api/v1/threads")
            .then()
            .statusCode(200) // Should deduplicate
            .extract()
            .`as`(ThreadResponse::class.java)

        assert(response1.threadId == response2.threadId)
    }

    @Test
    fun `URL normalization - preserves path and significant query params`() {
        val url1 = "https://example.com/path1?key=value"
        val url2 = "https://example.com/path2?key=value"

        val request1 = CreateThreadRequest(url = url1)
        val response1 = given()
            .contentType(ContentType.JSON)
            .body(request1)
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        val request2 = CreateThreadRequest(url = url2)
        val response2 = given()
            .contentType(ContentType.JSON)
            .body(request2)
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201))) // Different paths = different threads
            .extract()
            .`as`(ThreadResponse::class.java)

        // Should be different threads
        assert(response1.threadId != response2.threadId)
    }

    @Test
    fun `URL normalization - handles non-standard ports`() {
        val url1 = "https://example.com:8080/page"
        val url2 = "https://example.com:8080/page"

        val request1 = CreateThreadRequest(url = url1)
        val response1 = given()
            .contentType(ContentType.JSON)
            .body(request1)
            .post("/api/v1/threads")
            .then()
            .statusCode(anyOf(equalTo(200), equalTo(201)))
            .extract()
            .`as`(ThreadResponse::class.java)

        val request2 = CreateThreadRequest(url = url2)
        val response2 = given()
            .contentType(ContentType.JSON)
            .body(request2)
            .post("/api/v1/threads")
            .then()
            .statusCode(200) // Should deduplicate
            .extract()
            .`as`(ThreadResponse::class.java)

        assert(response1.threadId == response2.threadId)
    }
}
