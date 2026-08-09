package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.analysis.workflow.AnalysisAiTaskTypeResolver;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingRequest;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ProjectUnderstandingPreparationService {
    static final String INTENT_KEY = "describe-project-v1";
    private final ProjectRepository projectRepository;
    private final SourceRepository sourceRepository;
    private final IntentCatalog intentCatalog;
    private final AnalysisAiTaskTypeResolver taskTypeResolver;
    private final WorkspaceManager workspaceManager;
    private final ProjectHistoryService historyService;

    PreparedProjectUnderstanding prepare(UUID projectId, ProjectUnderstandingRequest request) {
        if (!projectRepository.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
        Source source = sourceRepository.findByIdAndProject_IdAndActiveTrue(request.sourceId(), projectId)
                .orElseThrow(() -> new EntityNotFoundException("Active project Source", request.sourceId()));
        if (source.getType() != SourceType.GIT_REPOSITORY) {
            throw new IllegalArgumentException("Project Understanding requires an active Git Source");
        }
        IntentDefinition intent = intentCatalog.resolve(INTENT_KEY);
        taskTypeResolver.resolve(intent);
        String revision = normalize(request.targetRevision());
        SynchronizedWorkspace workspace = workspaceManager.synchronize(source, revision);
        historyService.importHistory(source, workspace);
        return new PreparedProjectUnderstanding(projectId, source.getId(), revision,
                workspace.resolvedRevision(), request.userGuidance(), intent, snapshot(source));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, Object> snapshot(Source source) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", source.getId().toString());
        values.put("name", source.getName());
        values.put("type", source.getType().name());
        values.put("repositoryUrl", source.getRepositoryUrl());
        values.put("defaultBranch", source.getDefaultBranch());
        values.put("provider", source.getProvider() == null ? null : source.getProvider().name());
        return java.util.Collections.unmodifiableMap(values);
    }
}
