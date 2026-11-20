ALTER TABLE ai_tasks
    ADD COLUMN idempotency_scope_hash VARCHAR(64);

DROP INDEX IF EXISTS uk_ai_task_sca_idempotency_key_hash;

CREATE UNIQUE INDEX uk_ai_task_sca_idempotency_key_scope
    ON ai_tasks (idempotency_key_hash, idempotency_scope_hash)
    WHERE idempotency_key_hash IS NOT NULL
      AND idempotency_scope_hash IS NOT NULL;
