# AChan TODO Tracking

This directory contains structured TODO items for future development work on AChan.

## Naming Convention

Files follow the pattern: `{prefix}-{number}-{description}.md`

### Prefixes

- **AP** - API (REST endpoints, request/response handling)
- **IN** - Integration (Kafka, external services, crawling)
- **CF** - Configuration (application.yml, build.gradle, deployment)
- **TS** - Testing (unit tests, integration tests, e2e tests)
- **DB** - Database (migrations, schema changes, queries)
- **SC** - Security (authentication, authorization, rate limiting)
- **DO** - Documentation (README updates, API docs, architecture docs)

## Current TODOs

### High Priority
- **AP-01** - Fix Post Creation Endpoint (BLOCKED)

### Medium Priority
- **IN-01** - Implement Kafka Consumer for URL Crawl Requests
- **TS-01** - Testing Infrastructure

### Low Priority
- **CF-01** - Configuration Cleanup and Validation

## Completed Items

Move completed TODO files to `todo/completed/` with completion date in filename.

Example: `completed/ap-01-post-creation-endpoint-2025-10-08.md`