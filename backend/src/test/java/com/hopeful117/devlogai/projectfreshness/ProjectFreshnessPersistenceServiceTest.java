package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProjectFreshnessPersistenceServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final SourceRepository sources = mock(SourceRepository.class);
    private final ProjectProfileSnapshotRepository profiles = mock(ProjectProfileSnapshotRepository.class);
    private final ValidatableProposalRepository proposals = mock(ValidatableProposalRepository.class);
    private final ProjectSourceFreshnessRepository freshness = mock(ProjectSourceFreshnessRepository.class);
    private final ProjectFreshnessPersistenceService service = new ProjectFreshnessPersistenceService(
            projects, sources, profiles, proposals, freshness, new ProjectFreshnessClassifier());

    @Test
    void shouldPersistAndReadAFirstCheckWithoutBaseline() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Source source = Source.builder().id(sourceId).name("GitHub").defaultBranch("main")
                .type(SourceType.GIT_REPOSITORY).active(true).build();
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(profiles.findLatestComparable(eq(projectId), eq(sourceId), any())).thenReturn(List.of());
        when(freshness.findByProjectIdAndSourceId(projectId, sourceId)).thenReturn(Optional.empty());
        when(freshness.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant checkedAt = Instant.parse("2026-08-09T17:46:19Z");
        ProjectFreshnessResponse saved = service.save(projectId, sourceId, "origin/main",
                "A".repeat(40), checkedAt);

        assertEquals(ProjectFreshnessStatus.NO_BASELINE, saved.status());
        assertEquals(ProjectRefreshGuidance.ESTABLISH_BASELINE, saved.guidance());
        assertEquals("a".repeat(40), saved.source().currentRevision());
        assertNull(saved.source().ingestedRevision());
        assertNull(saved.baseline());
        assertEquals(0, saved.review().total());
        assertEquals(checkedAt, saved.checkedAt());

        ProjectSourceFreshness entity = verifyAndCaptureSavedEntity();
        when(freshness.findByProjectIdAndSourceId(projectId, sourceId)).thenReturn(Optional.of(entity));
        assertTrue(service.latest(projectId, sourceId).isPresent());
    }

    @Test
    void shouldRecordAnObservedBaselineAsCurrentWithoutProbing() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Source source = Source.builder().id(sourceId).name("GitHub").defaultBranch("main")
                .type(SourceType.GIT_REPOSITORY).active(true).build();
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(freshness.findByProjectIdAndSourceId(projectId, sourceId))
                .thenReturn(Optional.empty());
        when(freshness.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant checkedAt = Instant.parse("2026-08-26T10:00:00Z");
        var baselineAnalysis = com.hopeful117.devlogai.analysis.entity.Analysis.builder()
                .id(UUID.randomUUID()).build();
        ProjectFreshnessResponse saved = service.recordObservation(projectId, sourceId,
                baselineAnalysis, "A".repeat(40), checkedAt);

        assertEquals(ProjectFreshnessStatus.CURRENT, saved.status());
        assertEquals(ProjectRefreshGuidance.REFRESH_NOT_NEEDED, saved.guidance());
        assertEquals("a".repeat(40), saved.source().currentRevision());
        assertEquals("a".repeat(40), saved.baseline().analyzedRevision());
        assertEquals(baselineAnalysis.getId(), saved.baseline().analysisId());

        var captor = org.mockito.ArgumentCaptor.forClass(ProjectSourceFreshness.class);
        verify(freshness).save(captor.capture());
        ProjectSourceFreshness entity = captor.getValue();
        assertEquals(ProjectFreshnessStatus.CURRENT, entity.getStatus());
        assertEquals("origin/main", entity.getRequestedRevision());
        assertEquals(entity.getCurrentRevision(), entity.getBaselineRevision());
    }

    @Test
    void shouldAdvanceIngestedRevisionOnlyThroughSyncCompletionAndClassifyPartiallyFresh() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        String observedHead = "a".repeat(40);
        String knowledgeBaseline = "b".repeat(40);
        Project project = Project.builder().id(projectId).build();
        Source source = Source.builder().id(sourceId).name("GitHub").defaultBranch("main")
                .type(SourceType.GIT_REPOSITORY).active(true).build();
        var baselineAnalysis = com.hopeful117.devlogai.analysis.entity.Analysis.builder()
                .id(UUID.randomUUID()).build();
        ProjectSourceFreshness existing = ProjectSourceFreshness.builder()
                .id(UUID.randomUUID()).project(project).source(source)
                .baselineAnalysis(baselineAnalysis)
                .status(ProjectFreshnessStatus.STALE)
                .guidance(ProjectRefreshGuidance.REFRESH_RECOMMENDED)
                .requestedRevision("origin/main").currentRevision(observedHead)
                .baselineRevision(knowledgeBaseline).checkedAt(Instant.now()).build();
        var snapshot = com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot.builder()
                .analysis(baselineAnalysis)
                .resolvedRevisions(java.util.Map.of(sourceId.toString(), knowledgeBaseline))
                .build();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(source));
        when(freshness.findByProjectIdAndSourceId(projectId, sourceId))
                .thenReturn(Optional.of(existing));
        when(profiles.findLatestComparable(eq(projectId), eq(sourceId), any()))
                .thenReturn(List.of(snapshot));
        when(freshness.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Instant checkedAt = Instant.parse("2026-08-26T11:00:00Z");
        ProjectFreshnessResponse response =
                service.recordIngestedRevision(projectId, sourceId, observedHead, checkedAt);

        assertEquals(ProjectFreshnessStatus.PARTIALLY_FRESH, response.status());
        assertEquals(ProjectRefreshGuidance.REFRESH_RECOMMENDED, response.guidance());
        assertEquals(observedHead, response.source().ingestedRevision());
        assertEquals(knowledgeBaseline, response.baseline().analyzedRevision());

        var captor = org.mockito.ArgumentCaptor.forClass(ProjectSourceFreshness.class);
        verify(freshness).save(captor.capture());
        assertEquals(observedHead, captor.getValue().getIngestedRevision());
        // Understanding state must remain untouched by deterministic sync
        assertEquals(knowledgeBaseline, captor.getValue().getBaselineRevision());
        assertEquals(baselineAnalysis, captor.getValue().getBaselineAnalysis());
    }

    @Test
    void shouldRefuseToAdvanceIngestionForUnknownCheckpoint() {
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(projects.existsById(projectId)).thenReturn(true);
        when(sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId))
                .thenReturn(Optional.of(Source.builder().id(sourceId).name("GitHub")
                        .defaultBranch("main").type(SourceType.GIT_REPOSITORY)
                        .active(true).build()));
        when(freshness.findByProjectIdAndSourceId(projectId, sourceId))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.recordIngestedRevision(projectId, sourceId, "a".repeat(40),
                        Instant.now()));
    }

    private ProjectSourceFreshness verifyAndCaptureSavedEntity() {
        var captor = org.mockito.ArgumentCaptor.forClass(ProjectSourceFreshness.class);
        verify(freshness).save(captor.capture());
        return captor.getValue();
    }
}
