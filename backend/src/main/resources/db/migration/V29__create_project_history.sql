-- First deterministic project-history slice for ADR-035 and ADR-036.
CREATE TABLE project_commits (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    commit_hash VARCHAR(64) NOT NULL,
    author_name VARCHAR(255),
    author_email VARCHAR(320),
    authored_at TIMESTAMP WITH TIME ZONE,
    committed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    subject TEXT NOT NULL,
    full_message TEXT NOT NULL,
    root_commit BOOLEAN NOT NULL,
    merge_commit BOOLEAN NOT NULL,
    files_changed INTEGER NOT NULL,
    insertions INTEGER NOT NULL,
    deletions INTEGER NOT NULL,
    binary_files INTEGER NOT NULL,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_project_commit_source_hash UNIQUE (source_id, commit_hash)
);

CREATE TABLE commit_parents (
    id UUID PRIMARY KEY,
    project_commit_id UUID NOT NULL REFERENCES project_commits(id) ON DELETE CASCADE,
    parent_index INTEGER NOT NULL,
    parent_hash VARCHAR(64) NOT NULL,
    CONSTRAINT uk_commit_parent_index UNIQUE (project_commit_id, parent_index)
);

CREATE TABLE commit_changed_files (
    id UUID PRIMARY KEY,
    project_commit_id UUID NOT NULL REFERENCES project_commits(id) ON DELETE CASCADE,
    change_type VARCHAR(20) NOT NULL,
    old_path VARCHAR(2000),
    new_path VARCHAR(2000),
    binary_file BOOLEAN NOT NULL,
    insertions INTEGER NOT NULL,
    deletions INTEGER NOT NULL
);

CREATE INDEX idx_project_commits_project_time
    ON project_commits(project_id, committed_at, commit_hash);
CREATE INDEX idx_commit_changed_files_commit
    ON commit_changed_files(project_commit_id);
