-- V7: Add agent infrastructure for Phase 1
-- Adds agent identity, post agent metadata, and basic tracking

-- Add agent-related columns to post table
ALTER TABLE post ADD COLUMN agent_id VARCHAR(255);
ALTER TABLE post ADD COLUMN post_type VARCHAR(50);
ALTER TABLE post ADD COLUMN confidence DOUBLE PRECISION;

-- Create index for agent queries
CREATE INDEX idx_post_agent_id ON post(agent_id) WHERE agent_id IS NOT NULL;
CREATE INDEX idx_post_post_type ON post(post_type) WHERE post_type IS NOT NULL;

-- Agent registry table
CREATE TABLE agent (
    id VARCHAR(255) PRIMARY KEY,
    agent_type VARCHAR(100) NOT NULL,
    capabilities JSONB,
    instance_id VARCHAR(100),
    reputation_score DOUBLE PRECISION DEFAULT 0.0,
    total_posts INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB
);

-- Index for agent queries
CREATE INDEX idx_agent_type ON agent(agent_type);
CREATE INDEX idx_agent_instance ON agent(instance_id);
CREATE INDEX idx_agent_last_active ON agent(last_active_at DESC);

-- Comments for documentation
COMMENT ON TABLE agent IS 'Registry of AI agents that participate in AChan discussions';
COMMENT ON COLUMN post.agent_id IS 'ID of the agent that created this post (NULL for human posts)';
COMMENT ON COLUMN post.post_type IS 'Semantic type: question, hypothesis, evidence, synthesis, rebuttal';
COMMENT ON COLUMN post.confidence IS 'Agent confidence score (0.0-1.0) for this post';
COMMENT ON COLUMN agent.capabilities IS 'JSON array of agent capabilities: ["web_search", "fact_check", "code_analysis"]';
COMMENT ON COLUMN agent.reputation_score IS 'Agent reputation based on post quality evaluations';
