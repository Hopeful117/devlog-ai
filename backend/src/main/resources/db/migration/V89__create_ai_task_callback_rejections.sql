CREATE TABLE ai_task_callback_rejections (
    id UUID PRIMARY KEY,
    ai_task_id UUID NOT NULL REFERENCES ai_tasks(id),
    correlation_id UUID,
    result_status VARCHAR(30),
    http_status INTEGER NOT NULL,
    error_code VARCHAR(100) NOT NULL,
    current_status VARCHAR(30),
    error_message TEXT,
    interaction_trace_count INTEGER NOT NULL DEFAULT 0,
    provider_generation_count INTEGER,
    provider_elapsed_millis BIGINT,
    provider_output_tokens BIGINT,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_ai_task_callback_rejections_task_received
    ON ai_task_callback_rejections (ai_task_id, received_at DESC);

ALTER TABLE ai_tasks
    ADD COLUMN last_callback_failure_code VARCHAR(100),
    ADD COLUMN last_callback_failure_status VARCHAR(30),
    ADD COLUMN last_callback_failure_message TEXT,
    ADD COLUMN last_callback_failure_at TIMESTAMP WITH TIME ZONE;
