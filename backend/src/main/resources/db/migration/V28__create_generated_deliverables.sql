CREATE TABLE generated_deliverables (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    analysis_id UUID REFERENCES analyses(id) ON DELETE SET NULL,
    type VARCHAR(50) NOT NULL,
    audience VARCHAR(200) NOT NULL,
    style VARCHAR(100) NOT NULL,
    language VARCHAR(20) NOT NULL,
    additional_guidance VARCHAR(1000),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    prompt_version VARCHAR(100) NOT NULL,
    prompt_digest VARCHAR(64) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model_identifier VARCHAR(255) NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE generated_deliverable_insights (
    deliverable_id UUID NOT NULL REFERENCES generated_deliverables(id) ON DELETE CASCADE,
    insight_id UUID NOT NULL REFERENCES insights(id),
    PRIMARY KEY (deliverable_id, insight_id)
);

CREATE INDEX idx_generated_deliverables_project ON generated_deliverables(project_id, generated_at DESC);
CREATE INDEX idx_generated_deliverables_analysis ON generated_deliverables(analysis_id, generated_at DESC);
CREATE INDEX idx_generated_deliverable_insights_insight ON generated_deliverable_insights(insight_id);
