ALTER TABLE ai_tasks
    ADD COLUMN credential_binding_id UUID;

CREATE INDEX ix_ai_tasks_credential_binding_id
    ON ai_tasks (credential_binding_id);
