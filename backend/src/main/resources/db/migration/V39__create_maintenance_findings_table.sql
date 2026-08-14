CREATE TABLE maintenance_findings
(
    id                    UUID PRIMARY KEY,
    project_id            UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    context_surface       VARCHAR(50)  NOT NULL,
    issue_type            VARCHAR(80)  NOT NULL,
    severity              VARCHAR(20)  NOT NULL,
    status                VARCHAR(20)  NOT NULL,
    suggested_action      VARCHAR(20)  NOT NULL,
    human_review_required BOOLEAN      NOT NULL,
    summary               VARCHAR(255) NOT NULL,
    details               TEXT,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maintenance_findings_project_created
    ON maintenance_findings (project_id, created_at DESC, id DESC);

CREATE INDEX idx_maintenance_findings_project_status_created
    ON maintenance_findings (project_id, status, created_at DESC, id DESC);
