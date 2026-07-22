ALTER TABLE insights ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE;

-- Insights are immutable, so the creation timestamp is the only truthful value for legacy rows.
UPDATE insights SET updated_at = created_at;

ALTER TABLE insights ALTER COLUMN updated_at SET NOT NULL;
