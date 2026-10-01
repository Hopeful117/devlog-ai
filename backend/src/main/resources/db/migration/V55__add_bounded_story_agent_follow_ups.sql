ALTER TABLE ai_tasks
    ADD COLUMN parent_snapshot_id UUID REFERENCES ai_tasks(id),
    ADD COLUMN follow_up_request_digest VARCHAR(64);

CREATE UNIQUE INDEX uk_ai_tasks_one_follow_up_per_snapshot
    ON ai_tasks(parent_snapshot_id)
    WHERE parent_snapshot_id IS NOT NULL;

COMMENT ON COLUMN ai_tasks.parent_snapshot_id IS
    'Initial Story Context snapshot that authorizes one bounded, non-chainable follow-up';
COMMENT ON COLUMN ai_tasks.follow_up_request_digest IS
    'Canonical digest of the bounded follow-up request used for idempotent retries';
