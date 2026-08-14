CREATE TABLE maintenance_finding_actions
(
    id          UUID PRIMARY KEY,
    finding_id  UUID      NOT NULL REFERENCES maintenance_findings (id) ON DELETE CASCADE,
    action_type VARCHAR(20) NOT NULL,
    acted_by    UUID      NOT NULL,
    acted_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    comment     TEXT
);

CREATE INDEX idx_maintenance_finding_actions_finding_acted
    ON maintenance_finding_actions (finding_id, acted_at DESC, id DESC);
