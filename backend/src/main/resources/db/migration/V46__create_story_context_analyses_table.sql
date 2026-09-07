CREATE TABLE story_context_analyses (
    id UUID PRIMARY KEY,
    story_id UUID NOT NULL,
    ai_task_id UUID NOT NULL,
    analysis_snapshot JSONB NOT NULL,
    context_digest VARCHAR(64) NOT NULL,
    prompt_execution_metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_story_context_analysis_story
        FOREIGN KEY (story_id)
            REFERENCES engineering_stories(id) ON DELETE CASCADE,

    CONSTRAINT fk_story_context_analysis_ai_task
        FOREIGN KEY (ai_task_id)
            REFERENCES ai_tasks(id) ON DELETE CASCADE,

    CONSTRAINT uq_story_context_analysis_ai_task
        UNIQUE (ai_task_id)
);

CREATE INDEX idx_story_context_analyses_story_id
    ON story_context_analyses(story_id);

CREATE INDEX idx_story_context_analyses_ai_task_id
    ON story_context_analyses(ai_task_id);

CREATE INDEX idx_story_context_analyses_created_at
    ON story_context_analyses(created_at);