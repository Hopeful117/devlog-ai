CREATE TABLE validatable_proposals (
                                       id UUID PRIMARY KEY,

                                       project_id UUID NOT NULL,
                                       analysis_id UUID NOT NULL,

                                       type VARCHAR(50) NOT NULL,
                                       status VARCHAR(20) NOT NULL,

                                       payload JSONB NOT NULL,

                                       created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                                       decided_at TIMESTAMP WITH TIME ZONE,

                                       CONSTRAINT fk_validatable_proposal_project
                                           FOREIGN KEY (project_id)
                                               REFERENCES projects(id),

                                       CONSTRAINT fk_validatable_proposal_analysis
                                           FOREIGN KEY (analysis_id)
                                               REFERENCES analyses(id)
);

CREATE INDEX idx_validatable_proposal_project_id
    ON validatable_proposals(project_id);

CREATE INDEX idx_validatable_proposal_analysis_id
    ON validatable_proposals(analysis_id);

CREATE INDEX idx_validatable_proposal_status
    ON validatable_proposals(status);