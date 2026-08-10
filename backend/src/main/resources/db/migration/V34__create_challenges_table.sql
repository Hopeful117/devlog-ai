CREATE TABLE challenges (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    impact TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_challenge_project
        FOREIGN KEY (project_id)
            REFERENCES projects(id) ON DELETE CASCADE,

    CONSTRAINT ck_challenge_status
        CHECK (status IN ('OPEN', 'RESOLVED', 'ACCEPTED', 'MITIGATED'))
);

CREATE INDEX idx_challenges_project_id
    ON challenges(project_id);
