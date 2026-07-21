CREATE TABLE milestones (
                            id UUID PRIMARY KEY,
                            project_id UUID NOT NULL,

                            name VARCHAR(255) NOT NULL,
                            description TEXT,

                            status VARCHAR(50) NOT NULL,

                            started_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            completed_at TIMESTAMP WITH TIME ZONE,

                            created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                            updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                            CONSTRAINT fk_milestones_project
                                FOREIGN KEY (project_id)
                                    REFERENCES projects(id)
                                    ON DELETE CASCADE
);
CREATE INDEX idx_milestones_project_id
    ON milestones(project_id);