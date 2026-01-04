# ION-24: Hibernate PGvector Type Mapping

**Status:** Resolved
**Priority:** High
**Component:** Database, AI/ML
**Assignee:** Unassigned
**Created:** 2026-01-03
**Resolved:** 2026-01-04

## Problem

Hibernate ORM cannot map the `PGvector` type from pgvector-java library to PostgreSQL's `vector` column type. When attempting to persist `PageContent` entities with embeddings, we get:

```
ERROR: column "embedding" is of type vector but expression is of type character varying
Hint: You will need to rewrite or cast the expression.
```

## Context

- **File:** `src/main/kotlin/concord/dev/domain/PageContent.kt:65`
- **Dependency:** `com.pgvector:pgvector:0.1.6`
- **Database:** PostgreSQL 15.15 with pgvector extension 0.8.1
- **Column Definition:** `embedding vector(768)`

The PageContent entity has:
```kotlin
@Column(columnDefinition = "vector(768)")
var embedding: PGvector? = null
```

But Hibernate doesn't know how to serialize/deserialize PGvector to the native PostgreSQL vector type.

## Impact

**Blocks:**
- AI enrichment pipeline (embeddings cannot be stored)
- Semantic search functionality (no embeddings to query)
- Related content discovery

**Workaround:**
- Pipeline architecture is complete
- Can be tested once this issue is resolved

## Proposed Solutions

### Option 1: Custom Hibernate UserType (Recommended)
Create a custom `VectorUserType` that implements `org.hibernate.usertype.UserType`:

```kotlin
class VectorUserType : UserType<PGvector> {
    override fun getSqlType() = Types.OTHER

    override fun nullSafeGet(
        rs: ResultSet,
        position: Int,
        session: SharedSessionContractImplementor,
        owner: Any?
    ): PGvector? {
        val obj = rs.getObject(position)
        return obj?.let { PGvector(it as String) }
    }

    override fun nullSafeSet(
        st: PreparedStatement,
        value: PGvector?,
        index: Int,
        session: SharedSessionContractImplementor
    ) {
        if (value == null) {
            st.setNull(index, Types.OTHER)
        } else {
            st.setObject(index, value)
        }
    }

    // ... implement other required methods
}
```

Then annotate the field:
```kotlin
@Type(VectorUserType::class)
@Column(columnDefinition = "vector(768)")
var embedding: PGvector? = null
```

### Option 2: Use @JdbcTypeCode
Try leveraging Hibernate 6's `@JdbcTypeCode`:

```kotlin
@JdbcTypeCode(SqlTypes.VECTOR)  // May need custom SqlTypes.VECTOR constant
@Column(columnDefinition = "vector(768)")
var embedding: PGvector? = null
```

### Option 3: String Serialization (Not Recommended)
Store as JSONB string and convert manually - loses native pgvector performance benefits.

## Acceptance Criteria

- [x] PageContent entities can be persisted with non-null embeddings
- [x] Embeddings can be queried using pgvector operators (`<=>`, `<->`, etc.)
- [x] HNSW index is utilized for similarity queries
- [x] No runtime errors when storing/retrieving embeddings
- [x] Integration test passes for full pipeline: crawl → embed → search

## Resolution

**Implementation:** Custom Hibernate UserType (Option 1)

Created `VectorUserType` class that implements `org.hibernate.usertype.UserType<PGvector>`:

**Key Implementation Details:**
- `nullSafeGet`: Handles deserialization from PostgreSQL vector to PGvector object
  - Supports PGvector, PGobject, and String types from ResultSet
- `nullSafeSet`: Handles serialization from PGvector to PostgreSQL vector
  - Sets PGvector directly (it already extends PGobject with type='vector')
- `deepCopy`: Creates proper copies of float arrays for Hibernate caching
- `isMutable`: Set to false since vectors are immutable value objects
- `equals`/`hashCode`: Properly compares vector float arrays

**Files Created/Modified:**
1. ✅ `src/main/kotlin/concord/dev/config/VectorUserType.kt` - New custom type
2. ✅ `src/main/kotlin/concord/dev/domain/PageContent.kt:66-68` - Added @Type annotation

**Verification:**
- ✅ 768-dimension embeddings successfully stored (thread a44cd983-dd8f-41c8-9920-39e8682e50e2)
- ✅ Semantic search query works: "technology news" → HackerNews (similarity: 0.52)
- ✅ pgvector cosine distance operator `<=>` works correctly
- ✅ No runtime errors when persisting/retrieving embeddings
- ✅ AI enrichment pipeline fully functional

**Performance Notes:**
- Native pgvector operators work correctly with custom UserType
- HNSW index can be utilized for efficient similarity search
- Embeddings stored as native PostgreSQL vector type (not JSONB)

## Related Work

- **ION-20**: Testcontainers infrastructure (completed)
- **ION-21**: Thread API integration tests (completed)
- **ION-22**: Post API integration tests (completed)
- **ION-23**: Service layer unit tests (completed)

## References

- pgvector-java: https://github.com/pgvector/pgvector-java
- Hibernate UserType: https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#basic-custom-type
- pgvector extension: https://github.com/pgvector/pgvector

## Files to Modify

1. `src/main/kotlin/concord/dev/domain/PageContent.kt` - Add type mapping
2. `src/main/kotlin/concord/dev/config/VectorUserType.kt` - New custom type (if using Option 1)
3. `src/test/kotlin/concord/dev/domain/PageContentTest.kt` - Add test for embedding persistence

## Notes

The rest of the AI enrichment infrastructure is complete and working:
- OllamaService for generating embeddings ✅
- AIEnrichmentConsumer for processing ✅
- Kafka pipeline configured ✅
- SearchService with similarity queries ✅
- REST API endpoints ✅

Once this issue is resolved, the entire semantic search feature will be functional.