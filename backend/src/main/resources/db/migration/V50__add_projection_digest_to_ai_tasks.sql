ALTER TABLE ai_tasks
    ADD COLUMN projection_digest VARCHAR(64);

CREATE INDEX idx_ai_tasks_projection_digest ON ai_tasks(projection_digest);
