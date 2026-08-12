package com.hopeful117.devlogai.analysis.diagnostics.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.diagnostics.dto.AnalysisDiagnosticsResponse;
import com.hopeful117.devlogai.analysis.diagnostics.dto.CollectionWarningResponse;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.entity.CollectionWarningEntity;
import com.hopeful117.devlogai.analysis.diagnostics.entity.WarningSeverity;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.diagnostics.repository.CollectionWarningRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.collection.collector.CollectorType;
import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import com.hopeful117.devlogai.profile.model.ProfileCompletenessStatus;
import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisDiagnosticsServiceTest {

    @Mock private AnalysisRepository analysisRepository;
    @Mock private AnalysisExecutionDiagnosticRepository diagnosticRepository;
    @Mock private CollectionWarningRepository warningRepository;
    @Mock private AiTaskRepository aiTaskRepository;
    @Mock private ValidatableProposalRepository proposalRepository;
    @Mock private ProjectProfileSnapshotRepository profileRepository;
    @Mock private ObjectMapper objectMapper;

    private AnalysisDiagnosticsServiceImpl createService() {
        return new AnalysisDiagnosticsServiceImpl(
                analysisRepository, diagnosticRepository, warningRepository,
                aiTaskRepository, proposalRepository, profileRepository, objectMapper
        );
    }

    private Analysis createAnalysis(UUID analysisId, UUID projectId) {
        Project project = Project.builder().id(projectId).build();
        return Analysis.builder()
                .id(analysisId).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .intentId("architecture-overview").intentVersion("v1")
                .status(AnalysisStatus.IN_PROGRESS)
                .targetRevision("main")
                .createdAt(Instant.now()).build();
    }

    @Test
    void shouldGetDiagnosticsWithAllData() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, projectId);

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(2).factCount(10).observationCount(5)
                .warningCount(1).errorCount(0)
                .collectorCount(3).successfulCollectors(2)
                .collectorsWithWarnings(1).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of(projectId.toString(), "abc123"))
                .collectorVersions(Map.of("GIT", "v1", "SPRING", "v1"))
                .collectedAt(Instant.now()).build();

        AiTask task = AiTask.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .correlationId(UUID.randomUUID())
                .taskType(AiTaskType.INSIGHT_GENERATION)
                .status(AiTaskStatus.COMPLETED)
                .intentId("architecture-overview").intentVersion("v1")
                .contextSnapshot(Map.of("key", "value"))
                .createdAt(Instant.now()).startedAt(Instant.now()).completedAt(Instant.now())
                .build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.of(task));
        when(proposalRepository.countByAnalysisId(analysisId)).thenReturn(3L);
        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"key\":\"value\"}");

        AnalysisDiagnosticsServiceImpl service = createService();
        AnalysisDiagnosticsResponse response = service.getDiagnostics(analysisId);

        assertNotNull(response);
        assertEquals(analysisId, response.identity().analysisId());
        assertEquals(projectId, response.identity().projectId());
        assertEquals(AnalysisType.ARCHITECTURE_REVIEW, response.identity().analysisType());
        assertEquals(2, response.counts().sourceCount());
        assertEquals(10, response.counts().factCount());
        assertEquals(5, response.counts().observationCount());
        assertEquals(1, response.counts().warningCount());
        assertEquals(3, response.counts().proposalCount());
        assertTrue(response.completeness().collectionComplete());
        assertEquals(AiTaskType.INSIGHT_GENERATION, response.aiTask().taskType());
        assertEquals(AiTaskStatus.COMPLETED, response.aiTask().status());
        assertNotNull(response.technicalMetadata());
        assertNotNull(response.links());
        assertFalse(response.profile().profileAvailable());
    }

    @Test
    void shouldGetDiagnosticsWithoutAiTask() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, projectId);

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(0).observationCount(0)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.empty());
        when(proposalRepository.countByAnalysisId(analysisId)).thenReturn(0L);
        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        AnalysisDiagnosticsResponse response = service.getDiagnostics(analysisId);

        assertNotNull(response);
        assertNull(response.aiTask());
        assertEquals(0, response.technicalMetadata().serializedContextSize());
    }

    @Test
    void shouldGetDiagnosticsWithProfile() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, projectId);

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(5).observationCount(3)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of("GIT", "v1"))
                .collectedAt(Instant.now()).build();

        ProjectProfileSnapshot profile = ProjectProfileSnapshot.builder()
                .id(profileId)
                .project(analysis.getProject()).analysis(analysis)
                .profileVersion("v1").rendererVersion("r1")
                .generatedAt(Instant.now())
                .resolvedRevisions(Map.of())
                .completenessStatus(ProfileCompletenessStatus.COMPLETE)
                .collectionComplete(true).truncated(false)
                .warningCount(0).errorCount(0)
                .successfulCollectorCount(1).collectorsWithWarningsCount(0).failedCollectorCount(0)
                .sections(List.of()).deterministicSummary("summary")
                .sourceObservations(List.of()).characteristicCount(5)
                .build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.empty());
        when(proposalRepository.countByAnalysisId(analysisId)).thenReturn(0L);
        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.of(profile));

        AnalysisDiagnosticsServiceImpl service = createService();
        AnalysisDiagnosticsResponse response = service.getDiagnostics(analysisId);

        assertTrue(response.profile().profileAvailable());
        assertEquals(profileId, response.profile().profileId());
        assertEquals(5, response.profile().characteristicCount());
    }

    @Test
    void shouldThrowWhenAnalysisNotFoundForDiagnostics() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.getDiagnostics(analysisId));
    }

    @Test
    void shouldThrowWhenDiagnosticNotFound() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, UUID.randomUUID());

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.getDiagnostics(analysisId));
    }

    @Test
    void shouldGetWarnings() {
        UUID analysisId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, UUID.randomUUID());
        Source source = Source.builder().id(sourceId).build();

        CollectionWarningEntity warning = CollectionWarningEntity.builder()
                .id(UUID.randomUUID()).analysis(analysis).source(source)
                .collectorType(CollectorType.GIT).collectorVersion("v1")
                .code("TIMEOUT").severity(WarningSeverity.WARNING)
                .message("Collector timeout").metadata(Map.of())
                .occurredAt(Instant.now()).build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(warningRepository.findByAnalysisIdOrderByOccurredAtAscIdAsc(analysisId))
                .thenReturn(List.of(warning));

        AnalysisDiagnosticsServiceImpl service = createService();
        List<CollectionWarningResponse> warnings = service.getWarnings(analysisId);

        assertEquals(1, warnings.size());
        assertEquals("TIMEOUT", warnings.getFirst().code());
        assertEquals(WarningSeverity.WARNING, warnings.getFirst().severity());
        assertEquals(sourceId, warnings.getFirst().sourceId());
    }

    @Test
    void shouldGetEmptyWarnings() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, UUID.randomUUID());

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(warningRepository.findByAnalysisIdOrderByOccurredAtAscIdAsc(analysisId))
                .thenReturn(List.of());

        AnalysisDiagnosticsServiceImpl service = createService();
        List<CollectionWarningResponse> warnings = service.getWarnings(analysisId);

        assertTrue(warnings.isEmpty());
    }

    @Test
    void shouldThrowWhenAnalysisNotFoundForWarnings() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.getWarnings(analysisId));
    }

    @Test
    void shouldGetContext() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, UUID.randomUUID());
        Map<String, Object> context = Map.of("key", "value", "nested", Map.of("a", 1));

        AiTask task = AiTask.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .correlationId(UUID.randomUUID())
                .taskType(AiTaskType.INSIGHT_GENERATION)
                .status(AiTaskStatus.COMPLETED)
                .contextSnapshot(context)
                .createdAt(Instant.now()).build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.of(task));

        AnalysisDiagnosticsServiceImpl service = createService();
        Map<String, Object> result = service.getContext(analysisId);

        assertEquals("value", result.get("key"));
        assertNotNull(result.get("nested"));
    }

    @Test
    void shouldExposeTheCurrentNullContainingContextFailureShape() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, UUID.randomUUID());
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("key", "value");
        context.put("nullable", null);

        AiTask task = AiTask.builder()
                .id(UUID.randomUUID()).analysis(analysis)
                .correlationId(UUID.randomUUID())
                .taskType(AiTaskType.INSIGHT_GENERATION)
                .status(AiTaskStatus.COMPLETED)
                .contextSnapshot(context)
                .createdAt(Instant.now()).build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.of(task));

        AnalysisDiagnosticsServiceImpl service = createService();

        assertThrows(NullPointerException.class, () -> service.getContext(analysisId));
    }

    @Test
    void shouldThrowWhenContextNotFound() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, UUID.randomUUID());

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.getContext(analysisId));
    }

    @Test
    void shouldThrowWhenAnalysisNotFoundForContext() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        assertThrows(EntityNotFoundException.class, () -> service.getContext(analysisId));
    }

    @Test
    void shouldHandleDiagnosticsWithNullStartedAt() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder()
                .id(analysisId).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .intentId("arch").intentVersion("v1")
                .status(AnalysisStatus.PENDING)
                .createdAt(Instant.now()).build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(0).factCount(0).observationCount(0)
                .warningCount(0).errorCount(0)
                .collectorCount(0).successfulCollectors(0)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(false).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.empty());
        when(proposalRepository.countByAnalysisId(analysisId)).thenReturn(0L);
        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        AnalysisDiagnosticsResponse response = service.getDiagnostics(analysisId);

        assertNull(response.timeline().duration());
        assertNull(response.timeline().startedAt());
    }

    @Test
    void shouldHandleDiagnosticsWithCompletedAnalysis() {
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Instant started = Instant.parse("2026-01-01T10:00:00Z");
        Instant completed = Instant.parse("2026-01-01T10:05:00Z");

        Project project = Project.builder().id(projectId).build();
        Analysis analysis = Analysis.builder()
                .id(analysisId).project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .intentId("arch").intentVersion("v1")
                .status(AnalysisStatus.COMPLETED)
                .startedAt(started).completedAt(completed)
                .createdAt(Instant.now()).build();

        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).analysis(analysis)
                .sourceCount(1).factCount(5).observationCount(3)
                .warningCount(0).errorCount(0)
                .collectorCount(1).successfulCollectors(1)
                .collectorsWithWarnings(0).failedCollectors(0)
                .collectionComplete(true).truncated(false)
                .resolvedRevisions(Map.of()).collectorVersions(Map.of())
                .collectedAt(Instant.now()).build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(diagnosticRepository.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId))
                .thenReturn(Optional.empty());
        when(proposalRepository.countByAnalysisId(analysisId)).thenReturn(0L);
        when(profileRepository.findByAnalysisId(analysisId)).thenReturn(Optional.empty());

        AnalysisDiagnosticsServiceImpl service = createService();
        AnalysisDiagnosticsResponse response = service.getDiagnostics(analysisId);

        assertNotNull(response.timeline().duration());
        assertEquals(300, response.timeline().duration().getSeconds());
    }

    @Test
    void shouldHandleWarningWithNullSource() {
        UUID analysisId = UUID.randomUUID();
        Analysis analysis = createAnalysis(analysisId, UUID.randomUUID());

        CollectionWarningEntity warning = CollectionWarningEntity.builder()
                .id(UUID.randomUUID()).analysis(analysis).source(null)
                .collectorType(CollectorType.GIT).collectorVersion("v1")
                .code("WARN").severity(WarningSeverity.WARNING)
                .message("Test warning").metadata(Map.of())
                .occurredAt(Instant.now()).build();

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(warningRepository.findByAnalysisIdOrderByOccurredAtAscIdAsc(analysisId))
                .thenReturn(List.of(warning));

        AnalysisDiagnosticsServiceImpl service = createService();
        List<CollectionWarningResponse> warnings = service.getWarnings(analysisId);

        assertEquals(1, warnings.size());
        assertNull(warnings.getFirst().sourceId());
    }
}
