ALTER TABLE facts
    ADD COLUMN fingerprint VARCHAR(64);

CREATE UNIQUE INDEX uq_facts_analysis_fingerprint
    ON facts (analysis_id, fingerprint)
    WHERE fingerprint IS NOT NULL;
