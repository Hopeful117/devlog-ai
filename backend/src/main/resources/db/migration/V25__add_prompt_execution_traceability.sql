ALTER TABLE ai_tasks
    ADD COLUMN prompt_request_id UUID,
    ADD COLUMN prompt_version VARCHAR(100),
    ADD COLUMN provider VARCHAR(100),
    ADD COLUMN model_identifier VARCHAR(255),
    ADD COLUMN prompt_content_digest VARCHAR(64),
    ADD COLUMN context_digest VARCHAR(64);

CREATE INDEX idx_ai_tasks_prompt_digest ON ai_tasks(prompt_content_digest);
