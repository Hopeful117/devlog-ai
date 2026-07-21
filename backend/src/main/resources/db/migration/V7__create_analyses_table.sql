CREATE TABLE analyses (
                          id UUID PRIMARY KEY,
                          project_id UUID NOT NULL,

                          type VARCHAR(50) NOT NULL,
                          status VARCHAR(50) NOT NULL,

                          started_at TIMESTAMP WITH TIME ZONE NOT NULL,
                          completed_at TIMESTAMP WITH TIME ZONE,

                          created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                          CONSTRAINT fk_analyses_project
                              FOREIGN KEY (project_id)
                                  REFERENCES projects(id)
                                  ON DELETE CASCADE
);

CREATE INDEX idx_analyses_project_id
    ON analyses(project_id);