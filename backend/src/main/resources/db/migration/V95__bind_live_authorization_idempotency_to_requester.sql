ALTER TABLE live_evaluation_authorizations
    DROP CONSTRAINT uk_live_evaluation_authorization_idempotency;

ALTER TABLE live_evaluation_authorizations
    ADD CONSTRAINT uk_live_evaluation_authorization_idempotency
        UNIQUE (preparation_id, idempotency_key_hash, requester_id);
