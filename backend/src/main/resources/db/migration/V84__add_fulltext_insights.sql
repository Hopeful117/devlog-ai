ALTER TABLE insights
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(content, ''))) STORED;

CREATE INDEX idx_insights_search_vector ON insights USING GIN (search_vector);
