CREATE TABLE ai_credential_bindings (
    id UUID PRIMARY KEY,
    ai_task_id UUID NOT NULL REFERENCES ai_tasks(id) ON DELETE CASCADE,
    principal_id VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider VARCHAR(100) NOT NULL,
    credential_handle VARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    revocation_reason VARCHAR(100),
    CONSTRAINT ck_ai_credential_binding_status
        CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED')),
    CONSTRAINT ck_ai_credential_binding_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT uk_ai_credential_binding_task_provider
        UNIQUE (ai_task_id, provider)
);

CREATE INDEX ix_ai_credential_binding_principal
    ON ai_credential_bindings (principal_id, status);

CREATE INDEX ix_ai_credential_binding_expiry
    ON ai_credential_bindings (expires_at, status);

COMMENT ON TABLE ai_credential_bindings IS
    'Non-secret Core authorization binding between a principal, AI task and provider credential.';
