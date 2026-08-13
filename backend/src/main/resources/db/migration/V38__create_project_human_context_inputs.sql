CREATE TABLE project_human_context_inputs
(
    id               UUID PRIMARY KEY,
    project_id       UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    title            VARCHAR(255) NOT NULL,
    content_markdown TEXT         NOT NULL,
    input_type       VARCHAR(50)  NOT NULL,
    status           VARCHAR(50)  NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_human_context_inputs_project_updated
    ON project_human_context_inputs (project_id, updated_at DESC, id DESC);

CREATE INDEX idx_project_human_context_inputs_project_status_updated
    ON project_human_context_inputs (project_id, status, updated_at DESC, id DESC);
