CREATE TABLE ai_user_configurations (
    id UUID PRIMARY KEY,
    principal_id VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    authentication_method VARCHAR(32) NOT NULL,
    selected_model VARCHAR(255) NOT NULL,
    model_selection_origin VARCHAR(16) NOT NULL,
    allowed_parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_ai_user_configuration_principal UNIQUE (principal_id),
    CONSTRAINT ck_ai_user_configuration_origin
        CHECK (model_selection_origin IN ('RECOMMENDED', 'EXPLICIT')),
    CONSTRAINT ck_ai_user_configuration_parameters_empty
        CHECK (allowed_parameters = '{}'::jsonb)
);

COMMENT ON TABLE ai_user_configurations IS
    'Non-secret global AI provider and model preference owned by an authenticated principal.';
