ALTER TABLE mcp_tool_events
    ADD COLUMN agent_session_id VARCHAR(128),
    ADD COLUMN agent_provider   VARCHAR(128);
