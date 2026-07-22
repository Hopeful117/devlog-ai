ALTER TABLE ai_tasks
    ADD CONSTRAINT ck_ai_task_prompt_execution_identity
        CHECK (
            (prompt_version IS NULL
                AND provider IS NULL
                AND model_identifier IS NULL
                AND prompt_content_digest IS NULL
                AND context_digest IS NULL)
            OR
            (prompt_version IS NOT NULL
                AND provider IS NOT NULL
                AND model_identifier IS NOT NULL
                AND prompt_content_digest ~ '^[0-9a-f]{64}$'
                AND context_digest ~ '^[0-9a-f]{64}$')
        );
