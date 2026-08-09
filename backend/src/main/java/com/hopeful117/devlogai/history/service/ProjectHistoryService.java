package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.dto.ProjectCommitResponse;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.source.entity.Source;

import java.util.List;
import java.util.UUID;

public interface ProjectHistoryService {
    HistoryImportResult importHistory(UUID repositoryId, String targetRevision);

    HistoryImportResult importHistory(Source source, SynchronizedWorkspace workspace);

    List<ProjectCommitResponse> getProjectHistory(UUID projectId);

    CommitDiffAnalysisContext getCommitContext(UUID repositoryId, String commitHash);
}
