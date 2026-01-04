-- Page content table for storing crawled URL data
CREATE TABLE page_content (
    id BIGSERIAL PRIMARY KEY,
    thread_id UUID NOT NULL UNIQUE REFERENCES thread(id) ON DELETE CASCADE,
    url TEXT NOT NULL,

    -- Extracted content fields
    title TEXT,
    description TEXT,
    article_text TEXT,
    raw_html TEXT,

    -- HTTP metadata
    content_type VARCHAR(100),
    status_code INTEGER NOT NULL,

    -- Timing and versioning
    fetched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    content_hash TEXT,

    -- Flexible structured data (OpenGraph, images, etc.)
    metadata JSONB,

    -- Internal notes not shown to users (for AI pipeline, moderation, etc.)
    internal_notes JSONB
);

-- Indexes
CREATE INDEX idx_page_content_thread_id ON page_content(thread_id);
CREATE INDEX idx_page_content_url ON page_content(url);
CREATE INDEX idx_page_content_fetched_at ON page_content(fetched_at DESC);

-- Full-text search on article text
CREATE INDEX idx_page_content_article_text ON page_content USING GIN(to_tsvector('english', article_text));