CREATE TABLE ai_interaction_traces (
    id UUID PRIMARY KEY,
    ai_task_id UUID NOT NULL REFERENCES ai_tasks(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    correlation_id UUID NOT NULL,
    attempt INTEGER NOT NULL CHECK (attempt >= 1),
    interaction_type VARCHAR(40) NOT NULL,
    trace_level VARCHAR(20) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model_identifier VARCHAR(255) NOT NULL,
    intent VARCHAR(80) NOT NULL,
    intent_version VARCHAR(20) NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    validation_status VARCHAR(40) NOT NULL,
    failure_category VARCHAR(80),
    retry_reason TEXT,
    selected_knowledge_fingerprint VARCHAR(64) NOT NULL,
    prompt_fingerprint VARCHAR(64) NOT NULL,
    selected_fact_count INTEGER NOT NULL CHECK (selected_fact_count >= 0),
    selected_observation_count INTEGER NOT NULL CHECK (selected_observation_count >= 0),
    selected_insight_count INTEGER NOT NULL CHECK (selected_insight_count >= 0),
    selected_engineering_event_count INTEGER NOT NULL CHECK (selected_engineering_event_count >= 0),
    grounding_fingerprint VARCHAR(64) NOT NULL,
    input_tokens INTEGER CHECK (input_tokens IS NULL OR input_tokens >= 0),
    output_tokens INTEGER CHECK (output_tokens IS NULL OR output_tokens >= 0),
    total_tokens INTEGER CHECK (total_tokens IS NULL OR total_tokens >= 0),
    trace_id VARCHAR(100),
    span_id VARCHAR(100),
    system_prompt TEXT,
    user_prompt TEXT,
    raw_model_response TEXT,
    parsed_model_response JSONB,
    validation_diagnostics TEXT,
    expires_at TIMESTAMPTZ
);

CREATE INDEX idx_ai_interaction_traces_task_attempt
    ON ai_interaction_traces(ai_task_id, attempt, id);

CREATE INDEX idx_ai_interaction_traces_analysis_attempt
    ON ai_interaction_traces(analysis_id, attempt, id);
