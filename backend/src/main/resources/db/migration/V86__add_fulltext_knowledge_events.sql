ALTER TABLE knowledge_events
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('english', coalesce(title, '') || ' ' || coalesce(description, ''))) STORED;

CREATE INDEX idx_knowledge_events_search_vector ON knowledge_events USING GIN (search_vector);
