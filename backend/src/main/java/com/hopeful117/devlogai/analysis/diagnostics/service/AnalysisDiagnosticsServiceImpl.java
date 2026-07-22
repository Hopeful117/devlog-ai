package com.hopeful117.devlogai.analysis.diagnostics.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.diagnostics.dto.*;
import com.hopeful117.devlogai.analysis.diagnostics.entity.*;
import com.hopeful117.devlogai.analysis.diagnostics.repository.*;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalysisDiagnosticsServiceImpl implements AnalysisDiagnosticsService {
    private static final String CONTEXT_BUILDER_VERSION = "analysis-context-v3";
    private final AnalysisRepository analysisRepository;
    private final AnalysisExecutionDiagnosticRepository diagnosticRepository;
    private final CollectionWarningRepository warningRepository;
    private final AiTaskRepository aiTaskRepository;
    private final ValidatableProposalRepository proposalRepository;
    private final ProjectProfileSnapshotRepository profileRepository;
    private final ObjectMapper objectMapper;

    @Override
    public AnalysisDiagnosticsResponse getDiagnostics(UUID analysisId) {
        Analysis analysis = findAnalysis(analysisId);
        AnalysisExecutionDiagnostic diagnostic = diagnosticRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analysis diagnostics", analysisId));
        AiTask task = aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId).orElse(null);
        long proposalCount = proposalRepository.countByAnalysisId(analysisId);
        ProjectProfileSnapshot profile = profileRepository.findByAnalysisId(analysisId).orElse(null);
        Instant durationEnd = analysis.getCompletedAt() != null ? analysis.getCompletedAt() : Instant.now();
        Duration duration = analysis.getStartedAt() == null ? null : Duration.between(analysis.getStartedAt(), durationEnd);
        int contextSize = task == null ? 0 : objectMapper.writeValueAsString(task.getContextSnapshot())
                .getBytes(StandardCharsets.UTF_8).length;
        String base = "/api/v1/analyses/" + analysisId;

        return new AnalysisDiagnosticsResponse(
                new AnalysisDiagnosticsResponse.Identity(analysisId, analysis.getProject().getId(),
                        analysis.getType(), analysis.getIntentId(), analysis.getIntentVersion(), analysis.getStatus()),
                new AnalysisDiagnosticsResponse.Revision(analysis.getTargetRevision(), diagnostic.getResolvedRevisions()),
                new AnalysisDiagnosticsResponse.Timeline(analysis.getCreatedAt(), analysis.getStartedAt(), analysis.getCompletedAt(), duration),
                new AnalysisDiagnosticsResponse.Counts(diagnostic.getSourceCount(), diagnostic.getFactCount(), diagnostic.getObservationCount(), diagnostic.getWarningCount(), proposalCount),
                new AnalysisDiagnosticsResponse.Completeness(diagnostic.isCollectionComplete(), diagnostic.isTruncated(), diagnostic.getWarningCount(), diagnostic.getErrorCount()),
                new AnalysisDiagnosticsResponse.CollectorSummary(diagnostic.getCollectorCount(), diagnostic.getSuccessfulCollectors(), diagnostic.getCollectorsWithWarnings(), diagnostic.getFailedCollectors()),
                task == null ? null : new AnalysisDiagnosticsResponse.AiTaskSummary(
                        task.getTaskType(), task.getStatus(), task.getIntentId(), task.getIntentVersion(),
                        "ai-engine", task.getStartedAt(), task.getCompletedAt()),
                pipeline(diagnostic, task, proposalCount, profile),
                new AnalysisDiagnosticsResponse.TechnicalMetadata(CONTEXT_BUILDER_VERSION, diagnostic.getCollectorVersions(), contextSize),
                profile == null
                        ? new AnalysisDiagnosticsResponse.ProfileSummary(false, null, null, null, 0)
                        : new AnalysisDiagnosticsResponse.ProfileSummary(true, profile.getId(), profile.getProfileVersion(), profile.getCompletenessStatus(), profile.getCharacteristicCount()),
                Map.of("facts", base + "/facts", "observations", base + "/observations", "warnings", base + "/warnings", "context", base + "/context", "profile", base + "/profile", "aiTask", base + "/ai-task", "proposals", base + "/proposals")
        );
    }

    @Override
    public List<CollectionWarningResponse> getWarnings(UUID analysisId) {
        findAnalysis(analysisId);
        return warningRepository.findByAnalysisIdOrderByOccurredAtAscIdAsc(analysisId).stream()
                .map(w -> new CollectionWarningResponse(w.getId(), analysisId,
                        w.getSource() == null ? null : w.getSource().getId(), w.getCollectorType(),
                        w.getCollectorVersion(), w.getCode(), w.getSeverity(), w.getMessage(),
                        w.getEvidenceReference(), w.getMetadata(), w.getOccurredAt()))
                .toList();
    }

    @Override
    public Map<String, Object> getContext(UUID analysisId) {
        findAnalysis(analysisId);
        return aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId)
                .map(AiTask::getContextSnapshot)
                .map(Map::copyOf)
                .orElseThrow(() -> new EntityNotFoundException("Analysis context snapshot", analysisId));
    }

    private Analysis findAnalysis(UUID id) {
        return analysisRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Analysis", id));
    }

    private List<AnalysisDiagnosticsResponse.PipelineStage> pipeline(
            AnalysisExecutionDiagnostic d, AiTask task, long proposalCount,
            ProjectProfileSnapshot profile) {
        return List.of(
                new AnalysisDiagnosticsResponse.PipelineStage("WORKSPACE", "COMPLETED", d.getSourceCount()),
                new AnalysisDiagnosticsResponse.PipelineStage("COLLECTORS", d.isCollectionComplete() ? "COMPLETED" : "PARTIAL", d.getCollectorCount()),
                new AnalysisDiagnosticsResponse.PipelineStage("FACTS", "COMPLETED", d.getFactCount()),
                new AnalysisDiagnosticsResponse.PipelineStage("OBSERVATIONS", "COMPLETED", d.getObservationCount()),
                new AnalysisDiagnosticsResponse.PipelineStage("PROJECT_PROFILE", profile == null ? "PENDING" : "COMPLETED", profile == null ? 0 : 1),
                new AnalysisDiagnosticsResponse.PipelineStage("ANALYSIS_CONTEXT", task == null ? "PENDING" : "COMPLETED", task == null ? 0 : 1),
                new AnalysisDiagnosticsResponse.PipelineStage("AI_TASK", task == null ? "PENDING" : task.getStatus().name(), task == null ? 0 : 1),
                new AnalysisDiagnosticsResponse.PipelineStage("PROPOSALS", task == null ? "PENDING" : "AVAILABLE", proposalCount)
        );
    }
}
