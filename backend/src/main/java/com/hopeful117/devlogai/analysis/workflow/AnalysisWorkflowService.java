package com.hopeful117.devlogai.analysis.workflow;

import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;

import java.util.UUID;

public interface AnalysisWorkflowService {

    AnalysisWorkflowResult start(UUID analysisId, AiTaskType taskType);
}
