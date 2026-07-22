ALTER TABLE analyses
    ADD COLUMN intent_id VARCHAR(80),
    ADD COLUMN intent_version VARCHAR(20);

ALTER TABLE ai_tasks
    ADD COLUMN intent_id VARCHAR(80),
    ADD COLUMN intent_version VARCHAR(20),
    ADD COLUMN intent_snapshot JSONB;

CREATE INDEX idx_analyses_intent ON analyses(intent_id, intent_version);
CREATE INDEX idx_ai_tasks_intent ON ai_tasks(intent_id, intent_version);
