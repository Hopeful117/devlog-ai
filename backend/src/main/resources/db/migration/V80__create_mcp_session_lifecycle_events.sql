CREATE TABLE mcp_session_lifecycle_events (
    id               UUID PRIMARY KEY,
    project_id       UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    agent_session_id VARCHAR(128) NOT NULL,
    event_type       VARCHAR(64)  NOT NULL,
    occurred_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    agent_provider   VARCHAR(128),
    CONSTRAINT uk_session_lifecycle_identity
        UNIQUE (project_id, agent_session_id, event_type)
);

CREATE INDEX ix_session_lifecycle_project_session
    ON mcp_session_lifecycle_events (project_id, agent_session_id);
