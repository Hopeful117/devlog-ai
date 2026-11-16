CREATE INDEX IF NOT EXISTS idx_mcp_tool_events_project_session
    ON mcp_tool_events (project_id, agent_session_id)
    WHERE agent_session_id IS NOT NULL;
