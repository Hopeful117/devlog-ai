ALTER TABLE oauth_connection_intents
    ADD COLUMN authentication_method VARCHAR(32) NOT NULL DEFAULT 'OAUTH';
