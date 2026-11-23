ALTER TABLE live_evaluation_authorizations
    ADD COLUMN consumption_key_hash VARCHAR(64),
    ADD COLUMN consumer_id VARCHAR(255),
    ADD COLUMN consumed_at TIMESTAMP WITH TIME ZONE;
