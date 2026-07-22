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
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    @Override
    public AnalysisWorkflowResult start(UUID analysisId) {
        boolean started = false;
        boolean selectionCompleted = false;
        AiTaskResponse createdTask = null;
        try {
            AnalysisResponse analysis = analysisService.start(analysisId);
            started = true;
            AiTaskType taskType = taskTypeResolver.resolve(analysis.type());
            IntentDefinition intent = intentCatalog.resolve(analysis.intentId(), analysis.intentVersion());
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
            AiTaskSubmissionResponse submission = aiEngineClient.submit(
                    new PromptRequest(
                            createdTask.correlationId(),
                            createdTask.correlationId(),
                            analysisId,
                            createdTask.id(),
                            createdTask.taskType(),
                            intent,
                            guidance,
                            selectedKnowledge,
                            intent.outputSchema(),
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
