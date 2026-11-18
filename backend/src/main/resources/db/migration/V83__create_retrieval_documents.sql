CREATE TABLE retrieval_documents (
    id                UUID         NOT NULL DEFAULT gen_random_uuid(),
    project_id        UUID         NOT NULL,
    source_type       VARCHAR(50)  NOT NULL,
    source_id         UUID         NOT NULL,
    source_revision   VARCHAR(255),
    trust_tier        VARCHAR(50)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'CURRENT',
    title             TEXT         NOT NULL,
    content           TEXT         NOT NULL,
    content_hash      VARCHAR(64)  NOT NULL,
    embedding         vector(1536),
    embedding_model   VARCHAR(100),
    indexed_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    metadata          JSONB        NOT NULL DEFAULT '{}',

    CONSTRAINT pk_retrieval_documents PRIMARY KEY (id),
    CONSTRAINT fk_retrieval_documents_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uq_retrieval_documents_source
        UNIQUE NULLS NOT DISTINCT (project_id, source_type, source_id)
);

CREATE INDEX idx_retrieval_documents_project
    ON retrieval_documents (project_id);

CREATE INDEX idx_retrieval_documents_source_type
    ON retrieval_documents (project_id, source_type);

CREATE INDEX idx_retrieval_documents_embedding
    ON retrieval_documents USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
