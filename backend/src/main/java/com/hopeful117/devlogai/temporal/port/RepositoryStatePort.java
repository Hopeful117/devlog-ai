package com.hopeful117.devlogai.temporal.port;

import com.hopeful117.devlogai.source.entity.Source;

/**
 * Read-only port for checking repository file existence at a specific git revision,
 * scoped to a Source (repository identity).
 *
 * Temporal Knowledge domain depends on this interface, NOT on GitWorkspaceManager or
 * GitCommandExecutor directly (ADR-059 §22).
 *
 * Per FINAL_SOURCE_SCOPING_CORRECTION: the port MUST include Source identity.
 * A Git revision is meaningful only inside its repository.
 */
public interface RepositoryStatePort {

    /**
     * Checks if a file exists at the given commit hash within the given Source's repository.
     *
     * @param source the Source (repository identity) to scope the check
     * @param commitHash git commit hash to check against
     * @param relativePath repository-relative file path
     * @return true if file exists at that revision for that source, false otherwise
     * @throws com.hopeful117.devlogai.collection.workspace.GitCommandException
     *         if repository state cannot be verified (workspace unavailable, etc.)
     */
    boolean isFilePresentAtRevision(Source source, String commitHash, String relativePath);
}
