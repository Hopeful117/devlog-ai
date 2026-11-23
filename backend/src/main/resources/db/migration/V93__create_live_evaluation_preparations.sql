CREATE TABLE live_evaluation_preparations (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id),
    principal_id VARCHAR(255) NOT NULL,
    idempotency_key_hash VARCHAR(64) NOT NULL,
    idempotency_scope_hash VARCHAR(64) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    plan_digest VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    plan_snapshot JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_live_evaluation_preparation_idempotency
        UNIQUE (idempotency_key_hash, idempotency_scope_hash)
);

CREATE INDEX idx_live_evaluation_preparations_project
    ON live_evaluation_preparations (project_id, created_at DESC);
