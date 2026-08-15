-- V5: Add HNSW index for fast vector similarity search
--
-- HNSW (Hierarchical Navigable Small World) is the recommended index type for pgvector.
-- It provides approximate nearest neighbor search with sub-millisecond query times.
--
-- Parameters:
-- - m: Maximum number of connections per layer (default: 16, range: 2-100)
--   Higher m = better recall, more memory, slower build
-- - ef_construction: Size of dynamic candidate list during index build (default: 64)
--   Higher ef_construction = better index quality, slower build
--
-- For our use case (768-dim embeddings, moderate dataset):
-- - m=16 is a good balance
-- - ef_construction=64 is sufficient for initial deployment
--
-- Index will be built concurrently to avoid blocking writes.
-- Build time scales with dataset size (expect ~1-2 sec per 1000 vectors).

-- Create HNSW index using cosine distance operator
-- The vector_cosine_ops operator class is optimized for cosine similarity (our SearchService uses <=>)
-- Note: Not using CONCURRENTLY here to allow Flyway to run in transactional mode
-- For production datasets, create index manually with CONCURRENTLY to avoid blocking writes
CREATE INDEX IF NOT EXISTS idx_page_content_embedding_hnsw
ON page_content
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Note: At query time, you can tune search quality vs speed using:
-- SET hnsw.ef_search = 40;  (default: 40, range: 1-1000)
-- Higher ef_search = better recall, slower queries
-- This can be set per-connection or per-query as needed.