-- Failed event tracking for observability and retry management
CREATE TABLE failed_event (
    id BIGSERIAL PRIMARY KEY,

    -- Event identification
    event_type VARCHAR(100) NOT NULL,  -- 'URL_CRAWL', 'AI_ENRICHMENT', etc.
    event_payload TEXT NOT NULL,       -- Original message that failed

    -- Error details
    error_message TEXT NOT NULL,
    error_stacktrace TEXT,
    error_category VARCHAR(50),        -- 'TRANSIENT', 'PERMANENT', 'TIMEOUT', etc.

    -- Retry tracking
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP,

    -- Metadata
    thread_id UUID,                    -- Associated thread if applicable
    source_consumer VARCHAR(100),      -- Which consumer failed
    kafka_topic VARCHAR(100),
    kafka_partition INTEGER,
    kafka_offset BIGINT,

    -- Timestamps
    failed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,

    -- Additional context
    metadata JSONB
);

-- Indexes for common queries
CREATE INDEX idx_failed_event_type ON failed_event(event_type);
CREATE INDEX idx_failed_event_thread_id ON failed_event(thread_id) WHERE thread_id IS NOT NULL;
CREATE INDEX idx_failed_event_retry ON failed_event(next_retry_at) WHERE next_retry_at IS NOT NULL AND resolved_at IS NULL;
CREATE INDEX idx_failed_event_unresolved ON failed_event(failed_at) WHERE resolved_at IS NULL;

-- Comment for documentation
COMMENT ON TABLE failed_event IS 'Tracks failed message processing for DLQ, retry, and observability';
COMMENT ON COLUMN failed_event.error_category IS 'TRANSIENT: can retry, PERMANENT: skip, TIMEOUT: transient, RATE_LIMIT: transient';