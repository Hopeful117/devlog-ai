ALTER TABLE insights
    ADD COLUMN rationale TEXT,
    ADD COLUMN confidence NUMERIC(5, 4),
    ADD COLUMN evidence_references jsonb,
    ADD COLUMN source_type VARCHAR(100);
