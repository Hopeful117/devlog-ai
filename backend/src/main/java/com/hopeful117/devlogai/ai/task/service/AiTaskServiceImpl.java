package com.hopeful117.devlogai.ai.task.service;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.mapper.AiTaskMapper;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
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

    @Override
    public AiTaskResponse create(CreateAiTaskRequest request) {
        Analysis analysis = findAnalysis(request.analysisId());
        AnalysisContext context = analysisContextService.build(request.analysisId());
        return create(request, context, analysis);
    }

    @Override
    public AiTaskResponse create(
            CreateAiTaskRequest request,
            AnalysisContext context
    ) {
        return create(request, context, findAnalysis(request.analysisId()));
    }

    private AiTaskResponse create(
            CreateAiTaskRequest request,
            AnalysisContext context,
            Analysis analysis
    ) {
        @SuppressWarnings("unchecked")
        Map<String, Object> contextSnapshot = objectMapper.convertValue(
                context,
                Map.class
        );

        AiTask task = aiTaskMapper.toEntity(request);
        IntentDefinition intent = intentCatalog.resolve(analysis.getIntentId(), analysis.getIntentVersion());
        @SuppressWarnings("unchecked")
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
