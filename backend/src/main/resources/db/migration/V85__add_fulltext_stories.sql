ALTER TABLE engineering_stories
    ADD COLUMN search_vector tsvector
        GENERATED ALWAYS AS (to_tsvector('english', coalesce(title, ''))) STORED;

CREATE INDEX idx_engineering_stories_search_vector ON engineering_stories USING GIN (search_vector);
