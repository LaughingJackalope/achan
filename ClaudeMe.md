# AChan - Product Kickoff Document

## Product Overview

**AChan** is a URL-centric discussion platform designed to generate high-quality conversational training data. Users initiate threads by submitting URLs; all discussion about a given URL happens in a single canonical thread. The system captures raw, unfiltered discourse while structuring it for future AI training applications.

**Core Philosophy:** Capture emergent data patterns without imposing premature taxonomic structure. The lack of content uploads, combined with URL-based deduplication, creates a naturally organized dataset where community interaction provides implicit quality signals.

---

## Core Concepts

### The URL as Primary Key
- Each unique URL gets exactly one thread
- URL normalization prevents fragmentation
- Optional human-readable slug for routing (`/{slug}/https://...`)
- Slug is cosmetic; URL is canonical identifier

### Immutable Discussion
- Posts cannot be edited or deleted once published
- Thread history is complete temporal record
- Captures evolution of understanding over time

### Minimal Moderation
- Platform assumes authorized traffic (handled upstream)
- Content crawling includes CSAM detection
- Early phase prioritizes data capture over curation

### Anonymous by Default
- No user accounts in MVP
- Session/behavioral tracking happens via logging infrastructure
- Encourages authentic discourse

---

## Data Model

### Thread
```
id: UUID (PK)
url: TEXT (UNIQUE, INDEXED) - normalized canonical URL
slug: TEXT (nullable) - human-readable path component
created_at: TIMESTAMP
updated_at: TIMESTAMP - last post time
post_count: INTEGER
metadata: JSONB - extracted from URL crawl (title, description, etc.)
crawl_status: ENUM(pending, complete, failed, blocked)
```

### Post
```
id: BIGSERIAL (PK)
thread_id: UUID (FK → Thread, INDEXED)
parent_post_id: BIGINT (nullable, FK → Post) - for nested replies
content: TEXT - markdown formatted
posted_at: TIMESTAMP
post_number: INTEGER - sequential within thread for >>references
metadata: JSONB - IP hash, user agent fingerprint, session token, etc.
```

### URLCrawlRequest (Kafka Message)
```json
{
  "thread_id": "uuid",
  "url": "string",
  "requested_at": "timestamp"
}
```

---

## API Specification

### Base Path
`/api/v1`

### Endpoints

#### `POST /threads`
Create or retrieve thread for URL

**Request:**
```json
{
  "url": "https://example.com/article",
  "slug": "optional-slug"
}
```

**Response (201 or 200):**
```json
{
  "thread_id": "uuid",
  "url": "https://example.com/article",
  "slug": "optional-slug",
  "created_at": "timestamp",
  "post_count": 0,
  "crawl_status": "pending"
}
```

**Behavior:**
- Normalize URL
- Check if thread exists → return existing
- If new → create thread, emit URLCrawlRequest to Kafka
- Return thread immediately (async crawl)

---

#### `GET /threads/{threadId}`
Retrieve thread metadata

**Response:**
```json
{
  "thread_id": "uuid",
  "url": "string",
  "slug": "string",
  "created_at": "timestamp",
  "updated_at": "timestamp",
  "post_count": 42,
  "crawl_status": "complete",
  "metadata": {
    "title": "Article Title",
    "description": "...",
    "crawled_at": "timestamp"
  }
}
```

---

#### `POST /threads/{threadId}/posts`
Create post in thread

**Request:**
```json
{
  "content": "This is **markdown** formatted. See >>5 for context.",
  "parent_post_id": 12345  // optional, for nested reply
}
```

**Response (201):**
```json
{
  "post_id": 12346,
  "thread_id": "uuid",
  "parent_post_id": 12345,
  "content": "This is **markdown** formatted. See >>5 for context.",
  "posted_at": "timestamp",
  "post_number": 42
}
```

**Behavior:**
- Increment thread.post_count
- Assign sequential post_number
- Store metadata (hashed IP, session token, user agent fingerprint)
- Update thread.updated_at

---

#### `GET /threads/{threadId}/posts`
Retrieve posts for thread

**Query Params:**
- `limit` (default: 50, max: 500)
- `offset` (default: 0)
- `parent_id` (optional: filter to replies of specific post)

**Response:**
```json
{
  "posts": [
    {
      "post_id": 1,
      "parent_post_id": null,
      "content": "...",
      "posted_at": "timestamp",
      "post_number": 1
    }
  ],
  "total": 150,
  "limit": 50,
  "offset": 0
}
```

---

#### `GET /threads/by-url`
Look up thread by URL

**Query Params:**
- `url` (required)

**Response:**
Same as `GET /threads/{threadId}` or 404

---

## System Architecture

```
┌─────────────┐
│  API Gateway│  ← Auth/AuthZ, Rate Limiting, WAF
└──────┬──────┘
       │
┌──────▼──────────┐
│  AChan API      │  ← Quarkus Native
│  (Stateless)    │
└─────┬───────┬───┘
      │       │
      │       └──────────┐
      │                  │
┌─────▼─────┐      ┌────▼────────┐
│ Postgres  │      │   Kafka     │
│  (Primary)│      │ (URLCrawl   │
└───────────┘      │  Requests)  │
                   └─────────────┘
      
┌──────────────────┐
│  Cache Layer     │  ← Implementation decides (Redis/Caffeine/etc)
│  (Thread/Post    │
│   retrieval)     │
└──────────────────┘

┌──────────────────┐
│  Search Service  │  ← Separate, TBD design
│  (Future)        │
└──────────────────┘
```

**Data Flow:**
1. Request → API Gateway (authz, rate limit) → AChan API
2. Create thread → Write to Postgres + Emit Kafka message
3. Create post → Write to Postgres + Invalidate cache
4. Read operations → Check cache → Postgres if miss

---

## Technical Requirements

### Framework & Runtime
- **Quarkus** (native compilation target)
- GraalVM native image for K8s deployment
- Reactive where beneficial, blocking fine for DB ops

### Database
- **PostgreSQL 15+**
- Connection pooling (Agroal)
- Migrations via Flyway/Liquibase
- Indexes on: `Thread.url`, `Post.thread_id`, `Post.posted_at`

### Messaging
- **Kafka** producer for URL crawl requests
- Topic: `url-crawl-requests`
- No consumption in this service (crawler is separate)

### Caching
- Implementation decides mechanism (Redis, Caffeine, etc.)
- Cache thread metadata (TTL: 5 min)
- Cache recent posts per thread (TTL: 2 min)
- Cache invalidation on writes

### Content Processing
- Markdown rendering: CommonMark spec
- Post reference parsing: `>>(\d+)` pattern
- Sanitize HTML output (prevent XSS)
- No image uploads, no file attachments

### Observability
- Structured JSON logging
- Metrics: request latency, DB query time, cache hit rate, Kafka publish latency
- Health checks: `/health/live`, `/health/ready`
- Trace IDs for request correlation

### Security
- Assume auth handled upstream (API Gateway)
- Store hashed IP (not raw) in post metadata
- No PII in logs
- SQL injection protection via parameterized queries
- Output sanitization for markdown rendering

### Deployment
- Containerized (Quarkus native Docker image)
- Kubernetes manifests (Deployment, Service, ConfigMap)
- Horizontal pod autoscaling ready (stateless)
- Cloud-agnostic (no AWS/GCP-specific dependencies)
- Environment-based configuration (dev/staging/prod)

---

## Out of Scope (MVP)

**Not included in initial build:**
- User accounts/authentication
- Frontend UI
- Full-text search (separate service)
- Content moderation tools (beyond CSAM blocking)
- Post voting/reactions
- User profiles/reputation
- Real-time updates (WebSocket/SSE)
- Media previews/embeds
- Thread archival/pruning
- Admin dashboard

---

## Success Criteria

**MVP is successful when:**

1. **Functional:**
   - Can create thread from URL (with deduplication)
   - Can post to thread (nested replies work)
   - Can retrieve thread + posts with pagination
   - Kafka messages successfully emitted for crawls

2. **Performance:**
   - Thread creation: <200ms p95
   - Post creation: <150ms p95
   - Thread retrieval: <100ms p95 (cached), <300ms (uncached)
   - Supports 100 concurrent users without degradation

3. **Operational:**
   - Deploys to K8s successfully
   - Health checks pass
   - Logs structured and queryable
   - Zero data loss on pod restarts

4. **Data Quality:**
   - URL normalization prevents duplicate threads
   - Post numbering is sequential and consistent
   - Thread timestamps update correctly
   - Markdown renders safely

---

## Next Steps

1. **Environment Setup:** Provision Postgres, Kafka, K8s namespace
2. **Schema Migration:** Create Flyway scripts for Thread/Post tables
3. **Core API:** Implement endpoints in order: POST /threads, POST /threads/{id}/posts, GET endpoints
4. **Kafka Integration:** Wire up URL crawl request publisher
5. **Caching Layer:** Implement chosen caching strategy
6. **Testing:** Integration tests for thread deduplication, nested posts, concurrent writes
7. **Deployment:** Build native image, create K8s manifests, deploy to staging
8. **Monitoring:** Configure metrics export, log aggregation

---

**Let's build.** Questions before we start coding?
