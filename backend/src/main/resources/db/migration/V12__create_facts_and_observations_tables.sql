CREATE TABLE facts (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(255) NOT NULL,
    detected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_facts_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE
);

CREATE INDEX idx_facts_analysis_id ON facts(analysis_id);

CREATE TABLE fact_evidence_references (
    fact_id UUID NOT NULL,
    reference TEXT NOT NULL,
    CONSTRAINT fk_fact_evidence_references_fact
        FOREIGN KEY (fact_id) REFERENCES facts(id) ON DELETE CASCADE
);

CREATE TABLE observations (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_observations_analysis
        FOREIGN KEY (analysis_id) REFERENCES analyses(id) ON DELETE CASCADE
);

CREATE INDEX idx_observations_analysis_id ON observations(analysis_id);

CREATE TABLE observation_facts (
    observation_id UUID NOT NULL,
    fact_id UUID NOT NULL,
    PRIMARY KEY (observation_id, fact_id),
    CONSTRAINT fk_observation_facts_observation
        FOREIGN KEY (observation_id) REFERENCES observations(id) ON DELETE CASCADE,
    CONSTRAINT fk_observation_facts_fact
        FOREIGN KEY (fact_id) REFERENCES facts(id) ON DELETE CASCADE
);
