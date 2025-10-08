# TS-01: Testing Infrastructure

## Status
**TODO** - Not started

## Objectives
Set up comprehensive testing infrastructure for AChan backend

## Test Categories Needed

### 1. Unit Tests
- **URLNormalizer** - ✅ DONE (tests exist and pass)
- **ThreadService** - TODO
- **PostService** - TODO
- **KafkaProducerService** - TODO

### 2. Integration Tests
- **REST API endpoints** - TODO
  - Thread creation and retrieval
  - Post creation and retrieval (when fixed)
  - Error cases (404, 400, etc.)
- **Database operations** - TODO
  - Flyway migrations
  - JSONB handling
  - UUID handling
  - Enum mapping
- **Kafka integration** - TODO
  - Message production
  - Message consumption (when implemented)

### 3. E2E Tests
- Full workflow: Create thread → Create posts → Retrieve thread with posts
- URL normalization → Thread deduplication
- Concurrent post creation (test SERIALIZABLE isolation)

## Test Frameworks
- JUnit 5 (already in dependencies)
- RestAssured (already in dependencies)
- Testcontainers (for Postgres and Kafka) - TODO: add dependency
- MockK or Mockito for mocking - TODO: add dependency

## Files to Create
- `src/test/kotlin/concord/dev/service/ThreadServiceTest.kt`
- `src/test/kotlin/concord/dev/service/PostServiceTest.kt`
- `src/test/kotlin/concord/dev/api/ThreadResourceTest.kt`
- `src/test/kotlin/concord/dev/api/PostResourceTest.kt`
- `src/test/kotlin/concord/dev/integration/EndToEndTest.kt`
- `src/test/resources/application-test.yml`

## Dependencies to Add
```gradle
testImplementation 'org.testcontainers:testcontainers:1.19.0'
testImplementation 'org.testcontainers:postgresql:1.19.0'
testImplementation 'org.testcontainers:kafka:1.19.0'
testImplementation 'io.mockk:mockk:1.13.8'
```

## Priority
**MEDIUM** - Important for confidence but not blocking feature development