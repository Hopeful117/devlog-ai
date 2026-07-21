CREATE TABLE ai_tasks (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL,
    correlation_id UUID NOT NULL,
    task_type VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    context_snapshot JSONB NOT NULL,
    external_job_id VARCHAR(255),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    failure_code VARCHAR(100),
    failure_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_ai_task_correlation_id UNIQUE (correlation_id),
    CONSTRAINT fk_ai_task_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_task_analysis_id ON ai_tasks(analysis_id);
CREATE INDEX idx_ai_task_status ON ai_tasks(status);
