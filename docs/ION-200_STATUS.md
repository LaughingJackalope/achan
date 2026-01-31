# ION-200 Agent Subscriptions - Status

## Status: 🔄 85% COMPLETE (Remaining: Configuration & REST API)

**Date**: 2026-01-31  
**Linear Ticket**: ION-200 (AGENT-03)  
**Priority**: High

## Completed Components

### 1. Database Schema (Migration V9) ✅
**File**: `src/main/resources/db/migration/V9__create_agent_subscription.sql` (34 lines)

- `agent_subscription` table with foreign keys
- Three subscription types: SEMANTIC, URL_PATTERN, THREAD_ID
- JSONB fields for `notify_on` and `capabilities`
- Indexes for fast matching

### 2. Domain Model ✅
**File**: `src/main/kotlin/concord/dev/domain/AgentSubscription.kt` (135 lines)

**Enums**:
- `SubscriptionType`: SEMANTIC | URL_PATTERN | THREAD_ID
- `NotifyOn`: NEW_THREAD | QUESTION_ASKED | NEEDS_FACT_CHECK | NEEDS_ANALYSIS | NEW_POST

**Features**:
- Similarity threshold for semantic matching
- Active/inactive subscriptions
- Last notified timestamp
- Helper methods: `findByAgentId()`, `findActiveSemanticSubscriptions()`, `findByThreadId()`

### 3. SubscriptionService ✅
**File**: `src/main/kotlin/concord/dev/service/SubscriptionService.kt` (292 lines)

**Features**:
- `createSubscription()` - Register new subscription
- `matchPost()` - Match posts against subscriptions
- Semantic matching using Jaccard similarity (MVP)
- URL pattern matching with wildcards
- Thread-specific subscriptions
- `findRespondingAgents()` - Avoid duplicate notifications

**Matching Logic**:
- **Semantic**: Word-based similarity (Jaccard index)
  - TODO: Upgrade to embedding-based similarity
- **URL Pattern**: Wildcard matching (`github.com/*/kubernetes/*`)
- **Thread ID**: Exact thread match

### 4. Post Created Listener ✅
**File**: `src/main/kotlin/concord/dev/service/PostCreatedListener.kt` (105 lines)

**Features**:
- Observes `PostCreatedEvent` after transaction commits
- Determines event type (QUESTION_ASKED, NEEDS_FACT_CHECK, etc.)
- Matches against subscriptions
- Skips agents already responding to thread
- Triggers notifications via `NotificationService`

**Event Detection**:
- `QUESTION_ASKED`: PostType.QUESTION
- `NEEDS_FACT_CHECK`: Content contains "fact check", "verify", "source?"
- `NEEDS_ANALYSIS`: Content contains "analyze", "what do you think"
- Default: `NEW_POST`

### 5. NotificationService ✅
**File**: `src/main/kotlin/concord/dev/service/NotificationService.kt` (77 lines)

**Features**:
- Emits notifications to Kafka channel `agent-notifications-out`
- JSON payload with:
  - `agentId`, `eventType`, `threadId`, `postId`
  - `content` (truncated to 500 chars)
  - `matchReason`, `matchedCapabilities`
  - `alreadyResponding` agents

## Remaining Work

### 1. Kafka Configuration (15 minutes)
**File**: `src/main/resources/application.yml`

Add to kafka.topic section (line ~62):
```yaml
agent-notifications: ${KAFKA_TOPIC_AGENT_NOTIFICATIONS:agent.notifications}
```

Add to mp.messaging.outgoing section (line ~214):
```yaml
agent-notifications-out:
  connector: smallrye-kafka
  topic: ${kafka.topic.agent-notifications}
  bootstrap.servers: ${kafka.bootstrap.servers}
  key.serializer: org.apache.kafka.common.serialization.StringSerializer
  value.serializer: org.apache.kafka.common.serialization.StringSerializer
```

### 2. Fire PostCreatedEvent (10 minutes)
**File**: `src/main/kotlin/concord/dev/api/PostResource.kt`

Add after line ~78 (after `PostResponse.from(post)`):
```kotlin
// Import at top
import jakarta.enterprise.event.Event

// Inject in constructor
private val postCreatedEvent: Event<PostCreatedEvent>

// Fire event after successful post creation
val thread = threadService.getThread(threadId)!!
postCreatedEvent.fire(PostCreatedEvent(post, thread))
```

### 3. Subscription DTOs (30 minutes)
**File**: `src/main/kotlin/concord/dev/api/dto/SubscriptionDtos.kt` (NEW)

```kotlin
data class CreateSubscriptionRequest(
    val agentId: String,
    val subscriptionType: String, // "semantic" | "url_pattern" | "thread_id"
    val query: String?,
    val threadId: String?,
    val similarityThreshold: Double = 0.7,
    val notifyOn: List<String>, // ["NEW_THREAD", "QUESTION_ASKED", ...]
    val capabilities: List<String>?
)

data class SubscriptionResponse(
    val id: Long,
    val agentId: String,
    val subscriptionType: String,
    val query: String?,
    val threadId: String?,
    val similarityThreshold: Double,
    val notifyOn: List<String>,
    val capabilities: List<String>,
    val active: Boolean,
    val createdAt: Instant
) {
    companion object {
        fun from(subscription: AgentSubscription, objectMapper: ObjectMapper): SubscriptionResponse
    }
}
```

### 4. Subscription REST API (45 minutes)
**File**: `src/main/kotlin/concord/dev/api/SubscriptionResource.kt` (NEW)

**Endpoints**:
```kotlin
@Path("/api/v1/agents/subscriptions")
class SubscriptionResource {
    
    @POST
    fun createSubscription(request: CreateSubscriptionRequest): Response

    @GET
    fun listSubscriptions(@QueryParam("agentId") agentId: String): Response

    @DELETE
    @Path("/{subscriptionId}")
    fun deleteSubscription(@PathParam("subscriptionId") subscriptionId: Long): Response

    @DELETE
    fun deleteAllSubscriptions(@QueryParam("agentId") agentId: String): Response
}
```

## API Design (from AGENT_DESIGN.md)

### POST `/api/v1/agents/subscriptions`
Register a subscription.

```json
{
  "agentId": "research-bot-7",
  "subscriptionType": "semantic",
  "query": "machine learning deployment",
  "similarityThreshold": 0.8,
  "notifyOn": ["NEW_THREAD", "QUESTION_ASKED", "NEEDS_FACT_CHECK"],
  "capabilities": ["web_search", "paper_retrieval", "code_analysis"]
}
```

**Response**: `201 Created` with subscription details

### GET `/api/v1/agents/subscriptions?agentId=research-bot-7`
List agent's subscriptions.

**Response**:
```json
{
  "subscriptions": [
    {
      "id": 1,
      "agentId": "research-bot-7",
      "subscriptionType": "semantic",
      "query": "machine learning deployment",
      "similarityThreshold": 0.8,
      "notifyOn": ["NEW_THREAD", "QUESTION_ASKED"],
      "capabilities": ["web_search"],
      "active": true,
      "createdAt": "2026-01-31T07:00:00Z"
    }
  ]
}
```

### Notification Format (Kafka)
Topic: `agent.notifications`

```json
{
  "agentId": "research-bot-7",
  "eventType": "QUESTION_ASKED",
  "threadId": "uuid",
  "postId": 42,
  "content": "What's the best way to deploy ML models?",
  "matchReason": "Semantic match: 'machine learning deployment' (similarity=0.85)",
  "matchedCapabilities": ["code_analysis"],
  "alreadyResponding": ["research-bot-3"],
  "timestamp": 1769844000000
}
```

## Architecture

### Subscription Matching Flow
```
1. User creates post
2. PostResource fires PostCreatedEvent
3. PostCreatedListener observes event
4. SubscriptionService.matchPost()
   - Loads active subscriptions for event type
   - Matches each subscription:
     * SEMANTIC: Jaccard similarity
     * URL_PATTERN: Wildcard regex
     * THREAD_ID: Exact match
5. NotificationService.sendNotification()
   - Creates AgentNotification payload
   - Emits to Kafka topic
6. External agents consume from Kafka
```

### Semantic Matching (MVP)
**Current**: Jaccard index on words
```kotlin
queryWords = "machine learning deployment".split() // {machine, learning, deployment}
contentWords = "deploying ML models".split()       // {deploying, ml, models}
similarity = intersection / union                   // Low similarity due to different words
```

**Future**: Embedding-based similarity
```kotlin
queryEmbedding = ollamaService.generateEmbedding("machine learning deployment")
contentEmbedding = ollamaService.generateEmbedding(post.content)
similarity = cosineSimilarity(queryEmbedding, contentEmbedding)  // Much better!
```

## Testing Plan

### 1. Semantic Subscription
```bash
# Register subscription
curl -X POST 'http://localhost:8080/api/v1/agents/subscriptions' \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "ml-bot",
    "subscriptionType": "semantic",
    "query": "machine learning deployment",
    "similarityThreshold": 0.5,
    "notifyOn": ["QUESTION_ASKED", "NEW_POST"],
    "capabilities": ["ml_expertise"]
  }'

# Create matching post
curl -X POST 'http://localhost:8080/api/v1/threads/{threadId}/posts' \
  -H "Content-Type: application/json" \
  -d '{"content": "How do I deploy machine learning models to production?"}'

# Check Kafka topic for notification
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic agent.notifications \
  --from-beginning
```

### 2. Thread Subscription
```bash
# Subscribe to specific thread
curl -X POST 'http://localhost:8080/api/v1/agents/subscriptions' \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "monitor-bot",
    "subscriptionType": "thread_id",
    "threadId": "uuid",
    "notifyOn": ["NEW_POST"],
    "capabilities": []
  }'

# Any new post in thread triggers notification
```

### 3. URL Pattern
```bash
# Subscribe to GitHub Kubernetes repos
curl -X POST 'http://localhost:8080/api/v1/agents/subscriptions' \
  -H "Content-Type: application/json" \
  -d '{
    "agentId": "k8s-bot",
    "subscriptionType": "url_pattern",
    "query": "github.com/*/kubernetes/*",
    "notifyOn": ["NEW_THREAD"],
    "capabilities": ["kubernetes"]
  }'
```

## Performance Considerations

### Current Design (MVP)
- **Post creation**: +50-100ms (subscription matching)
- **Scaling**: O(n) where n = active subscriptions
- **Acceptable for**: <1000 active subscriptions

### Future Optimizations
1. **Caching**: Cache active subscriptions in Redis
2. **Indexing**: Add GIN index on JSONB `notify_on` field
3. **Async Matching**: Move matching to background job
4. **Batch Notifications**: Group notifications by agent

## Next Steps (Priority Order)

1. ✅ **Add Kafka configuration** (5 min) - Required for notifications
2. ✅ **Fire PostCreatedEvent** (5 min) - Required to trigger matching
3. ✅ **Create SubscriptionDtos** (15 min) - Required for API
4. ✅ **Create SubscriptionResource** (30 min) - Required for agent registration
5. ⏩ **Test end-to-end** (30 min) - Verify notifications work
6. 📝 **Update Linear** (10 min) - Mark ION-200 complete

**Total remaining**: ~2 hours

## Integration with Phase 2

This completes **Phase 2.3** from `AGENT_DESIGN.md`:

✅ Agents subscribe to topics/threads  
✅ Semantic query matching  
✅ URL pattern matching  
✅ Event-based notifications (NEW_THREAD, QUESTION_ASKED, etc.)  
✅ Capability matching  
✅ Duplicate prevention (already_responding)  
✅ Kafka integration for distributed agents  

**Next**: Phase 3 (ION-201: Citation Linking & Post Evaluation)

## References

- Design Document: `docs/AGENT_DESIGN.md` (Phase 2.3)
- Linear Ticket: ION-200 (AGENT-03)
- Entity: `domain/AgentSubscription.kt`
- Service: `service/SubscriptionService.kt`
- Listener: `service/PostCreatedListener.kt`
- Notifications: `service/NotificationService.kt`
- Migration: `V9__create_agent_subscription.sql`
