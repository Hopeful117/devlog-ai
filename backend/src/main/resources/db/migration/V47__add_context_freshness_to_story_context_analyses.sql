ALTER TABLE story_context_analyses
    ADD COLUMN context_freshness JSONB;

COMMENT ON COLUMN story_context_analyses.context_freshness IS
    'Authoritative deterministic freshness snapshot captured at context construction time. '
    'NULL for historical analyses created before Story 0114.';
