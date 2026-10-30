ALTER TABLE ai_credential_associations
    ALTER COLUMN chatgpt_subject DROP NOT NULL,
    ADD COLUMN authentication_method VARCHAR(32) NOT NULL DEFAULT 'OAUTH';
