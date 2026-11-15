CREATE TABLE mcp_tool_events (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    instance_id VARCHAR(64) NOT NULL,
    operation_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(128),
    event_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    generation INT NOT NULL,
    duration_ms INT,
    reason_code VARCHAR(64),
    schema_version VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_mcp_tool_event_identity UNIQUE (instance_id, operation_id, event_type)
);

CREATE INDEX ix_mcp_tool_events_project_received
    ON mcp_tool_events (project_id, received_at DESC);
