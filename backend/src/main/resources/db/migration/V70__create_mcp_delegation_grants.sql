CREATE TABLE mcp_delegation_grants (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    technical_actor_id VARCHAR(255) NOT NULL,
    human_principal_id VARCHAR(255) NOT NULL,
    audience VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    scopes VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_mcp_delegation_grant_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

CREATE INDEX ix_mcp_delegation_grants_human_project
    ON mcp_delegation_grants (human_principal_id, project_id);
