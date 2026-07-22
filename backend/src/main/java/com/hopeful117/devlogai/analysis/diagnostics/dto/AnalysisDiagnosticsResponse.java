package com.hopeful117.devlogai.analysis.diagnostics.dto;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AnalysisDiagnosticsResponse(
        Identity identity,
        Revision revision,
        Timeline timeline,
        Counts counts,
        Completeness completeness,
        CollectorSummary collectors,
        AiTaskSummary aiTask,
        List<PipelineStage> pipeline,
        TechnicalMetadata technicalMetadata,
        Map<String, String> links
) {
    public record Identity(UUID analysisId, UUID projectId, AnalysisType analysisType, AnalysisStatus status) { }
    public record Revision(String requestedRevision, Map<String, Object> resolvedRevisions) { }
    public record Timeline(Instant createdAt, Instant startedAt, Instant completedAt, Duration duration) { }
    public record Counts(int sourceCount, int factCount, int observationCount, int warningCount, long proposalCount) { }
    public record Completeness(boolean collectionComplete, boolean truncated, int warningCount, int errorCount) { }
    public record CollectorSummary(int collectorCount, int successfulCollectors, int collectorsWithWarnings, int failedCollectors) { }
    public record AiTaskSummary(AiTaskType taskType, AiTaskStatus status, String provider, Instant startedAt, Instant completedAt) { }
    public record PipelineStage(String name, String status, long resourceCount) { }
    public record TechnicalMetadata(String contextBuilderVersion, Map<String, Object> collectorVersions, int serializedContextSize) { }
}
