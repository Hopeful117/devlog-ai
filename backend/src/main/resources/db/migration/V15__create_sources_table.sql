CREATE TABLE sources (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    repository_url VARCHAR(2000) NOT NULL,
    default_branch VARCHAR(255),
    provider VARCHAR(50),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_synchronized_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_source_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
);

CREATE INDEX idx_sources_project_created
    ON sources(project_id, created_at DESC, id DESC);

CREATE INDEX idx_sources_active_project
    ON sources(project_id, active)
    WHERE active = TRUE;
