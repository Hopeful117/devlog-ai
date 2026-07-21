ALTER TABLE validatable_proposals
    ADD COLUMN ai_task_id UUID,
    ADD COLUMN source_index INTEGER,
    ADD COLUMN confidence NUMERIC(5, 4),
    ADD COLUMN supporting_fact_ids JSONB,
    ADD COLUMN supporting_observation_ids JSONB,
    ADD COLUMN evidence_references JSONB;

ALTER TABLE validatable_proposals
    ADD CONSTRAINT fk_validatable_proposal_ai_task
        FOREIGN KEY (ai_task_id) REFERENCES ai_tasks(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX uk_validatable_proposal_ai_task_source_index
    ON validatable_proposals(ai_task_id, source_index)
    WHERE ai_task_id IS NOT NULL;

CREATE INDEX idx_validatable_proposal_ai_task_id
    ON validatable_proposals(ai_task_id);
