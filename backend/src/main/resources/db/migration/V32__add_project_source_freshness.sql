CREATE TABLE project_source_freshness (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    source_id UUID NOT NULL,
    baseline_analysis_id UUID,
    status VARCHAR(30) NOT NULL,
    guidance VARCHAR(30) NOT NULL,
    requested_revision VARCHAR(300) NOT NULL,
    current_revision VARCHAR(64) NOT NULL,
    baseline_revision VARCHAR(64),
    checked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_freshness_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_freshness_source FOREIGN KEY (source_id)
        REFERENCES sources(id) ON DELETE CASCADE,
    CONSTRAINT fk_freshness_baseline_analysis FOREIGN KEY (baseline_analysis_id)
        REFERENCES analyses(id) ON DELETE SET NULL,
    CONSTRAINT uq_freshness_project_source UNIQUE (project_id, source_id),
    CONSTRAINT uq_freshness_source UNIQUE (source_id)
);

CREATE INDEX idx_freshness_project ON project_source_freshness(project_id);
