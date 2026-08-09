package com.hopeful117.devlogai.engineeringevent.execution;

import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.engineeringevent.GitCommitIdentity;
import com.hopeful117.devlogai.history.context.CommitDiffAnalysisContext;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
class EngineeringEventExecutionPreparationService {
    static final String INTENT_KEY = "analyze-engineering-event-v1";
    private final ProjectRepository projects;
    private final SourceRepository sources;
    private final WorkspaceManager workspaces;
    private final ProjectHistoryService history;
    private final IntentCatalog intents;

    PreparedEngineeringEventExecution prepare(UUID projectId, EngineeringEventExecutionRequest request) {
        if (!projects.existsById(projectId)) throw new EntityNotFoundException("Project", projectId);
        Source source = sources.findByIdAndProject_IdAndActiveTrue(request.sourceId(), projectId)
                .orElseThrow(() -> new EntityNotFoundException("Active project Source", request.sourceId()));
        if (source.getType() != SourceType.GIT_REPOSITORY)
            throw new IllegalArgumentException("Engineering Event analysis requires an active Git Source");
        String target = GitCommitIdentity.normalize(request.targetCommit())
                .orElseThrow(() -> new IllegalArgumentException("targetCommit must be a complete Git object ID"));
        var workspace = workspaces.synchronize(source, target);
        if (!target.equals(workspace.resolvedRevision()))
            throw new IllegalArgumentException("Resolved target does not match targetCommit");
        history.importHistory(source, workspace);
        CommitDiffAnalysisContext context = history.getCommitContext(source.getId(), target);
        if (context.rootCommit() || context.firstParentHash() == null)
            throw new IllegalArgumentException("Root commits are not supported by event analysis v1");
        String base = GitCommitIdentity.normalize(context.firstParentHash())
                .orElseThrow(() -> new IllegalStateException("Imported first parent is invalid"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", source.getId().toString());
        snapshot.put("name", source.getName());
        snapshot.put("type", source.getType().name());
        snapshot.put("defaultBranch", source.getDefaultBranch());
        snapshot.put("provider", source.getProvider() == null ? null : source.getProvider().name());
        return new PreparedEngineeringEventExecution(projectId, source.getId(), base, target,
                request.userGuidance(), intents.resolve(INTENT_KEY),
                Collections.unmodifiableMap(snapshot), context);
    }
}
