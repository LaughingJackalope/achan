-- V8: Create thread_digest table for AI-generated thread summaries
-- Provides agents with quick context about thread content

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

-- Index for fast lookup by thread_id
CREATE INDEX idx_thread_digest_thread_id ON thread_digest(thread_id);

-- Index for finding stale digests that need refresh
CREATE INDEX idx_thread_digest_last_synthesized ON thread_digest(last_synthesized_at);

-- Comments for documentation
COMMENT ON TABLE thread_digest IS 'AI-generated summaries and key information about threads for agent consumption';
COMMENT ON COLUMN thread_digest.summary IS 'AI-generated summary of the thread discussion';
COMMENT ON COLUMN thread_digest.key_claims IS 'JSON array of key claims with support scores and citations';
COMMENT ON COLUMN thread_digest.open_questions IS 'JSON array of unresolved questions from the thread';
COMMENT ON COLUMN thread_digest.related_threads IS 'JSON array of related threads with similarity scores';
COMMENT ON COLUMN thread_digest.last_synthesized_at IS 'When the digest was last generated/updated';
