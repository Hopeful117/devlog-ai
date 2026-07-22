package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionRequest;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskSubmissionResponse;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisResult;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisService;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import com.hopeful117.devlogai.collection.service.KnowledgeCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisWorkflowServiceImpl implements AnalysisWorkflowService {

    private final AnalysisService analysisService;
    private final KnowledgeCollectionService knowledgeCollectionService;
    private final DeterministicAnalysisService deterministicAnalysisService;
    private final AnalysisContextService analysisContextService;
    private final AiTaskService aiTaskService;
    private final AIEngineClient aiEngineClient;

    @Override
    public AnalysisWorkflowResult start(UUID analysisId, AiTaskType taskType) {
        boolean started = false;
        AiTaskResponse createdTask = null;
        try {
            AnalysisResponse analysis = analysisService.start(analysisId);
            started = true;
            knowledgeCollectionService.collect(analysisId);
            DeterministicAnalysisResult deterministicResult =
                    deterministicAnalysisService.analyze(analysisId);
            AnalysisContext context = analysisContextService.build(analysisId);
            createdTask = aiTaskService.create(
                    new CreateAiTaskRequest(analysisId, taskType),
                    context
            );
            AiTaskSubmissionResponse submission = aiEngineClient.submit(
                    new AiTaskSubmissionRequest(
                            createdTask.correlationId(),
                            createdTask.taskType(),
                            analysisId,
                            context
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
                    markTaskSubmissionFailed(createdTask.id(), failure);
                }
                markAnalysisFailed(analysisId, failure);
            }
            throw failure;
        }
    }

    private void markTaskSubmissionFailed(
            UUID taskId,
            RuntimeException failure
    ) {
        try {
            aiTaskService.failSubmission(
                    taskId,
                    new FailAiTaskRequest(
                            "AI_ENGINE_SUBMISSION_FAILED",
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
