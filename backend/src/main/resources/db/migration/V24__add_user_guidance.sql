ALTER TABLE analyses ADD COLUMN user_guidance JSONB;
ALTER TABLE ai_tasks ADD COLUMN user_guidance_snapshot JSONB;
