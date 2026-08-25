package com.hopeful117.devlogai.repositoryobservation;

import com.hopeful117.devlogai.source.entity.Source;

/**
 * Observes the current immutable HEAD revision of a repository source
 * (ADR-062 change detection). Implementations must be strictly read-only:
 * no clone, no fetch, no checkout, no reset, no workspace mutation of any
 * kind. Detection produces observations only — never synchronization.
 */
public interface RepositoryRevisionProbe {

    /**
     * @return the observed HEAD revision (normalized 40/64 character hex).
     * @throws com.hopeful117.devlogai.collection.workspace.GitCommandException
     *         when the repository cannot be observed or reports no revision.
     */
    String probeHead(Source source);
}
