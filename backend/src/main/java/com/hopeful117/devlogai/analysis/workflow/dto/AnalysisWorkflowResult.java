package com.hopeful117.devlogai.analysis.workflow.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;

import java.util.UUID;

public record AnalysisWorkflowResult(
        UUID analysisId,
        AnalysisStatus analysisStatus,
        int factCount,
        int observationCount,
        UUID aiTaskId,
        AiTaskStatus aiTaskStatus,
        UUID correlationId
) {
}
