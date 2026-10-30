CREATE TABLE ai_credential_associations (
    id UUID PRIMARY KEY,
    principal_id VARCHAR(255) NOT NULL,
    chatgpt_subject VARCHAR(255) NOT NULL,
    credential_handle VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ai_credential_association_principal_provider UNIQUE (principal_id, provider)
);

CREATE INDEX ix_ai_credential_association_handle
    ON ai_credential_associations (credential_handle, provider);
