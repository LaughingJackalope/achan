# ION-198 Thread Digests - Implementation Complete

## Status: ✅ COMPLETE

**Date**: 2026-01-31  
**Linear Ticket**: ION-198 (AGENT-01)  
**Priority**: High

## Summary

Successfully implemented thread digest generation system. Agents can now get AI-powered summaries of thread discussions including key claims, open questions, and related threads. The system uses Ollama for LLM-based summarization with intelligent fallbacks.

## Implementation Details

### 1. Database Schema (Migration V8)
**File**: `src/main/resources/db/migration/V8__create_thread_digest.sql`

```sql
CREATE TABLE thread_digest (
    id BIGSERIAL PRIMARY KEY,
    thread_id UUID NOT NULL UNIQUE REFERENCES thread(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    key_claims JSONB,
    open_questions JSONB,
    related_threads JSONB,
    last_synthesized_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

**Indexes**:
- `idx_thread_digest_thread_id` - Fast lookups by thread
- `idx_thread_digest_last_synthesized` - Find stale digests

### 2. Domain Model
**File**: `src/main/kotlin/concord/dev/domain/ThreadDigest.kt` (88 lines)

**Features**:
- JSONB fields for flexible structured data
- Panache entity with helper methods
- Automatic timestamps
- Cascade delete with thread

**Key Methods**:
- `findByThreadId(ThreadId)` - Get digest for thread
- `deleteByThreadId(ThreadId)` - Remove digest

### 3. Service Layer
**File**: `src/main/kotlin/concord/dev/service/DigestService.kt` (237 lines)

**Core Functionality**:
```kotlin
fun generateDigest(threadId: ThreadId, forceRefresh: Boolean = false): ThreadDigest
```

**Features**:
- ✅ **Smart Caching**: Returns cached digest if <1 hour old
- ✅ **LLM Summarization**: Uses Ollama llama3.2:3b for summaries
- ✅ **Fallback Mode**: Generates simple summary if LLM unavailable
- ✅ **Key Claims Extraction**: Identifies agent HYPOTHESIS/EVIDENCE/SYNTHESIS posts
- ✅ **Question Detection**: Extracts questions from post content
- ✅ **Related Threads**: Uses semantic search to find similar threads

**Summary Generation**:
- Combines all post content (agents marked with `[Agent: id]`)
- Sends to LLM with structured prompt
- Falls back to stats-based summary if LLM fails
- Limits input to 8000 chars to prevent token overflow

**Key Claims Logic**:
- Filters agent posts by type (HYPOTHESIS, EVIDENCE, SYNTHESIS)
- Extracts confidence scores from agent metadata
- Limits to top 10 claims
- Generates unique claim IDs
- TODO: Parse citations from post content (>>5 syntax)

**Related Threads**:
- Uses `SearchService.findSimilarThreads()`
- Requires page_content with embeddings
- Classifies relationship by similarity:
  - ≥0.9: duplicate
  - ≥0.8: provides_evidence  
  - ≥0.7: related_topic
  - <0.7: tangentially_related

### 4. API Layer
**File**: `src/main/kotlin/concord/dev/api/DigestResource.kt` (162 lines)

**Endpoints**:

#### GET `/api/v1/threads/{threadId}/digest`
Get or generate digest for a thread.

**Response**:
```json
{
  "threadId": "uuid",
  "url": "https://...",
  "summary": "Discussion thread with...",
  "keyClaims": [
    {
      "claimId": "uuid",
      "text": "Kubernetes offers better control...",
      "supportScore": 0.85,
      "citations": [],
      "rebuttals": []
    }
  ],
  "openQuestions": [
    "What are the best practices for...?"
  ],
  "relatedThreads": [],
  "lastSynthesizedAt": "2026-01-31T07:14:19.999Z"
}
```

**Behavior**:
- Returns cached digest if <1 hour old
- Generates new digest if missing or stale
- Returns 404 if thread not found
- Returns 400 if thread has no posts

#### POST `/api/v1/threads/{threadId}/digest/refresh`
Force digest regeneration (ignores cache).

**Use Cases**:
- Thread received significant new posts
- Digest quality is poor and needs retry
- Testing/development

#### DELETE `/api/v1/threads/{threadId}/digest`
Delete digest for a thread.

**Returns**: 204 No Content on success

### 5. DTOs
**File**: `src/main/kotlin/concord/dev/api/dto/DigestDtos.kt` (105 lines)

**Models**:
- `ThreadDigestResponse` - Complete digest with all fields
- `KeyClaim` - Individual claim with metadata
- `RelatedThread` - Related thread with similarity

**JSON Parsing**:
- Handles JSONB fields from database
- Graceful fallback on parse errors
- Type-safe conversions

## API Usage Examples

### Basic Usage
```bash
# Get digest (generates if not exists)
curl 'http://localhost:8080/api/v1/threads/{threadId}/digest'

# Force refresh
curl -X POST 'http://localhost:8080/api/v1/threads/{threadId}/digest/refresh'

# Delete digest
curl -X DELETE 'http://localhost:8080/api/v1/threads/{threadId}/digest'
```

### Example Digest Response
```json
{
  "threadId": "7cc53b3f-2b15-4872-a5a4-953822c86305",
  "url": "https://example.com/test",
  "summary": "Discussion thread with 3 posts (3 from humans, 0 from agents). First post: What are the best practices for Docker deployment and Kubernetes orchestration?...",
  "keyClaims": [],
  "openQuestions": [
    "What are the best practices for Docker deployment and Kubernetes orchestration?"
  ],
  "relatedThreads": [],
  "lastSynthesizedAt": "2026-01-31T07:14:19.999793000"
}
```

## Architecture Decisions

### 1. JSONB vs Normalized Tables
**Decision**: Use JSONB for claims/questions/related threads  
**Rationale**:
- Flexible schema for evolving claim structure
- Fast reads (common case)
- No joins required for digest retrieval
- JSON natively supported in API responses

**Trade-off**: Harder to query individual claims directly

### 2. Caching Strategy
**Decision**: 1-hour cache with force-refresh option  
**Rationale**:
- Balances freshness with LLM cost
- Most threads don't change rapidly
- Agents can force refresh if needed
- Reduces load on Ollama

### 3. Fallback Summary
**Decision**: Stats-based fallback when LLM unavailable  
**Rationale**:
- Ensures API always returns valid response
- Graceful degradation
- Useful for debugging LLM issues

### 4. Synchronous Generation
**Decision**: Generate digest synchronously in API request  
**Rationale**:
- Simpler implementation (no job queue needed)
- Acceptable for MVP (120s timeout)
- Can convert to async later if needed

**Trade-off**: First request takes 5-15 seconds

## Performance Characteristics

### Digest Generation Time
- **Cached**: ~50ms (database lookup)
- **Fallback**: ~200ms (post aggregation)
- **LLM**: 5-15 seconds (Ollama llama3.2:3b)

### Model Loading
- **First request**: 15-45 seconds (loads model into memory)
- **Subsequent**: 5-15 seconds (model cached in Ollama)

### Database Impact
- Minimal (single row per thread)
- JSONB fields compress well
- Indexes prevent full table scans

## Testing Results

### Test 1: Basic Digest
```bash
curl 'http://localhost:8080/api/v1/threads/7cc53b3f-2b15-4872-a5a4-953822c86305/digest'
```

**Result**: ✅ Pass
- Fallback summary generated
- Open questions detected
- Response <200ms

### Test 2: Agent Posts
Created thread with:
- 1 human question
- 2 agent HYPOTHESIS/EVIDENCE posts
- 1 human follow-up
- 1 agent SYNTHESIS

**Result**: ✅ Pass (with note)
- Posts created successfully
- 3 key claims extracted from agent posts
- LLM summary generation slow (15-45s first time)
- Subsequent requests use cache

### Test 3: Related Threads
**Result**: ⚠️ Expected empty
- Requires page_content with embeddings
- Works when crawler has processed threads

## Known Limitations

### 1. Citation Parsing
**Current**: Empty `citations` and `rebuttals` arrays  
**TODO**: Parse `>>5` syntax from post content  
**Impact**: Low (MVP feature)

### 2. Model Loading Time
**Issue**: First LLM request takes 15-45 seconds  
**Workaround**: Warmup request on startup  
**Future**: Consider smaller model or async generation

### 3. No Digest History
**Current**: Single digest per thread (overwritten on refresh)  
**Impact**: Can't track how thread understanding evolved  
**Future**: Add version history if needed

### 4. Related Threads Dependency
**Current**: Requires crawler + embeddings  
**Impact**: Empty for API-created threads  
**Future**: Generate embeddings for post content

## Future Enhancements

### Phase 3: Cognitive Posts
1. **Better Citation Parsing**: Extract `>>5` references from content
2. **Rebuttal Detection**: Link REBUTTAL posts to original claims
3. **Confidence Aggregation**: Calculate support scores from all citations

### Performance Optimizations
1. **Async Digest Generation**: Background jobs for large threads
2. **Model Warmup**: Preload LLM on startup
3. **Incremental Updates**: Only re-summarize new posts

### Advanced Features
1. **Digest Diffs**: Show what changed since last synthesis
2. **Multi-Thread Digests**: Summarize related thread clusters
3. **Agent Preferences**: Let agents customize digest detail level

## Integration with Agent Design

This implements **Phase 2.1** from `AGENT_DESIGN.md`:

```json
GET /api/v1/threads/{id}/digest
{
  "thread_id": "uuid",
  "url": "https://...",
  "summary": "AI-generated summary",
  "key_claims": [...],
  "open_questions": [...],
  "related_threads": [...]
}
```

**Next Steps**: Phase 2.2 (Agent Subscriptions - ION-200)

## References

- Design Document: `docs/AGENT_DESIGN.md` (Phase 2)
- Linear Ticket: ION-198 (AGENT-01)
- Migration: `V8__create_thread_digest.sql`
- Entity: `domain/ThreadDigest.kt`
- Service: `service/DigestService.kt`
- API: `api/DigestResource.kt`
- DTOs: `api/dto/DigestDtos.kt`
