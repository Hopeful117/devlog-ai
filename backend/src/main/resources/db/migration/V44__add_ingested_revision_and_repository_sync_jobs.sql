ALTER TABLE project_source_freshness
    ADD COLUMN ingested_revision VARCHAR(64);

CREATE TABLE repository_sync_jobs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    source_id UUID NOT NULL,
    from_revision VARCHAR(64),
    to_revision VARCHAR(64) NOT NULL,
    reason VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failure TEXT,
    CONSTRAINT fk_sync_job_project FOREIGN KEY (project_id)
        REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_sync_job_source FOREIGN KEY (source_id)
        REFERENCES sources(id) ON DELETE CASCADE
);

CREATE INDEX idx_sync_jobs_project ON repository_sync_jobs(project_id);
CREATE INDEX idx_sync_jobs_source_status ON repository_sync_jobs(source_id, status);
