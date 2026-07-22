package com.hopeful117.devlogai.ai.task.service;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;

import java.util.List;
import java.util.UUID;

public interface AiTaskService {

    AiTaskResponse create(CreateAiTaskRequest request);

    AiTaskResponse create(CreateAiTaskRequest request, AnalysisContext context);

    AiTaskResponse create(CreateAiTaskRequest request, AnalysisContext context,
                          SelectedKnowledge selectedKnowledge);

    AiTaskResponse attachSelectedKnowledge(UUID id, SelectedKnowledge selectedKnowledge);

    AiTaskResponse getById(UUID id);

    AiTaskResponse getByCorrelationId(UUID correlationId);

    List<AiTaskResponse> getByAnalysisId(UUID analysisId);

    AiTaskResponse submit(UUID id, SubmitAiTaskRequest request);

    AiTaskResponse failSubmission(UUID id, FailAiTaskRequest request);

    AiTaskResponse startProcessing(UUID id);

    AiTaskResponse complete(UUID id);

    AiTaskResponse fail(UUID id, FailAiTaskRequest request);
}
