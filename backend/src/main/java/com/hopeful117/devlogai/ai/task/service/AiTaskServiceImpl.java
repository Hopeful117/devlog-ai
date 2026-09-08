package com.hopeful117.devlogai.ai.task.service;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.mapper.AiTaskMapper;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledgePromptProjectionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collections;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
@Transactional
public class AiTaskServiceImpl implements AiTaskService {

    private final AiTaskRepository aiTaskRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisContextService analysisContextService;
    private final AiTaskMapper aiTaskMapper;
    private final ObjectMapper objectMapper;
    private final IntentCatalog intentCatalog;
    private final SelectedKnowledgePromptProjectionService promptProjectionService;
    private final ProjectRepository projectRepository;

    @Override
    public AiTaskResponse create(CreateAiTaskRequest request) {
        Analysis analysis = findAnalysis(request.analysisId());
        AnalysisContext context = analysisContextService.build(request.analysisId());
        return create(request, context, null, analysis);
    }

    @Override
    public AiTaskResponse create(
            CreateAiTaskRequest request,
            AnalysisContext context
    ) {
        return create(request, context, null, findAnalysis(request.analysisId()));
    }

    @Override
    public AiTaskResponse create(CreateAiTaskRequest request, AnalysisContext context,
                                 SelectedKnowledge selectedKnowledge) {
        return create(request, context, selectedKnowledge, findAnalysis(request.analysisId()));
    }

    @Override
    public AiTaskResponse createForStoryContextAnalysis(
            UUID analysisId,
            AiTaskType taskType,
            String intentId,
            String intentVersion,
            String promptTemplate,
            Map<String, Object> selectedKnowledgeSnapshot,
            String contextDigest,
            Map<String, Object> groundingContract,
            Map<String, Object> userGuidance
    ) {
        Analysis analysis = findAnalysis(analysisId);
        IntentDefinition intent = intentCatalog.resolve(intentId, intentVersion);

        Map<String, Object> contextSnapshot = new LinkedHashMap<>(Map.of(
                "analysisId", analysisId.toString(),
                "intentId", intentId,
                "intentVersion", intentVersion
        ));
        if (selectedKnowledgeSnapshot != null && selectedKnowledgeSnapshot.containsKey("engineeringStories")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stories = (List<Map<String, Object>>) selectedKnowledgeSnapshot.get("engineeringStories");
            if (stories != null && !stories.isEmpty() && stories.get(0).containsKey("id")) {
                contextSnapshot.put("storyId", stories.get(0).get("id").toString());
            }
        }

        Map<String, Object> intentSnapshot = objectMapper.convertValue(intent, Map.class);

        AiTask task = new AiTask();
        task.setAnalysis(analysis);
        task.setCorrelationId(UUID.randomUUID());
        task.setStatus(AiTaskStatus.CREATED);
        task.setTaskType(taskType);
        task.setIntentId(intent.id());
        task.setIntentVersion(intent.version());
        task.setIntentSnapshot(intentSnapshot);
        task.setUserGuidanceSnapshot(userGuidance != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(userGuidance))
                : null);
        task.setPromptRequestId(task.getCorrelationId());
        task.setContextSnapshot(contextSnapshot);
        task.setSelectedKnowledgeSnapshot(selectedKnowledgeSnapshot);
        task.setSelectionVersion(null);
        task.setSelectionDigest(contextDigest);
        task.setAttemptCount(0);
        task.setExternalJobId(null);
        task.setFailureCode(null);
        task.setFailureMessage(null);
        task.setSubmittedAt(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);

        return aiTaskMapper.toResponse(aiTaskRepository.save(task));
    }

    @Override
    public AiTask createForStoryContextAnalysisEntity(
            UUID projectId,
            AiTaskType taskType,
            String intentId,
            String intentVersion,
            String promptTemplate,
            Map<String, Object> selectedKnowledgeSnapshot,
            String contextDigest,
            Map<String, Object> groundingContract,
            Map<String, Object> userGuidance
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));

        // Get or create an Analysis for the project with STORY_CONTEXT_ANALYSIS type
        List<Analysis> existing = analysisRepository.findByProjectIdAndTypeOrderByCreatedAtDesc(projectId, AnalysisType.STORY_CONTEXT_ANALYSIS);
        Analysis analysis = existing.isEmpty()
                ? analysisRepository.save(Analysis.builder()
                        .project(project)
                        .type(AnalysisType.STORY_CONTEXT_ANALYSIS)
                        .intentId("engineering-story-context-analysis")
                        .intentVersion("v1")
                        .status(AnalysisStatus.PENDING)
                        .build())
                : existing.get(0);

        IntentDefinition intent = intentCatalog.resolve(intentId, intentVersion);

        Map<String, Object> contextSnapshot = new LinkedHashMap<>(Map.of(
                "analysisId", analysis.getId().toString(),
                "intentId", intentId,
                "intentVersion", intentVersion
        ));

        // Store grounding contract in context snapshot for callback validation
        if (groundingContract != null && !groundingContract.isEmpty()) {
            contextSnapshot.put("groundingContract", groundingContract);
        }

        // Extract storyId from selected knowledge for callback
        if (selectedKnowledgeSnapshot != null && selectedKnowledgeSnapshot.containsKey("engineeringStories")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stories = (List<Map<String, Object>>) selectedKnowledgeSnapshot.get("engineeringStories");
            if (stories != null && !stories.isEmpty() && stories.get(0).containsKey("id")) {
                contextSnapshot.put("storyId", stories.get(0).get("id").toString());
            }
        }

        Map<String, Object> intentSnapshot = objectMapper.convertValue(intent, Map.class);

        AiTask task = new AiTask();
        task.setAnalysis(analysis);
        task.setCorrelationId(UUID.randomUUID());
        task.setStatus(AiTaskStatus.CREATED);
        task.setTaskType(taskType);
        task.setIntentId(intent.id());
        task.setIntentVersion(intent.version());
        task.setIntentSnapshot(intentSnapshot);
        task.setUserGuidanceSnapshot(userGuidance != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(userGuidance))
                : null);
        task.setPromptRequestId(task.getCorrelationId());
        task.setContextSnapshot(contextSnapshot);
        task.setSelectedKnowledgeSnapshot(selectedKnowledgeSnapshot);
        // Use v5 for SCA intent (Story-aware selection)
        task.setSelectionVersion("engineering-story-context-analysis".equals(intentId)
                ? "knowledge-selection-v5" : "knowledge-selection-v1");
        task.setSelectionDigest(contextDigest);
        task.setAttemptCount(0);
        task.setExternalJobId(null);
        task.setFailureCode(null);
        task.setFailureMessage(null);
        task.setSubmittedAt(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);

        return aiTaskRepository.save(task);
    }

    @Override
    public AiTaskResponse attachSelectedKnowledge(UUID id, SelectedKnowledge selectedKnowledge) {
        AiTask task = findTask(id);
        requireStatus(task, AiTaskStatus.CREATED, AiTaskStatus.CREATED);
        if (task.getSelectedKnowledgeSnapshot() != null) {
            throw new ConflictException("Selected knowledge is already attached to AI task " + id);
        }
        Map<String, Object> snapshot = promptProjectionService.toMap(selectedKnowledge);
        task.setSelectedKnowledgeSnapshot(snapshot);
        task.setSelectionVersion(selectedKnowledge.selectionMetadata().selectionVersion());
        task.setSelectionDigest(selectedKnowledge.selectionDigest());
        return saveAndMap(task);
    }

    private AiTaskResponse create(
            CreateAiTaskRequest request,
            AnalysisContext context,
            SelectedKnowledge selectedKnowledge,
            Analysis analysis
    ) {
        Map<String, Object> contextSnapshot = objectMapper.convertValue(
                context,
                Map.class
        );
        Map<String, Object> selectedKnowledgeSnapshot = selectedKnowledge == null ? null
                : promptProjectionService.toMap(selectedKnowledge);

        AiTask task = aiTaskMapper.toEntity(request);
        IntentDefinition intent = intentCatalog.resolve(analysis.getIntentId(), analysis.getIntentVersion());
        Map<String, Object> intentSnapshot = objectMapper.convertValue(intent, Map.class);
        task.setAnalysis(analysis);
        task.setCorrelationId(UUID.randomUUID());
        task.setStatus(AiTaskStatus.CREATED);
        task.setIntentId(intent.id());
        task.setIntentVersion(intent.version());
        task.setIntentSnapshot(intentSnapshot);
        task.setUserGuidanceSnapshot(analysis.getUserGuidance() == null
                ? null : Collections.unmodifiableMap(
                        new LinkedHashMap<>(analysis.getUserGuidance())));
        task.setPromptRequestId(task.getCorrelationId());
        task.setContextSnapshot(contextSnapshot);
        task.setSelectedKnowledgeSnapshot(selectedKnowledgeSnapshot);
        task.setSelectionVersion(selectedKnowledge == null ? null
                : selectedKnowledge.selectionMetadata().selectionVersion());
        task.setSelectionDigest(selectedKnowledge == null ? null
                : selectedKnowledge.selectionDigest());
        task.setAttemptCount(0);
        task.setExternalJobId(null);
        task.setFailureCode(null);
        task.setFailureMessage(null);
        task.setSubmittedAt(null);
        task.setStartedAt(null);
        task.setCompletedAt(null);

        return aiTaskMapper.toResponse(aiTaskRepository.save(task));
    }

    private Analysis findAnalysis(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Analysis", analysisId
                ));
    }

    @Override
    public AiTaskResponse getById(UUID id) {
        return aiTaskMapper.toResponse(findTask(id));
    }

    @Override
    public AiTaskResponse getByCorrelationId(UUID correlationId) {
        return aiTaskRepository.findByCorrelationId(correlationId)
                .map(aiTaskMapper::toResponse)
                .orElseThrow(() -> new EntityNotFoundException(
                        "AI task correlation", correlationId
                ));
    }

    @Override
    public List<AiTaskResponse> getByAnalysisId(UUID analysisId) {
        return aiTaskRepository.findByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId)
                .stream()
                .map(aiTaskMapper::toResponse)
                .toList();
    }

    @Override
    public AiTaskResponse submit(UUID id, SubmitAiTaskRequest request) {
        AiTask task = findTask(id);
        requireStatus(task, AiTaskStatus.CREATED, AiTaskStatus.SUBMITTED);

        task.setStatus(AiTaskStatus.SUBMITTED);
        task.setExternalJobId(request.externalJobId());
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setSubmittedAt(Instant.now());

        return saveAndMap(task);
    }

    @Override
    public AiTaskResponse startProcessing(UUID id) {
        AiTask task = findTask(id);
        requireStatus(task, AiTaskStatus.SUBMITTED, AiTaskStatus.PROCESSING);

        task.setStatus(AiTaskStatus.PROCESSING);
        task.setStartedAt(Instant.now());

        return saveAndMap(task);
    }

    @Override
    public AiTaskResponse failSubmission(UUID id, FailAiTaskRequest request) {
        AiTask task = findTask(id);
        requireStatus(task, AiTaskStatus.CREATED, AiTaskStatus.FAILED);

        task.setStatus(AiTaskStatus.FAILED);
        task.setAttemptCount(task.getAttemptCount() + 1);
        task.setFailureCode(request.failureCode());
        task.setFailureMessage(request.failureMessage());
        task.setCompletedAt(Instant.now());

        return saveAndMap(task);
    }

    @Override
    public AiTaskResponse complete(UUID id) {
        AiTask task = findTask(id);
        requireStatus(task, AiTaskStatus.PROCESSING, AiTaskStatus.COMPLETED);

        if ("architecture-overview".equals(task.getIntentId())
                && "v2".equals(task.getIntentVersion())) {
            throw new ConflictException(
                    "Architecture Overview v2 must complete through the AI result callback"
            );
        }

        task.setStatus(AiTaskStatus.COMPLETED);
        task.setCompletedAt(Instant.now());

        return saveAndMap(task);
    }

    @Override
    public AiTaskResponse fail(UUID id, FailAiTaskRequest request) {
        AiTask task = findTask(id);
        requireStatus(task, AiTaskStatus.PROCESSING, AiTaskStatus.FAILED);

        task.setStatus(AiTaskStatus.FAILED);
        task.setFailureCode(request.failureCode());
        task.setFailureMessage(request.failureMessage());
        task.setCompletedAt(Instant.now());

        return saveAndMap(task);
    }

    private AiTask findTask(UUID id) {
        return aiTaskRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("AI task", id));
    }

    private AiTaskResponse saveAndMap(AiTask task) {
        return aiTaskMapper.toResponse(aiTaskRepository.save(task));
    }

    private void requireStatus(
            AiTask task,
            AiTaskStatus expected,
            AiTaskStatus target
    ) {
        if (task.getStatus() != expected) {
            throw new ConflictException(
                    "AI task cannot transition from %s to %s"
                            .formatted(task.getStatus(), target)
            );
        }
    }
}
