CREATE TABLE committed_acts (
    id UUID PRIMARY KEY,
    intent_event_id UUID NOT NULL,
    proposal_event_id UUID NOT NULL,
    object_id VARCHAR(2048) NOT NULL,
    action TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_committed_acts_intent_event_id ON committed_acts(intent_event_id);
