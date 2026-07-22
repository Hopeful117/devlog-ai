ALTER TABLE insights
    ADD COLUMN proposal_id UUID,
    ADD COLUMN validation_id UUID;

ALTER TABLE insights
    ADD CONSTRAINT fk_insight_proposal FOREIGN KEY (proposal_id) REFERENCES validatable_proposals(id),
    ADD CONSTRAINT fk_insight_validation FOREIGN KEY (validation_id) REFERENCES validations(id),
    ADD CONSTRAINT uk_insight_proposal UNIQUE (proposal_id),
    ADD CONSTRAINT uk_insight_validation UNIQUE (validation_id);

CREATE INDEX idx_insights_type ON insights(type);
CREATE INDEX idx_insights_severity ON insights(severity);

-- Legacy insights predate ADR-029 and cannot be assigned fabricated provenance.
-- Every insight created by the application after this migration sets both links.
ALTER TABLE insights DROP COLUMN updated_at;
