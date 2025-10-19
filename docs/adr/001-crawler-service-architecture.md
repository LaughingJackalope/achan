# ADR 001: Crawler Service Architecture - Separate Microservice

## Status
**ACCEPTED** - 2025-10-18

## Context

AChan requires URL crawling functionality to extract metadata (title, description, etc.) from submitted URLs. When users create threads via `POST /threads`, the system emits a Kafka message to the `url-crawl-requests` topic. We need to decide where the consumer implementation should live.

### Current Architecture
- Single Quarkus service (`achan-api`) handling HTTP API
- PostgreSQL for persistence
- Kafka for async messaging (producer implemented)
- Docker Compose for local development

### Requirements
1. Consume `url-crawl-requests` Kafka messages
2. Fetch URL content via HTTP
3. Extract metadata from HTML
4. Update Thread entity with crawl results
5. Handle failures (rate limits, blocked sites, timeouts)
6. CSAM detection per product specification

## Decision Drivers

1. **Failure Isolation** - Web crawling has unpredictable failure modes (malicious URLs, huge responses, slow targets)
2. **Resource Profile Mismatch** - API is latency-sensitive; crawler can tolerate minutes-long operations
3. **Security Boundaries** - Crawler needs external internet access; API should be more restricted
4. **Independent Deployment** - Crawler logic will iterate faster than API (domain-specific extractors, retry strategies)
5. **Future Scaling** - Different scaling characteristics (user traffic vs crawl queue depth)
6. **Agent Velocity** - Development speed with AI agents reduces traditional setup cost concerns

## Options Considered

### Option A: In-Process Kafka Consumer
**Description:** Implement consumer within `achan-api` service

**Pros:**
- Simpler deployment (single service)
- Direct Hibernate database access
- Faster initial implementation
- No inter-service communication

**Cons:**
- Crawler failures can impact API availability
- Shared resource pool (memory, threads, connections)
- Every crawler change requires API redeployment
- Cannot scale crawler independently
- Mixed security posture (API + external HTTP client)

### Option B: Separate Crawler Microservice ✅
**Description:** Implement consumer in new `achan-crawler` service

**Pros:**
- **Failure isolation** - Crawler crashes don't affect API
- **Resource isolation** - Separate memory/CPU limits
- **Security isolation** - Different network policies (crawler needs internet access)
- **Independent deployment** - Iterate on crawler without API changes
- **Independent scaling** - Scale crawler pods based on queue depth
- **Clean boundaries** - Better separation of concerns
- **Future-proof** - Avoids migration complexity later

**Cons:**
- Additional deployment artifact
- Need to manage two services
- Slightly more complex docker-compose setup

## Decision

**We choose Option B: Separate Crawler Microservice**

### Rationale

1. **Failure Isolation Justifies Complexity**
   - A malicious URL causing OOM should not take down the API
   - CSAM scanning may block for extended periods
   - Crawler failures are expected; API failures are critical

2. **Operational Characteristics Too Different**
   - API: <200ms latency, user-facing, predictable
   - Crawler: minutes acceptable, background, unpredictable
   - Different scaling triggers, resource needs, failure modes

3. **Development Velocity Enables Best Practices**
   - With AI agent support, marginal setup cost is 2-4 hours
   - Avoids future 2-3 day migration project
   - Build correct architecture from day one

4. **Low Traffic Doesn't Mean Simple Architecture**
   - Even with modest traffic, failure scenarios still exist
   - Easier to build right than refactor under pressure

## Implementation Plan

### Phase 1: Scaffold `achan-crawler` Service
1. Create new Quarkus project (or lightweight runtime)
2. Add Kafka consumer dependency
3. Add HTTP client (OkHttp or Ktor)
4. Shared message DTOs (copy or library)
5. Add to docker-compose.yml

### Phase 2: Core Crawler Logic
1. Implement `UrlCrawlConsumer` (Kafka consumer)
2. Implement `CrawlerService` (HTTP fetching + metadata extraction)
3. Direct Postgres access for Thread updates (same Hibernate entities)
4. Error handling and retry logic

### Phase 3: Production Hardening
1. Rate limiting per domain
2. CSAM detection integration
3. Domain-specific extractors (Twitter, Reddit, etc.)
4. Metrics and monitoring
5. Kubernetes manifests

### Database Access Strategy
Crawler will have **direct database access** for MVP:
- Same PostgreSQL connection
- Same Hibernate entities (shared or duplicated)
- Simple update queries for Thread.crawlStatus and Thread.metadata

**Future consideration:** Emit crawl results back to Kafka for full async pattern

## Consequences

### Positive
- ✅ API stability protected from crawler failures
- ✅ Can iterate on crawler logic rapidly
- ✅ Clear service boundaries for future team growth
- ✅ Independent scaling when needed
- ✅ Better security posture (network isolation)

### Negative
- ❌ Two services to deploy/monitor instead of one
- ❌ Additional 2-4 hours setup time
- ❌ Need to manage inter-service message contracts

### Neutral
- Database access is same (direct Postgres)
- Docker Compose adds one more service definition
- Total codebase size increases marginally

## Alternatives Considered and Rejected

### Modified Option A: In-Process with Extraction Plan
Keep consumer in-process but design for easy extraction later.

**Rejected because:**
- "Design for extraction" often fails under pressure
- Extraction still requires downtime and migration
- No failure isolation until extraction happens
- Marginal cost savings don't justify technical debt

## Follow-up Decisions Required

1. **Runtime choice for crawler:** Quarkus vs lightweight alternative (Ktor, Micronaut)
2. **Message contract:** Shared library vs duplicated DTOs
3. **Repository structure:** Monorepo vs separate repos
4. **Crawl result handling:** Direct DB write vs Kafka pub-sub pattern

## References

- `ClaudeMe.md` - Product specification
- `todo/in-01-kafka-consumer.md` - Original task description
- Linear Issue ION-25 - Architecture decision ticket

## Notes

This decision was made during MVP phase with the following context:
- Low expected traffic (no immediate scaling pressure)
- Development velocity enhanced by AI agent workflow
- Team comfortable with microservices operational overhead
- Long runway for implementation (weeks/months available)

The decision prioritizes **correctness over speed**, leveraging available development capacity to avoid future technical debt.
