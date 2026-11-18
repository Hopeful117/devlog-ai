CREATE TABLE retrieval_indexing_queue (
    id          UUID          NOT NULL DEFAULT gen_random_uuid(),
    project_id  UUID          NOT NULL,
    source_type VARCHAR(50)   NOT NULL,
    source_id   UUID          NOT NULL,
    queued_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    attempts    INT           NOT NULL DEFAULT 0,
    last_error  TEXT,

    CONSTRAINT pk_retrieval_indexing_queue PRIMARY KEY (id),
    CONSTRAINT fk_retrieval_indexing_queue_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE INDEX idx_retrieval_indexing_queue_pending
    ON retrieval_indexing_queue (queued_at)
    WHERE status = 'PENDING';
