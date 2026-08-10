CREATE TABLE engineering_stories (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    story_number INTEGER NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
    story_path VARCHAR(500),
    base_commit VARCHAR(64),
    target_commit VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_engineering_story_project
        FOREIGN KEY (project_id)
            REFERENCES projects(id) ON DELETE CASCADE,

    CONSTRAINT uq_engineering_story_project_number
        UNIQUE (project_id, story_number),

    CONSTRAINT ck_engineering_story_status
        CHECK (status IN ('REGISTERED', 'IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX idx_engineering_stories_project_id
    ON engineering_stories(project_id);