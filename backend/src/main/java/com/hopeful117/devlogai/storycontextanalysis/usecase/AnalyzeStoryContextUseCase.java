package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.*;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContextFreshness;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResult;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.engineeringcontext.EngineeringContextFacade;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.story.entity.EngineeringStory;
import com.hopeful117.devlogai.story.repository.EngineeringStoryRepository;
import com.hopeful117.devlogai.storycontextanalysis.entity.StoryContextAnalysis;
import com.hopeful117.devlogai.storycontextanalysis.repository.StoryContextAnalysisRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyzeStoryContextUseCase {

    private static final String INTENT_ID = "engineering-story-context-analysis";
    private static final String INTENT_VERSION = "v1";

    private final ProjectRepository projectRepository;
    private final EngineeringStoryRepository storyRepository;
    private final EngineeringContextFacade engineeringContextFacade;
    private final IntentCatalog intentCatalog;
    private final AiTaskService aiTaskService;
    private final AIEngineClient aiEngineClient;
    private final AiTaskRepository aiTaskRepository;
    private final StoryContextAnalysisRepository storyContextAnalysisRepository;
    private final ObjectMapper objectMapper;

    public UUID execute(
            String projectSlug,
            UUID storyId,
            List<String> files,
            Map<String, Object> guidance
    ) {
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectSlug));
        EngineeringStory story = storyRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("EngineeringStory", storyId));
        if (!story.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Story does not belong to project");
        }

        var intentDef = intentCatalog.resolve(INTENT_ID, INTENT_VERSION);

        EngineeringContext engineeringContext = engineeringContextFacade.getEngineeringContext(
                projectSlug,
                INTENT_ID,
                files != null ? files : List.of(),
                storyId
        );

        Map<String, Object> selectedKnowledge = buildSelectedKnowledge(engineeringContext, story);
        String contextDigest = engineeringContext.metadata().contextDigest();

        AiTask aiTask = aiTaskService.createForStoryContextAnalysisEntity(
                project.getId(),
                AiTaskType.STORY_CONTEXT_ANALYSIS,
                INTENT_ID,
                INTENT_VERSION,
                intentDef.promptTemplate(),
                selectedKnowledge,
                contextDigest,
                buildGroundingContract(engineeringContext),
                guidance
        );

        Map<String, Object> freshnessSnapshot = captureFreshnessSnapshot(engineeringContext);
        if (freshnessSnapshot != null) {
            Map<String, Object> contextSnapshot = new LinkedHashMap<>(aiTask.getContextSnapshot());
            contextSnapshot.put("contextFreshness", freshnessSnapshot);
            aiTask.setContextSnapshot(contextSnapshot);
            aiTaskRepository.save(aiTask);
        }

        PromptRequest promptRequest = new PromptRequest(
                UUID.randomUUID(),
                aiTask.getCorrelationId(),
                project.getId(),
                aiTask.getId(),
                AiTaskType.STORY_CONTEXT_ANALYSIS,
                intentDef,
                null,
                selectedKnowledge,
                intentDef.outputSchema(),
                Map.of(
                        "projectSlug", projectSlug,
                        "storyId", storyId.toString()
                )
        );

        aiEngineClient.submit(promptRequest);

        return aiTask.getId();
    }

    private Map<String, Object> buildSelectedKnowledge(EngineeringContext context, EngineeringStory story) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", context.project());
        result.put("analysis", Map.of());
        result.put("projectProfile", context.project());
        result.put("selectedFacts", List.of());
        result.put("selectedObservations", List.of());
        result.put("diagnostics", List.of());
        result.put("selectedInsights", List.of());
        result.put("selectionMetadata", List.of());
        result.put("selectionDigest", context.metadata().contextDigest());
        result.put("repositoryContext", Map.of("evidence", context.evidence()));
        result.put("engineeringStories", List.of(Map.of(
                "id", story.getId().toString(),
                "title", story.getTitle(),
                "status", story.getStatus().name(),
                "baseCommit", story.getBaseCommit(),
                "targetCommit", story.getTargetCommit(),
                "reference", "story:" + story.getId(),
                "relatedReferences", List.of()
        )));
        return result;
    }

    private Map<String, Object> buildGroundingContract(EngineeringContext context) {
        Set<String> allowedRefs = new LinkedHashSet<>();
        for (var evidence : context.evidence()) {
            if (evidence.identifier() != null) {
                allowedRefs.add(evidence.identifier());
            }
            if (evidence.relatedReferences() != null) {
                allowedRefs.addAll(evidence.relatedReferences());
            }
        }
        return Map.of("allowedEvidenceReferences", new ArrayList<>(allowedRefs));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureFreshnessSnapshot(EngineeringContext context) {
        if (context.metadata() == null || context.metadata().freshness() == null) {
            return null;
        }
        return objectMapper.convertValue(context.metadata().freshness(), Map.class);
    }

    @Transactional
    public void handleCallback(UUID correlationId, AiTaskResultRequest request) {
        AiTask task = aiTaskRepository.findByCorrelationIdForUpdate(correlationId)
                .orElseThrow(() -> new EntityNotFoundException("AI task correlation", correlationId));

        if (task.getStatus().isTerminal()) {
            log.info("Duplicate callback for completed task correlationId={}", correlationId);
            return;
        }

        if (request.status() == AiTaskResultStatus.FAILED) {
            task.setStatus(AiTaskStatus.FAILED);
            task.setFailureCode(request.error().code());
            task.setFailureMessage(request.error().message());
            task.setCompletedAt(request.completedAt());
            aiTaskRepository.save(task);
            return;
        }

        var analysisResult = request.analysisResult();
        if (analysisResult == null) {
            throw new IllegalStateException("Story Context Analysis callback must include analysisResult");
        }

        UUID storyId = UUID.fromString(
                task.getContextSnapshot().get("storyId").toString()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> contextFreshness = task.getContextSnapshot() != null
                ? (Map<String, Object>) task.getContextSnapshot().get("contextFreshness")
                : null;

        StoryContextAnalysis analysis = StoryContextAnalysis.builder()
                .story(storyRepository.findById(storyId).orElseThrow())
                .aiTask(task)
                .analysisSnapshot(objectMapper.convertValue(analysisResult, Map.class))
                .contextDigest(request.promptExecution().contextDigest())
                .promptExecutionMetadata(objectMapper.convertValue(request.promptExecution(), Map.class))
                .contextFreshness(contextFreshness)
                .build();

        storyContextAnalysisRepository.save(analysis);

        task.setStatus(AiTaskStatus.COMPLETED);
        task.setCompletedAt(request.completedAt());
        task.setPromptVersion(request.promptExecution().promptVersion());
        task.setProvider(request.promptExecution().provider());
        task.setModelIdentifier(request.promptExecution().modelIdentifier());
        task.setPromptContentDigest(request.promptExecution().promptContentDigest());
        task.setContextDigest(request.promptExecution().contextDigest());
        aiTaskRepository.save(task);
    }
}