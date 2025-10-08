# Checkpoint: Initial Backend Implementation

**Date:** October 7, 2025
**Commit:** 93288c7 - Initial AChan backend implementation

## What We Built

### Working Features ✅
1. **Thread Management API**
   - Create or retrieve thread by URL (POST /api/v1/threads)
   - Get thread by ID (GET /api/v1/threads/{id})
   - Get thread by normalized URL (GET /api/v1/threads/by-url)
   - Automatic URL normalization (8-step pipeline per cl-110.md)
   - Kafka message emission for URL crawl requests

2. **Post Listing API**
   - Get posts for a thread with pagination (GET /api/v1/threads/{id}/posts)
   - Support for filtering by parent post ID

3. **URL Normalization**
   - Complete implementation with comprehensive unit tests
   - Handles HTTPS forcing, domain lowercasing, port removal, query param sorting, fragment removal, punycode

4. **Infrastructure**
   - PostgreSQL 15 database with Flyway migrations
   - Kafka setup (producer configured, topic: url-crawl-requests)
   - Docker Compose for local development
   - Proper JSONB handling with `@JdbcTypeCode(SqlTypes.JSON)`

### Known Issues ⚠️

**High Priority:**
- **AP-01**: Post creation endpoint (POST /api/v1/threads/{id}/posts) hangs indefinitely
  - Request never returns
  - No server-side errors logged
  - Suspected Jackson Kotlin deserialization or transaction deadlock issue
  - See: `todo/ap-01-post-creation-endpoint.md`

**Medium Priority:**
- **IN-01**: Kafka consumer for URL crawl requests not implemented
- **TS-01**: Limited test coverage (only URLNormalizer has tests)

**Low Priority:**
- **CF-01**: Configuration deprecation warnings (OpenTelemetry, Kubernetes, logging)

### Architecture Decisions

1. **Technology Stack**
   - Quarkus 3.28.2 (Kotlin 2.2.20, Java 21)
   - PostgreSQL 15 with JSONB
   - Apache Kafka (simple client, not reactive per spec)
   - Hibernate ORM with Panache (Kotlin variant)

2. **Database Design**
   - UUID primary keys for threads
   - Sequential BIGSERIAL for posts
   - SERIALIZABLE transaction isolation for post numbering
   - JSONB metadata columns for extensibility
   - Unique constraint on normalized URLs

3. **API Design**
   - RESTful endpoints under /api/v1/
   - Create-or-get pattern for threads (idempotent by URL)
   - Pagination support for posts
   - Jackson Kotlin module for data class serialization

## Development Journey Highlights

### Wins
1. Successfully debugged complex Postgres connection issue (port conflict)
2. Fixed JSONB type mapping with modern Hibernate 6 annotations
3. Corrected enum case mismatch between SQL and Kotlin
4. Implemented comprehensive URL normalization with edge case handling
5. Set up complete local development environment with Docker Compose

### Challenges
1. Post creation endpoint remains non-functional after multiple debugging attempts
2. Live reload issues with certain types of changes (build.gradle)
3. Jackson Kotlin module integration complexity

### Time Investment
- Approximately 2-3 hours of development and debugging
- Major time spent on: database connection troubleshooting, JSONB mapping, post creation debugging

## Next Steps

For the next developer (human or AI):

1. **Immediate Priority**: Fix post creation endpoint (todo/ap-01)
   - Add HTTP request logging
   - Test with simplified non-data-class DTO
   - Verify transaction isolation isn't causing deadlock
   - Consider adding timeout configuration

2. **Short Term**: Implement Kafka consumer (todo/in-01)
   - Design crawling strategy
   - Implement consumer for url-crawl-requests topic
   - Update thread crawl_status based on results

3. **Medium Term**: Add comprehensive testing (todo/ts-01)
   - Integration tests for all endpoints
   - Database operation tests
   - Kafka integration tests

## Files of Interest

**Core Implementation:**
- `src/main/kotlin/concord/dev/api/` - REST endpoints
- `src/main/kotlin/concord/dev/service/` - Business logic
- `src/main/kotlin/concord/dev/domain/` - JPA entities
- `src/main/kotlin/concord/dev/util/URLNormalizer.kt` - URL normalization

**Configuration:**
- `src/main/resources/application.yml` - App config
- `docker-compose.yml` - Local infrastructure
- `src/main/resources/db/migration/V1__initial_schema.sql` - Database schema

**Documentation:**
- `CLAUDE.md` - Development guide for Claude Code
- `README.dev.md` - Local development setup
- `ClaudeMe.md` - Product specification
- `cl-110.md` - Technical specifications for URL normalization and post numbering
- `todo/` - Structured TODO tracking

## Testing the Stack

```bash
# Start infrastructure
docker-compose up -d

# Start application
./gradlew quarkusDev

# Test thread creation
curl -X POST 'http://localhost:8080/api/v1/threads' \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/article", "slug": "test-article"}'

# Test thread retrieval
curl 'http://localhost:8080/api/v1/threads/{id}'

# Test post listing
curl 'http://localhost:8080/api/v1/threads/{id}/posts'

# ⚠️ Post creation currently broken
curl -X POST 'http://localhost:8080/api/v1/threads/{id}/posts' \
  -H 'Content-Type: application/json' \
  -d '{"content": "First post!"}'
```

## Commit Statistics

- **40 files changed**
- **2,606 insertions**
- Includes: source code, tests, documentation, infrastructure config, TODO tracking