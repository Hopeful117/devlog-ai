CREATE TABLE oauth_connection_intents (
    id UUID PRIMARY KEY,
    principal_id VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_oauth_connection_intent_expiry
    ON oauth_connection_intents (expires_at, consumed_at);
