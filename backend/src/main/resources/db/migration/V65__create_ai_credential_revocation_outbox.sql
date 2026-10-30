CREATE TABLE ai_credential_revocation_outbox (
    id UUID PRIMARY KEY,
    credential_handle VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(500)
);

CREATE INDEX idx_ai_credential_revocation_outbox_created_at
    ON ai_credential_revocation_outbox (created_at);

CREATE UNIQUE INDEX uk_ai_credential_revocation_outbox_handle_provider
    ON ai_credential_revocation_outbox (credential_handle, provider);
