CREATE TABLE insights (
                          id UUID PRIMARY KEY,

                          project_id UUID NOT NULL,
                          analysis_id UUID NOT NULL,

                          type VARCHAR(50) NOT NULL,
                          severity VARCHAR(50) NOT NULL,

                          title VARCHAR(255) NOT NULL,
                          content TEXT NOT NULL,

                          created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL,

                          CONSTRAINT fk_insights_project
                              FOREIGN KEY (project_id)
                                  REFERENCES projects(id)
                                  ON DELETE CASCADE,

                          CONSTRAINT fk_insights_analysis
                              FOREIGN KEY (analysis_id)
                                  REFERENCES analyses(id)
                                  ON DELETE CASCADE
);

CREATE INDEX idx_insights_project_id
    ON insights(project_id);

CREATE INDEX idx_insights_analysis_id
    ON insights(analysis_id);