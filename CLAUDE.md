# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AChan is a URL-centric discussion platform built with Quarkus 3.28.2, Kotlin 2.2.20, and Java 21. See `ClaudeMe.md` for product specification and `cl-110.md` for technical specifications.

**Key Technologies:**
- **Backend**: Quarkus (Kotlin), Hibernate ORM with Panache
- **Database**: PostgreSQL 15 with JSONB support
- **Messaging**: Apache Kafka (simple client, not reactive)
- **Observability**: OpenTelemetry
- **Migrations**: Flyway

## Development Commands

### Local Development Setup
```bash
# Start infrastructure (Postgres, Kafka, Zookeeper, Kafka UI)
docker-compose up -d

# Start application with live reload
./gradlew quarkusDev
```

Dev UI: http://localhost:8080/q/dev/
Kafka UI: http://localhost:8081
Database: postgresql://localhost:54320/achan (user: achan, password: achan)

See `README.dev.md` for detailed local development guide.

### Building
```bash
./gradlew build
```
Produces `build/quarkus-app/quarkus-run.jar`

### Testing
```bash
./gradlew test
```

## Architecture

### Database Schema
- **thread** - URL-based discussion threads with normalization
- **post** - Sequential numbered posts within threads

Key patterns:
- JSONB columns use `@JdbcTypeCode(SqlTypes.JSON)` annotation
- Sequential post numbering uses SERIALIZABLE transaction isolation
- URL normalization follows cl-110.md specification

### API Structure
REST endpoints under `/api/v1/`:
- `POST /threads` - Create or get thread by URL
- `GET /threads/{id}` - Get thread by ID
- `GET /threads/by-url?url={url}` - Get thread by normalized URL
- `POST /threads/{id}/posts` - Create post (KNOWN ISSUE - see todo/ap-01)
- `GET /threads/{id}/posts` - List posts with pagination

### Kafka Integration
- **Producer**: `KafkaProducerService` emits URL crawl requests when threads are created
- **Consumer**: Not yet implemented (see todo/in-01)
- **Topic**: `url-crawl-requests`

### URL Normalization
Implemented in `URLNormalizer` per cl-110.md:
1. Parse with java.net.URI
2. Force HTTPS
3. Lowercase scheme + host
4. Remove default ports
5. Remove trailing slash (except root)
6. Sort query parameters
7. Remove fragment
8. Punycode non-ASCII domains

### Configuration
- Main config: `src/main/resources/application.yml`
- Docker Compose: `docker-compose.yml`
- Environment template: `.env.example`

### Kotlin-Quarkus Integration
- Uses `allOpen` plugin for: `@Path`, `@ApplicationScoped`, `@Entity`, `@QuarkusTest`
- Kotlin compiled with `-javaParameters` for parameter name reflection
- Jackson Kotlin module registered via `JacksonConfig`

## Known Issues

See `todo/` directory for structured tracking:
- **todo/ap-01-post-creation-endpoint.md** - HIGH PRIORITY - Post creation hangs
- **todo/in-01-kafka-consumer.md** - MEDIUM - Need crawl consumer
- **todo/ts-01-testing-infrastructure.md** - MEDIUM - Test coverage needed
- **todo/cf-01-configuration-cleanup.md** - LOW - Config warnings

## Testing Strategy

Currently:
- URLNormalizer has comprehensive unit tests ✅
- Other components need test coverage (see todo/ts-01)

## Troubleshooting

### Database Connection Issues
If you see "role 'achan' does not exist", check that:
1. Docker Compose is running (`docker-compose ps`)
2. No local Postgres is conflicting on port 54320
3. Connection string uses correct port: `jdbc:postgresql://localhost:54320/achan`

### Live Reload Not Working
- Editing `build.gradle` requires full restart
- Entity changes may need explicit rebuild
- Check logs for "Restarting quarkus due to changes in..."

### JSONB Errors
Ensure entity fields use: `@JdbcTypeCode(SqlTypes.JSON)` and `@Column(columnDefinition = "jsonb")`