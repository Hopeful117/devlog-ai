CREATE TABLE live_evaluation_authorizations (
    id UUID PRIMARY KEY,
    preparation_id UUID NOT NULL REFERENCES live_evaluation_preparations(id),
    requester_id VARCHAR(255) NOT NULL,
    approver_id VARCHAR(255),
    idempotency_key_hash VARCHAR(64) NOT NULL,
    plan_digest VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    artifact_snapshot JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_live_evaluation_authorization_idempotency
        UNIQUE (preparation_id, idempotency_key_hash)
);

CREATE INDEX idx_live_evaluation_authorizations_preparation
    ON live_evaluation_authorizations (preparation_id, created_at DESC);
