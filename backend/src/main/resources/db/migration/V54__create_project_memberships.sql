CREATE TABLE project_memberships (
    id UUID PRIMARY KEY,
    principal_id VARCHAR(255) NOT NULL,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL,
    CONSTRAINT uk_project_membership_principal_project UNIQUE (principal_id, project_id),
    CONSTRAINT ck_project_membership_role CHECK (role IN ('PROJECT_READER', 'PROJECT_OWNER'))
);

COMMENT ON TABLE project_memberships IS
    'Minimal Story 0155 project authorization relation; identity provider remains external';
