ALTER TABLE ai_tasks
    ADD COLUMN deadline_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN terminal_reason VARCHAR(100),
    ADD COLUMN recovery_action VARCHAR(100);

CREATE INDEX idx_ai_task_deadline_recovery
    ON ai_tasks (status, deadline_at)
    WHERE status IN ('SUBMITTED', 'PROCESSING') AND deadline_at IS NOT NULL;
