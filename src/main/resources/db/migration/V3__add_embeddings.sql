-- Enable pgvector extension for semantic search
CREATE EXTENSION IF NOT EXISTS vector;

-- Add embedding column to page_content
-- nomic-embed-text produces 768-dimensional embeddings
ALTER TABLE page_content
ADD COLUMN embedding vector(768);

-- Create index for fast similarity search (HNSW)
-- This uses Hierarchical Navigable Small World algorithm for approximate nearest neighbor search
-- m = 16 (connections per layer), ef_construction = 64 (search quality during build)
CREATE INDEX page_content_embedding_idx
ON page_content
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- Add AI enrichment fields to internal_notes
-- This is stored as JSONB for flexibility
COMMENT ON COLUMN page_content.internal_notes IS
'AI-generated metadata: { "summary": "...", "topics": [...], "sentiment": 0.7, "enriched_at": "..." }';