-- Story 0077: Persist Decision promotion provenance.
-- Adds a UNIQUE, NULLABLE proposal reference to the decisions table so that an
-- accepted ENGINEERING_DECISION proposal maps to exactly one trusted Decision.
-- Legacy Decisions created before promotion provenance existed keep NULL.
-- No backfill: associations are never invented by matching content/timestamps.

ALTER TABLE decisions
    ADD COLUMN proposal_id UUID;

ALTER TABLE decisions
    ADD CONSTRAINT uk_decision_proposal_id UNIQUE (proposal_id);

ALTER TABLE decisions
    ADD CONSTRAINT fk_decision_proposal
        FOREIGN KEY (proposal_id)
            REFERENCES validatable_proposals (id);
