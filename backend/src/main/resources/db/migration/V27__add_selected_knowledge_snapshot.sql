ALTER TABLE ai_tasks
    ADD COLUMN selected_knowledge_snapshot JSONB,
    ADD COLUMN selection_version VARCHAR(100),
    ADD COLUMN selection_digest VARCHAR(64);

ALTER TABLE ai_tasks
    ADD CONSTRAINT ck_ai_task_selection_identity
        CHECK (
            (selected_knowledge_snapshot IS NULL
                AND selection_version IS NULL
                AND selection_digest IS NULL)
            OR
            (selected_knowledge_snapshot IS NOT NULL
                AND selection_version IS NOT NULL
                AND selection_digest ~ '^[0-9a-f]{64}$')
        );

CREATE INDEX idx_ai_tasks_selection_digest ON ai_tasks(selection_digest);
