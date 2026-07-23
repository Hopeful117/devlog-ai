package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;

import java.util.List;

/**
 * Extensible deterministic collector contract from ADR-038.
 */
public interface RepositoryContextCollector {
    String collectorId();

    String collectorVersion();

    List<RepositoryEvidence> collect(ContextRequest request);
}
