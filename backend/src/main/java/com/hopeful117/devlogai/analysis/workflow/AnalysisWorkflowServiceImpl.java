package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisResult;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisService;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import com.hopeful117.devlogai.collection.service.KnowledgeCollectionService;
import com.hopeful117.devlogai.profile.service.ProjectProfileService;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.knowledge.selection.KnowledgeSelectionService;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledgePromptProjectionService;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineBindingClient;
import com.hopeful117.devlogai.aicredentialbinding.service.AiTaskExecutionAuthorizationService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisWorkflowServiceImpl implements AnalysisWorkflowService {

    private final AnalysisService analysisService;
    private final AnalysisAiTaskTypeResolver taskTypeResolver;
    private final KnowledgeCollectionService knowledgeCollectionService;
    private final DeterministicAnalysisService deterministicAnalysisService;
    private final ProjectProfileService projectProfileService;
    private final AnalysisContextService analysisContextService;
    private final AiTaskService aiTaskService;
    private final AIEngineClient aiEngineClient;
    private final IntentCatalog intentCatalog;
    private final KnowledgeSelectionService knowledgeSelectionService;
    private final SelectedKnowledgePromptProjectionService promptProjectionService;

    private AiTaskExecutionAuthorizationService taskAuthorizationService;
    private AiEngineBindingClient aiEngineBindingClient;
    private AiTaskRepository aiTaskRepository;

    @Autowired(required = false)
    void setUserBoundExecution(AiTaskExecutionAuthorizationService taskAuthorizationService,
                               AiEngineBindingClient aiEngineBindingClient,
                               AiTaskRepository aiTaskRepository) {
        this.taskAuthorizationService = taskAuthorizationService;
        this.aiEngineBindingClient = aiEngineBindingClient;
        this.aiTaskRepository = aiTaskRepository;
    }

    @Override
    public AnalysisWorkflowResult start(UUID analysisId) {
        return start(analysisId, null);
    }

    @Override
    public AnalysisWorkflowResult start(UUID analysisId, AuthenticatedPrincipal principal) {
        boolean started = false;
        boolean selectionCompleted = false;
        AiTaskResponse createdTask = null;
        try {
            AnalysisResponse analysis = analysisService.start(analysisId);
            started = true;
            IntentDefinition intent = intentCatalog.resolve(analysis.intentId(), analysis.intentVersion());
            AiTaskType taskType = taskTypeResolver.resolve(analysis.type(), intent);
            knowledgeCollectionService.collect(analysisId);
            DeterministicAnalysisResult deterministicResult =
                    deterministicAnalysisService.analyze(analysisId);
            projectProfileService.build(analysisId);
            AnalysisContext context = analysisContextService.build(analysisId);
            UserGuidance guidance = UserGuidance.from(analysis.userGuidance());
            createdTask = aiTaskService.create(
                    new CreateAiTaskRequest(analysisId, taskType), context);
            SelectedKnowledge selectedKnowledge = knowledgeSelectionService.select(
                    context, intent, guidance);
            createdTask = aiTaskService.attachSelectedKnowledge(createdTask.id(), selectedKnowledge);
            selectionCompleted = true;
            if (principal != null) {
                bindUserCredential(principal, createdTask.id());
            }
            AiTaskSubmissionResponse submission = aiEngineClient.submit(
                    new PromptRequest(
                            createdTask.correlationId(),
                            createdTask.correlationId(),
                            analysisId,
                            createdTask.id(),
                            createdTask.taskType(),
                            intent,
                            guidance,
                            "architecture-overview".equals(intent.id()) && "v3".equals(intent.version())
                                    ? promptProjectionService.toTypedArchitectureOverviewMap(selectedKnowledge)
                                    : promptProjectionService.toMap(selectedKnowledge),
                            intent.outputSchema(),
                            Map.of(), // groundingContract - not used for standard analysis
                            java.util.Map.of(
                                    "source", "devlog-ai-core",
                                    "analysisContextId", analysisId.toString())
                    )
            );
            AiTaskResponse submittedTask = aiTaskService.submit(
                    createdTask.id(),
                    new SubmitAiTaskRequest(submission.externalJobId())
            );

            return new AnalysisWorkflowResult(
                    analysisId,
                    analysis.status(),
                    deterministicResult.factCount(),
                    deterministicResult.observationCount(),
                    submittedTask.id(),
                    submittedTask.status(),
                    submittedTask.correlationId()
            );
        } catch (RuntimeException failure) {
            if (started) {
                log.error(
                        "Analysis workflow preparation failed for analysis {}",
                        analysisId,
                        failure
                );
                if (createdTask != null) {
                    markTaskFailed(createdTask.id(), failure, selectionCompleted
                            ? "AI_ENGINE_SUBMISSION_FAILED" : "KNOWLEDGE_SELECTION_FAILED");
                }
                markAnalysisFailed(analysisId, failure);
            }
            throw failure;
        }
    }

    /**
     * Story 0241: issue the principal's provider credential binding for this
     * AI task and register it with the AI Engine before submission, exactly
     * like the Story Context path. Fails closed when user-bound execution is
     * not configured or the principal's AI configuration is not READY.
     */
    private void bindUserCredential(AuthenticatedPrincipal principal, UUID taskId) {
        if (taskAuthorizationService == null || aiEngineBindingClient == null || aiTaskRepository == null) {
            throw new IllegalStateException("user-bound AI task authorization is not configured");
        }
        AiTask task = aiTaskRepository.findById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("AiTask", taskId));
        // Binding only: outside Story Context, provider and model are recorded
        // atomically with the prompt identity at callback time.
        var authorization = taskAuthorizationService.authorizeAndIssueBinding(principal, task);
        aiEngineBindingClient.register(authorization.binding());
    }

    private void markTaskFailed(
            UUID taskId,
            RuntimeException failure,
            String failureCode
    ) {
        try {
            aiTaskService.failSubmission(
                    taskId,
                    new FailAiTaskRequest(
                            failureCode,
                            failureMessage(failure)
                    )
            );
        } catch (RuntimeException transitionFailure) {
            failure.addSuppressed(transitionFailure);
            log.error(
                    "Unable to mark AI task {} as FAILED after submission failure",
                    taskId,
                    transitionFailure
            );
        }
    }

    private String failureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() <= 5000 ? message : message.substring(0, 5000);
    }

    private void markAnalysisFailed(UUID analysisId, RuntimeException failure) {
        try {
            analysisService.fail(analysisId);
        } catch (RuntimeException transitionFailure) {
            failure.addSuppressed(transitionFailure);
            log.error(
                    "Unable to mark analysis {} as FAILED after workflow failure",
                    analysisId,
                    transitionFailure
            );
        }
    }
}
