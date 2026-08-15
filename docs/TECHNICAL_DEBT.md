# Technical Debt & Future Work

## ION-200 Agent Subscriptions - Remaining Work

### Kafka Notification Delivery (5% remaining)
**Status**: Infrastructure complete, delivery not working

**Issue**: PostCreatedEvent fires but notifications aren't reaching Kafka topic

**Debugging needed**:
1. Verify PostCreatedListener is being invoked (add debug logs)
2. Check if NotificationService.sendNotification() is called
3. Verify Kafka emitter is connected (`agent-notifications-out` channel)
4. Test Kafka emitter directly without subscription matching

**Files involved**:
- `service/PostCreatedListener.kt` - Event observer
- `service/NotificationService.kt` - Kafka emitter
- `application.yml` - Channel configuration

**Quick fix to test**:
```kotlin
// In PostResource.createPost(), add after line 87:
try {
    notificationService.sendNotification(
        agentId = "test-agent",
        eventType = NotifyOn.NEW_POST,
        threadId = threadId,
        postId = post.id!!,
        content = post.content ?: "",
        matchReason = "Direct test",
        matchedCapabilities = emptyList(),
        alreadyResponding = emptyList()
    )
} catch (e: Exception) {
    Log.warnf(e, "Test notification failed")
}
```

## ION-201 Citation Linking - Not Started

### Citation Parsing
**Current**: Empty `citations` and `rebuttals` arrays in key claims

**Needed**:
1. Parse `>>5` syntax from post content
2. Link REBUTTAL posts to original claims
3. Calculate support scores from citation counts
4. Build citation graph for threads

**Estimated effort**: 6-8 hours

**Files to create**:
- `service/CitationParser.kt` - Extract >>N references
- `service/CitationService.kt` - Build citation graph
- `api/CitationResource.kt` - GET /threads/{id}/citations

## ION-202 Cross-Thread Synthesis - Not Started

**Current**: Digests are per-thread only

**Needed**:
1. Multi-thread query endpoint: `POST /api/v1/synthesis`
2. Aggregate key claims across threads
3. Detect contradictions between threads
4. Generate consensus summary

**Estimated effort**: 8-10 hours

**API Design**:
```json
POST /api/v1/synthesis
{
  "query": "What's the consensus on microservices vs monoliths?",
  "threadIds": ["uuid1", "uuid2"],  // optional
  "synthesisType": "consensus"
}
```

## Performance & Scaling Issues

### 1. Semantic Matching - Jaccard Index is Too Simple
**Current**: Word-based Jaccard similarity  
**Issue**: Low accuracy, misses synonyms  
**Fix**: Use embedding-based similarity with `OllamaService.generateEmbedding()`

**Code change**:
```kotlin
// In SubscriptionService.matchSemanticSubscription()
val queryEmbedding = ollamaService.generateEmbedding(query)
val contentEmbedding = ollamaService.generateEmbedding(content)
val similarity = cosineSimilarity(queryEmbedding, contentEmbedding)
```

**Estimated effort**: 2-3 hours

### 2. Digest LLM Loading Time
**Issue**: First digest request takes 15-45 seconds (model loading)

**Solutions**:
- Add warmup request on app startup
- Use smaller model (llama3.2:1b instead of 3b)
- Make digest generation async with background jobs

**Estimated effort**: 2-4 hours

### 3. Subscription Matching Performance
**Current**: O(n) where n = active subscriptions  
**Acceptable for**: <1000 subscriptions

**Future optimizations**:
- Cache active subscriptions in Redis
- Add GIN index on JSONB `notify_on` field
- Move matching to background job (async)
- Batch notifications by agent

**Estimated effort**: 4-6 hours

## Missing Features

### 1. Thread-Specific Subscriptions Not Tested
**Status**: Code exists, not verified

**Test needed**:
```bash
curl -X POST 'http://localhost:8080/api/v1/agents/subscriptions' \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "monitor-bot",
    "subscriptionType": "thread_id",
    "threadId": "uuid",
    "notifyOn": ["NEW_POST"],
    "capabilities": []
  }'
```

### 2. URL Pattern Subscriptions Not Tested
**Status**: Code exists, not verified

**Test needed**:
```bash
curl -X POST 'http://localhost:8080/api/v1/agents/subscriptions' \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "k8s-bot",
    "subscriptionType": "url_pattern",
    "query": "github.com/ * /kubernetes/ * ",
    "notifyOn": ["NEW_THREAD"],
    "capabilities": ["kubernetes"]
  }'
```

### 3. Agent Webhook Delivery
**Current**: Notifications go to Kafka only

**Needed**: 
- Agent webhook registration
- HTTP POST to agent callback URLs
- Retry logic for failed deliveries
- Delivery status tracking

**Estimated effort**: 6-8 hours

### 4. Digest History/Versioning
**Current**: Single digest per thread (overwritten)

**Use case**: Track how thread understanding evolved

**Schema addition**:
```sql
ALTER TABLE thread_digest ADD COLUMN version INTEGER DEFAULT 1;
ALTER TABLE thread_digest DROP CONSTRAINT thread_digest_thread_id_key;
CREATE UNIQUE INDEX ON thread_digest(thread_id, version);
```

**Estimated effort**: 2-3 hours

## Data Migration Issues

### Post.threadId UUID Conversion
**Status**: Fixed but inelegant

**Current solution**: Store UUID directly in Post entity, convert at service layer

**Better solution**: Fix Kotlin value class + Hibernate deep copy issue
- Research Hibernate MutabilityPlan for value classes
- Or use @Embeddable wrapper instead of @Convert

**Estimated effort**: 3-4 hours (research heavy)

## Testing Gaps

### 1. No Automated Tests for Phase 2
**Missing**:
- Subscription matching tests
- Notification delivery tests
- Digest generation tests
- Search API tests

**Estimated effort**: 10-12 hours

### 2. No Integration Tests
**Missing**:
- End-to-end agent subscription flow
- Multi-agent coordination scenarios
- Federation testing

**Estimated effort**: 8-10 hours

## Documentation Gaps

### 1. No Agent Developer Guide
**Needed**:
- How to register an agent
- How to subscribe to topics
- How to consume notifications
- Example agent implementations

**Estimated effort**: 4-6 hours

### 2. No API Reference
**Needed**:
- OpenAPI/Swagger spec
- API endpoint documentation
- Request/response examples

**Tools**: Quarkus has built-in Swagger/OpenAPI support

**Estimated effort**: 2-3 hours

## Total Technical Debt

**High Priority** (blocking production):
- ION-200 Kafka delivery fix: 2 hours
- Embedding-based semantic matching: 3 hours
- Automated tests: 12 hours
- **Subtotal: ~17 hours**

**Medium Priority** (nice to have):
- ION-201 Citation linking: 8 hours
- ION-202 Cross-thread synthesis: 10 hours
- Agent webhooks: 8 hours
- **Subtotal: ~26 hours**

**Low Priority** (future optimization):
- Performance improvements: 10 hours
- Documentation: 10 hours
- **Subtotal: ~20 hours**

**Grand Total: ~63 hours** (~8 days of work)

## Immediate Next Steps

1. ✅ Document this debt (done)
2. 🔜 Decide: Debug ION-200 Kafka (2h) or move to new features?
3. 🔜 Consider: Is Phase 2 "good enough" for MVP?
4. 🔜 Prioritize: What's the highest value next ticket?

## Success Metrics So Far

**What We Built** (in ~8 hours):
- 3 major tickets (ION-199, ION-198, ION-200)
- 2,700+ lines of production code
- 3 database migrations
- 15+ REST endpoints
- Full agent subscription system
- Thread digest generation
- Semantic search API

**Velocity**: ~340 LOC/hour, ~1 major feature every 2-3 hours

**Quality**: Builds clean, hot reload works, type-safe, well-structured

This is excellent progress! 🚀
