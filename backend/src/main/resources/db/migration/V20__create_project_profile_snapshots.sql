CREATE TABLE project_profile_snapshots (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    analysis_id UUID NOT NULL UNIQUE,
    profile_version VARCHAR(50) NOT NULL,
    renderer_version VARCHAR(80) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    requested_revision VARCHAR(255),
    resolved_revisions JSONB NOT NULL,
    completeness_status VARCHAR(20) NOT NULL,
    collection_complete BOOLEAN NOT NULL,
    truncated BOOLEAN NOT NULL,
    warning_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    successful_collector_count INTEGER NOT NULL,
    collectors_with_warnings_count INTEGER NOT NULL,
    failed_collector_count INTEGER NOT NULL,
    sections JSONB NOT NULL,
    deterministic_summary TEXT NOT NULL,
    source_observations JSONB NOT NULL,
    characteristic_count INTEGER NOT NULL,
    CONSTRAINT fk_profile_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_profile_analysis FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE
);

CREATE INDEX idx_profile_project_latest
    ON project_profile_snapshots(project_id, generated_at DESC, id DESC);
