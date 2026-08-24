package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistorySearchResult;

import java.util.UUID;

/**
 * Deterministic, read-only search over the project history already imported
 * by DevLog (commit messages and changed paths). No AI, no external index:
 * same-state queries always produce the same results.
 */
public interface ProjectHistorySearchService {

    int DEFAULT_LIMIT = 20;
    int MAX_LIMIT = 100;

    ProjectHistorySearchResult search(UUID projectId, String query, Integer limit);
}
