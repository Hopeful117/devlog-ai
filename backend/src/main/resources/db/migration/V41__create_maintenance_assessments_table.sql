CREATE TABLE maintenance_assessments
(
    id                       UUID PRIMARY KEY,
    finding_id               UUID         NOT NULL REFERENCES maintenance_findings (id) ON DELETE CASCADE,
    project_id               UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    confidence_level         VARCHAR(20)  NOT NULL,
    semantic_classification  VARCHAR(40)  NOT NULL,
    recommended_action       VARCHAR(20)  NOT NULL,
    rationale                TEXT         NOT NULL,
    supporting_signals       TEXT,
    created_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_maintenance_assessments_project_created
    ON maintenance_assessments (project_id, created_at DESC, id DESC);

CREATE INDEX idx_maintenance_assessments_finding_created
    ON maintenance_assessments (finding_id, created_at DESC, id DESC);
