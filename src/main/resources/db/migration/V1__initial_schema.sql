-- Thread table
CREATE TABLE thread (
    id UUID PRIMARY KEY,
    url TEXT NOT NULL UNIQUE,
    slug TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    post_count INTEGER NOT NULL DEFAULT 0,
    metadata JSONB,
    crawl_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (crawl_status IN ('PENDING', 'COMPLETE', 'FAILED', 'BLOCKED'))
);

CREATE INDEX idx_thread_url ON thread(url);
CREATE INDEX idx_thread_updated_at ON thread(updated_at DESC);

-- Post table
CREATE TABLE post (
    id BIGSERIAL PRIMARY KEY,
    thread_id UUID NOT NULL REFERENCES thread(id) ON DELETE CASCADE,
    parent_post_id BIGINT REFERENCES post(id) ON DELETE SET NULL,
    content TEXT NOT NULL,
    posted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    post_number INTEGER NOT NULL,
    metadata JSONB,
    UNIQUE (thread_id, post_number)
);

CREATE INDEX idx_post_thread_id ON post(thread_id);
CREATE INDEX idx_post_posted_at ON post(posted_at DESC);
CREATE INDEX idx_post_parent_id ON post(parent_post_id) WHERE parent_post_id IS NOT NULL;