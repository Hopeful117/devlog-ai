package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.context.AnalysisContextService;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisResult;
import com.hopeful117.devlogai.analysis.deterministic.DeterministicAnalysisService;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisWorkflowServiceImpl implements AnalysisWorkflowService {

    private final AnalysisService analysisService;
    private final DeterministicAnalysisService deterministicAnalysisService;
    private final AnalysisContextService analysisContextService;
    private final AiTaskService aiTaskService;

    @Override
    public AnalysisWorkflowResult start(UUID analysisId, AiTaskType taskType) {
        boolean started = false;
        try {
            AnalysisResponse analysis = analysisService.start(analysisId);
            started = true;
            DeterministicAnalysisResult deterministicResult =
                    deterministicAnalysisService.analyze(analysisId);
            AnalysisContext context = analysisContextService.build(analysisId);
            AiTaskResponse aiTask = aiTaskService.create(
                    new CreateAiTaskRequest(analysisId, taskType),
                    context
            );

            return new AnalysisWorkflowResult(
                    analysisId,
                    analysis.status(),
                    deterministicResult.factCount(),
                    deterministicResult.observationCount(),
                    aiTask.id(),
                    aiTask.status(),
                    aiTask.correlationId()
            );
        } catch (RuntimeException failure) {
            if (started) {
                log.error(
                        "Analysis workflow preparation failed for analysis {}",
                        analysisId,
                        failure
                );
                markAnalysisFailed(analysisId, failure);
            }
            throw failure;
        }
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
