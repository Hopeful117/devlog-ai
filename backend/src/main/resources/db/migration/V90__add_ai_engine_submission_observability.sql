ALTER TABLE ai_tasks
    ADD COLUMN ai_engine_accepted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN ai_engine_submission_duration_ms BIGINT,
    ADD COLUMN ai_engine_submission_error TEXT;
