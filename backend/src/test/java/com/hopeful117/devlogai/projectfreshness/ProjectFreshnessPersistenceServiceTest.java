package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
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

    private ProjectSourceFreshness verifyAndCaptureSavedEntity() {
        var captor = org.mockito.ArgumentCaptor.forClass(ProjectSourceFreshness.class);
        verify(freshness).save(captor.capture());
        return captor.getValue();
    }
}
