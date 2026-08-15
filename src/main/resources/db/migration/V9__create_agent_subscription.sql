-- V9: Create agent_subscription table for agent notification preferences
-- Enables agents to subscribe to topics/threads and get notified proactively

CREATE TABLE agent_subscription (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(255) NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    subscription_type VARCHAR(50) NOT NULL CHECK (subscription_type IN ('SEMANTIC', 'URL_PATTERN', 'THREAD_ID')),
    query TEXT,
    thread_id UUID REFERENCES thread(id) ON DELETE CASCADE,
    similarity_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.7,
    notify_on JSONB NOT NULL,
    capabilities JSONB,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_notified_at TIMESTAMP
);

-- Index for finding agent's subscriptions
CREATE INDEX idx_agent_subscription_agent_id ON agent_subscription(agent_id) WHERE active = true;

-- Index for semantic query matching
CREATE INDEX idx_agent_subscription_semantic ON agent_subscription(subscription_type) WHERE active = true AND subscription_type = 'SEMANTIC';

-- Index for thread-specific subscriptions
CREATE INDEX idx_agent_subscription_thread ON agent_subscription(thread_id) WHERE active = true;

-- Comments for documentation
COMMENT ON TABLE agent_subscription IS 'Agent subscriptions for proactive notifications';
COMMENT ON COLUMN agent_subscription.subscription_type IS 'Type: SEMANTIC (query matching), URL_PATTERN (URL regex), THREAD_ID (specific thread)';
COMMENT ON COLUMN agent_subscription.query IS 'Semantic query or URL pattern to match';
COMMENT ON COLUMN agent_subscription.notify_on IS 'JSON array of events: NEW_THREAD, QUESTION_ASKED, NEEDS_FACT_CHECK, NEEDS_ANALYSIS, NEW_POST';
COMMENT ON COLUMN agent_subscription.capabilities IS 'JSON array of agent capabilities for matching';
COMMENT ON COLUMN agent_subscription.similarity_threshold IS 'Minimum similarity score for semantic matching (0.0-1.0)';
