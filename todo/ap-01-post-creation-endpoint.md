# AP-01: Fix Post Creation Endpoint

## Status
**BLOCKED** - Endpoint hangs indefinitely with no error logs

## Context
The POST `/api/v1/threads/{threadId}/posts` endpoint is non-functional. All other endpoints work correctly:
- ✅ Thread creation (POST /api/v1/threads)
- ✅ Thread retrieval (GET /api/v1/threads/{id})
- ✅ Post listing (GET /api/v1/threads/{id}/posts)

## Symptoms
- Request hangs indefinitely (no HTTP response)
- No server-side errors logged
- GET requests to the same resource work fine
- Thread creation with similar data structures works fine

## Investigation Done
1. ✅ Fixed JSONB mapping with `@JdbcTypeCode(SqlTypes.JSON)` annotation
2. ✅ Fixed enum case mismatch (PENDING vs pending)
3. ✅ Fixed UUID query parameter in PostService.kt:37 (removed `.toString()`)
4. ✅ Created `JacksonConfig` to explicitly register Kotlin module
5. ✅ Verified quarkus-kotlin extension is present

## Suspected Causes
1. **Jackson Kotlin deserialization** - Despite explicit registration, Kotlin data class deserialization may be failing silently
2. **Transaction deadlock** - SERIALIZABLE isolation level in PostService.createPost() may be causing issues
3. **Circular dependency** - PostService → ThreadService.incrementPostCount() might be problematic

## Recommended Next Steps
1. Add explicit HTTP request logging to confirm request reaches endpoint
2. Test with simplified DTO (non-data class or Java POJO)
3. Remove SERIALIZABLE transaction isolation temporarily to test
4. Add debug logging in PostResource.createPost() entry point
5. Check for transaction timeout configuration

## Files Involved
- `src/main/kotlin/concord/dev/api/PostResource.kt:20-37`
- `src/main/kotlin/concord/dev/service/PostService.kt:17-56`
- `src/main/kotlin/concord/dev/api/dto/PostDtos.kt:7-10`
- `src/main/kotlin/concord/dev/config/JacksonConfig.kt`

## Test Command
```bash
curl -v -X POST 'http://localhost:8080/api/v1/threads/5779694f-970c-40fa-885c-bfe860908f15/posts' \
  -H 'Content-Type: application/json' \
  -d '{"content": "First post!"}'
```

## Priority
**HIGH** - Core functionality blocker