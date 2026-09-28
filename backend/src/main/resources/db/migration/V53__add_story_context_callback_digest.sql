ALTER TABLE ai_tasks
    ADD COLUMN terminal_callback_digest VARCHAR(64);

ALTER TABLE ai_tasks
    ADD COLUMN submission_digest VARCHAR(64),
    ADD COLUMN idempotency_key_hash VARCHAR(64);

CREATE UNIQUE INDEX uk_ai_task_sca_submission_digest
    ON ai_tasks (submission_digest)
    WHERE submission_digest IS NOT NULL;

CREATE UNIQUE INDEX uk_ai_task_sca_idempotency_key_hash
    ON ai_tasks (idempotency_key_hash)
    WHERE idempotency_key_hash IS NOT NULL;
