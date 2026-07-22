ALTER TABLE observations
    ADD COLUMN rule_id VARCHAR(100),
    ADD COLUMN rule_version VARCHAR(50);

UPDATE observations
SET rule_id = 'LEGACY_OBSERVATION', rule_version = '1'
WHERE rule_id IS NULL;

ALTER TABLE observations
    ALTER COLUMN rule_id SET NOT NULL,
    ALTER COLUMN rule_version SET NOT NULL;

CREATE INDEX idx_observations_rule ON observations(rule_id, rule_version);
