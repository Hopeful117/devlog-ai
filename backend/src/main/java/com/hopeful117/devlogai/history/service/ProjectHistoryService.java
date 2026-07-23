package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.dto.ProjectCommitResponse;

import java.util.List;
import java.util.UUID;

public interface ProjectHistoryService {
    HistoryImportResult importHistory(UUID repositoryId, String targetRevision);

    List<ProjectCommitResponse> getProjectHistory(UUID projectId);

    CommitDiffAnalysisContext getCommitContext(UUID repositoryId, String commitHash);
}
