ALTER TABLE analyses
    ADD COLUMN selected_source_id UUID,
    ADD COLUMN selected_source_snapshot JSONB,
    ADD COLUMN understanding_execution_key VARCHAR(64);

ALTER TABLE analyses
    ADD CONSTRAINT fk_analyses_selected_source
        FOREIGN KEY (selected_source_id) REFERENCES sources(id) ON DELETE SET NULL;

CREATE INDEX idx_analyses_selected_source ON analyses(selected_source_id);

CREATE UNIQUE INDEX uq_analyses_active_understanding_execution
    ON analyses(understanding_execution_key)
    WHERE understanding_execution_key IS NOT NULL
      AND status IN ('PENDING', 'IN_PROGRESS');
