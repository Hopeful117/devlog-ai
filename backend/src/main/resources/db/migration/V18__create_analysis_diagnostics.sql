CREATE TABLE analysis_execution_diagnostics (
    analysis_id UUID PRIMARY KEY REFERENCES analyses(id) ON DELETE CASCADE,
    source_count INTEGER NOT NULL,
    fact_count INTEGER NOT NULL,
    observation_count INTEGER NOT NULL,
    warning_count INTEGER NOT NULL,
    error_count INTEGER NOT NULL,
    collector_count INTEGER NOT NULL,
    successful_collectors INTEGER NOT NULL,
    collectors_with_warnings INTEGER NOT NULL,
    failed_collectors INTEGER NOT NULL,
    collection_complete BOOLEAN NOT NULL,
    truncated BOOLEAN NOT NULL,
    resolved_revisions JSONB NOT NULL,
    collector_versions JSONB NOT NULL,
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE collection_warnings (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analyses(id) ON DELETE CASCADE,
    source_id UUID REFERENCES sources(id) ON DELETE SET NULL,
    collector_type VARCHAR(50) NOT NULL,
    collector_version VARCHAR(100) NOT NULL,
    code VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message TEXT NOT NULL,
    evidence_reference VARCHAR(2000),
    metadata JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_collection_warning_analysis
    ON collection_warnings(analysis_id, occurred_at, id);
