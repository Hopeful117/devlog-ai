UPDATE ai_tasks
SET deadline_at = COALESCE(submitted_at, started_at, created_at) + INTERVAL '5 minutes'
WHERE deadline_at IS NULL
  AND status IN ('SUBMITTED', 'PROCESSING');
