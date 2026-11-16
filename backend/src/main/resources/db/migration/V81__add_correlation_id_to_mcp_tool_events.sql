ALTER TABLE mcp_tool_events ADD COLUMN correlation_id UUID NULL;

CREATE INDEX ix_mcp_tool_events_correlation_id
    ON mcp_tool_events (correlation_id) WHERE correlation_id IS NOT NULL;
