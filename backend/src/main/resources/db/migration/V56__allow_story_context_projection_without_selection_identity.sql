ALTER TABLE ai_tasks
    DROP CONSTRAINT ck_ai_task_selection_identity;

ALTER TABLE ai_tasks
    ADD CONSTRAINT ck_ai_task_selection_identity
        CHECK (
            (
                task_type = 'STORY_CONTEXT_ANALYSIS'
                AND selected_knowledge_snapshot IS NOT NULL
                AND selection_version IS NULL
                AND selection_digest IS NULL
            )
            OR
            (
                selected_knowledge_snapshot IS NULL
                AND selection_version IS NULL
                AND selection_digest IS NULL
            )
            OR
            (
                selected_knowledge_snapshot IS NOT NULL
                AND selection_version IS NOT NULL
                AND selection_digest ~ '^[0-9a-f]{64}$'
            )
        );
