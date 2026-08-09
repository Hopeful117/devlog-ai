ALTER TABLE analyses ADD COLUMN evolution_execution_key VARCHAR(64);

CREATE UNIQUE INDEX uk_analyses_active_evolution_execution
    ON analyses(evolution_execution_key)
    WHERE evolution_execution_key IS NOT NULL AND status IN ('PENDING', 'IN_PROGRESS');

CREATE TABLE analysis_evolution_scopes (
    analysis_id UUID PRIMARY KEY REFERENCES analyses(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    context_version VARCHAR(50) NOT NULL,
    comparison_policy VARCHAR(30) NOT NULL,
    base_commit VARCHAR(64) NOT NULL,
    target_commit VARCHAR(64) NOT NULL,
    target_committed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    merge_commit BOOLEAN NOT NULL,
    CONSTRAINT ck_evolution_base_commit CHECK (base_commit ~ '^[0-9a-f]{40}([0-9a-f]{24})?$'),
    CONSTRAINT ck_evolution_target_commit CHECK (target_commit ~ '^[0-9a-f]{40}([0-9a-f]{24})?$'),
    CONSTRAINT ck_evolution_distinct_commits CHECK (base_commit <> target_commit)
);

CREATE INDEX idx_evolution_scope_project_time
    ON analysis_evolution_scopes(project_id, target_committed_at DESC, target_commit);
CREATE INDEX idx_evolution_scope_source_target
    ON analysis_evolution_scopes(source_id, target_commit);

CREATE TABLE engineering_events (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    analysis_id UUID NOT NULL REFERENCES analyses(id),
    proposal_id UUID NOT NULL UNIQUE REFERENCES validatable_proposals(id),
    validation_id UUID NOT NULL UNIQUE REFERENCES validations(id),
    source_id UUID NOT NULL REFERENCES sources(id),
    category VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    significance TEXT NOT NULL,
    base_commit VARCHAR(64) NOT NULL,
    target_commit VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_event_base_commit CHECK (base_commit ~ '^[0-9a-f]{40}([0-9a-f]{24})?$'),
    CONSTRAINT ck_event_target_commit CHECK (target_commit ~ '^[0-9a-f]{40}([0-9a-f]{24})?$'),
    CONSTRAINT ck_event_distinct_commits CHECK (base_commit <> target_commit)
);

CREATE INDEX idx_engineering_events_project_time
    ON engineering_events(project_id, occurred_at DESC, target_commit DESC, id);
