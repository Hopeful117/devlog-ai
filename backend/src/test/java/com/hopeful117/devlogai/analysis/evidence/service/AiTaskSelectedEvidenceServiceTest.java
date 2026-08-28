package com.hopeful117.devlogai.analysis.evidence.service;

import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ArchitectureKnowledgeSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.Availability;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.Categories;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.EngineeringEventsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.EvolutionContextSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.FactsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.HumanContextSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ObservationsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.PriorInsightsSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.ProjectedSnapshot;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.RepositoryEvidenceSection;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.SnapshotMetadata;
import com.hopeful117.devlogai.analysis.evidence.projection.HistoricalSelectedEvidenceSnapshotProjector;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiTaskSelectedEvidenceServiceTest {
    private static final String VERSION = "knowledge-selection-v4";
    private static final String DIGEST = "a".repeat(64);

    @Mock private AnalysisRepository analysisRepository;
    @Mock private AiTaskRepository aiTaskRepository;
    @Mock private HistoricalSelectedEvidenceSnapshotProjector projector;

    @Test
    void shouldThrowExistingNotFoundExceptionWhenAnalysisIsMissing() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findWithProjectById(analysisId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service().getSelectedEvidence(analysisId));

        verifyNoInteractions(aiTaskRepository, projector);
    }

    @Test
    void shouldReturnNoTaskForExistingAnalysis() {
        Analysis analysis = analysis();
        when(analysisRepository.findWithProjectById(analysis.getId()))
                .thenReturn(Optional.of(analysis));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysis.getId()))
                .thenReturn(Optional.empty());

        AiTaskSelectedEvidenceResponse result =
                service().getSelectedEvidence(analysis.getId());

        assertEquals(AiTaskSelectedEvidenceResponse.State.NO_AI_TASK, result.state());
        assertEquals(analysis.getId(), result.analysisId());
        assertEquals(analysis.getProject().getId(), result.projectId());
        assertNull(result.task());
        verifyNoInteractions(projector);
    }

    @ParameterizedTest
    @EnumSource(value = AiTaskStatus.class, names = {"CREATED", "SUBMITTED", "PROCESSING"})
    void shouldReturnPendingForNonterminalTaskWithoutSnapshot(AiTaskStatus status) {
        Analysis analysis = analysis();
        AiTask task = task(analysis, status, null);
        arrange(analysis, task);

        AiTaskSelectedEvidenceResponse result =
                service().getSelectedEvidence(analysis.getId());

        assertEquals(AiTaskSelectedEvidenceResponse.State.SNAPSHOT_PENDING, result.state());
        assertEquals(task.getId(), result.task().id());
        assertEquals(status, result.task().status());
        verifyNoInteractions(projector);
    }

    @ParameterizedTest
    @EnumSource(value = AiTaskStatus.class, names = {"COMPLETED", "FAILED"})
    void shouldReturnUnavailableForTerminalTaskWithoutSnapshot(AiTaskStatus status) {
        Analysis analysis = analysis();
        AiTask task = task(analysis, status, null);
        arrange(analysis, task);

        AiTaskSelectedEvidenceResponse result =
                service().getSelectedEvidence(analysis.getId());

        assertEquals(AiTaskSelectedEvidenceResponse.State.SNAPSHOT_UNAVAILABLE, result.state());
        assertEquals(task.getId(), result.task().id());
        verifyNoInteractions(projector);
    }

    @ParameterizedTest
    @EnumSource(AiTaskStatus.class)
    void shouldReturnAvailableWheneverSnapshotExists(AiTaskStatus status) {
        Analysis analysis = analysis();
        AiTask task = task(analysis, status, Map.of("selectedFacts", List.of()));
        ProjectedSnapshot projected = emptyProjection();
        arrange(analysis, task);
        when(projector.project(task.getId(), VERSION, DIGEST,
                analysis.getId(), analysis.getProject().getId(), task.getSelectedKnowledgeSnapshot()))
                .thenReturn(projected);

        AiTaskSelectedEvidenceResponse result =
                service().getSelectedEvidence(analysis.getId());

        assertEquals(AiTaskSelectedEvidenceResponse.State.AVAILABLE, result.state());
        assertEquals(task.getId(), result.task().id());
        assertEquals(AiTaskType.INSIGHT_GENERATION, result.task().taskType());
        assertEquals(task.getCreatedAt(), result.task().createdAt());
        assertEquals(VERSION, result.selectionVersion());
        assertEquals(DIGEST, result.selectionDigest());
        assertSame(projected.categories(), result.categories());
        verify(aiTaskRepository)
                .findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysis.getId());
    }

    @Test
    void shouldFailClosedWhenResolvedTaskBelongsToAnotherAnalysis() {
        Analysis requested = analysis();
        Analysis other = analysis();
        AiTask task = task(other, AiTaskStatus.COMPLETED, Map.of());
        when(analysisRepository.findWithProjectById(requested.getId()))
                .thenReturn(Optional.of(requested));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(requested.getId()))
                .thenReturn(Optional.of(task));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service().getSelectedEvidence(requested.getId()));

        assertEquals("Selected evidence association failed task=%s path=task.analysis"
                .formatted(task.getId()), exception.getMessage());
        verify(projector, never()).project(
                task.getId(), VERSION, DIGEST, requested.getId(),
                requested.getProject().getId(), task.getSelectedKnowledgeSnapshot());
    }

    @Test
    void shouldHaveOnlyApprovedNoRecomputeConstructorDependencies() {
        Constructor<?> constructor = Arrays.stream(
                        AiTaskSelectedEvidenceServiceImpl.class.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 3)
                .findFirst()
                .orElseThrow();

        assertArrayEquals(new Class<?>[]{
                AnalysisRepository.class,
                AiTaskRepository.class,
                HistoricalSelectedEvidenceSnapshotProjector.class
        }, constructor.getParameterTypes());
    }

    private AiTaskSelectedEvidenceServiceImpl service() {
        return new AiTaskSelectedEvidenceServiceImpl(
                analysisRepository, aiTaskRepository, projector);
    }

    private void arrange(Analysis analysis, AiTask task) {
        when(analysisRepository.findWithProjectById(analysis.getId()))
                .thenReturn(Optional.of(analysis));
        when(aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysis.getId()))
                .thenReturn(Optional.of(task));
    }

    private Analysis analysis() {
        Project project = Project.builder()
                .id(UUID.randomUUID())
                .name("Evidence project")
                .slug("evidence-" + UUID.randomUUID())
                .status(ProjectStatus.ACTIVE)
                .build();
        return Analysis.builder()
                .id(UUID.randomUUID())
                .project(project)
                .type(AnalysisType.ARCHITECTURE_REVIEW)
                .status(AnalysisStatus.COMPLETED)
                .createdAt(Instant.parse("2026-08-27T10:00:00Z"))
                .build();
    }

    private AiTask task(Analysis analysis, AiTaskStatus status, Map<String, Object> snapshot) {
        return AiTask.builder()
                .id(UUID.randomUUID())
                .analysis(analysis)
                .correlationId(UUID.randomUUID())
                .taskType(AiTaskType.INSIGHT_GENERATION)
                .status(status)
                .contextSnapshot(Map.of())
                .selectedKnowledgeSnapshot(snapshot)
                .selectionVersion(snapshot == null ? null : VERSION)
                .selectionDigest(snapshot == null ? null : DIGEST)
                .createdAt(Instant.parse("2026-08-27T10:01:00Z"))
                .build();
    }

    private ProjectedSnapshot emptyProjection() {
        return new ProjectedSnapshot(
                new SnapshotMetadata(null, null, null, null, null, null),
                new Categories(
                        new FactsSection(Availability.RECORDED, 0, List.of()),
                        new ObservationsSection(Availability.NOT_RECORDED, 0, List.of()),
                        new PriorInsightsSection(Availability.NOT_RECORDED, 0, List.of()),
                        new ArchitectureKnowledgeSection(Availability.NOT_RECORDED, 0, List.of()),
                        new EngineeringEventsSection(Availability.NOT_RECORDED, 0, List.of()),
                        new HumanContextSection(Availability.NOT_RECORDED, 0, List.of()),
                        new EvolutionContextSection(Availability.NOT_RECORDED, 0, List.of()),
                        new RepositoryEvidenceSection(Availability.NOT_RECORDED, 0, List.of())
                )
        );
    }
}
