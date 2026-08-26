package com.hopeful117.devlogai.repositoryobservation;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.repositorysync.RepositorySyncJob;
import com.hopeful117.devlogai.repositorysync.RepositorySyncJobRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledRepositoryChangeDetectorTest {

    @Mock SourceRepository sources;
    @Mock ProjectFreshnessService freshnessService;
    @Mock RepositoryRevisionProbe probe;
    @Mock RepositorySyncJobRepository syncJobRepository;

    private ScheduledRepositoryChangeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ScheduledRepositoryChangeDetector(sources, freshnessService, probe,
                syncJobRepository);
    }

    @Test
    void recordsObservationsForEveryEligibleSource() {
        Source first = eligibleSource("first");
        Source second = eligibleSource("second");
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(first, second));
        when(freshnessService.latest(first.getProject().getId(), first.getId()))
                .thenReturn(Optional.empty());
        when(freshnessService.latest(second.getProject().getId(), second.getId()))
                .thenReturn(Optional.empty());
        when(probe.probeHead(first)).thenReturn("a".repeat(40));
        when(probe.probeHead(second)).thenReturn("b".repeat(40));
        when(freshnessService.recordObservedRevision(any(), any(), any()))
                .thenReturn(mock(ProjectFreshnessResponse.class));

        detector.detectRepositoryChanges();

        verify(freshnessService).recordObservedRevision(
                first.getProject().getId(), first.getId(), "a".repeat(40));
        verify(freshnessService).recordObservedRevision(
                second.getProject().getId(), second.getId(), "b".repeat(40));
    }

    @Test
    void isolatesProbeFailuresAndPreservesPreviousCheckpoints() {
        Source failing = eligibleSource("failing");
        Source healthy = eligibleSource("healthy");
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(failing, healthy));
        when(freshnessService.latest(healthy.getProject().getId(), healthy.getId()))
                .thenReturn(Optional.empty());
        when(probe.probeHead(failing))
                .thenThrow(new com.hopeful117.devlogai.collection.workspace.GitCommandException(
                        "remote unavailable"));
        when(probe.probeHead(healthy)).thenReturn("c".repeat(40));

        assertThatCode(() -> detector.detectRepositoryChanges()).doesNotThrowAnyException();

        verify(freshnessService, never()).recordObservedRevision(
                eq(failing.getProject().getId()), eq(failing.getId()), any());
        verify(freshnessService).recordObservedRevision(
                healthy.getProject().getId(), healthy.getId(), "c".repeat(40));
    }

    @Test
    void repeatedCyclesRemainIdempotent() {
        Source source = eligibleSource("stable");
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(source));
        when(freshnessService.latest(source.getProject().getId(), source.getId()))
                .thenReturn(Optional.empty());
        when(probe.probeHead(source)).thenReturn("d".repeat(40));

        for (int cycle = 0; cycle < 3; cycle++) {
            detector.detectRepositoryChanges();
        }

        verify(freshnessService,
                org.mockito.Mockito.times(3)).recordObservedRevision(
                source.getProject().getId(), source.getId(), "d".repeat(40));
        // detection must never advance the knowledge baseline (ADR-062 §20)
        verify(freshnessService, never()).recordObservedBaseline(
                any(), any(), any(), any());
    }

    @Test
    void schedulesInitialImportJobWhenNoRevisionHasBeenIngested() {
        Source source = eligibleSource("initial");
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(source));
        when(freshnessService.latest(source.getProject().getId(), source.getId()))
                .thenReturn(Optional.empty());
        when(probe.probeHead(source)).thenReturn("e".repeat(40));
        when(freshnessService.recordObservedRevision(any(), any(), any()))
                .thenReturn(mock(ProjectFreshnessResponse.class));
        when(syncJobRepository.existsBySourceIdAndStatusIn(eq(source.getId()), any()))
                .thenReturn(false);

        detector.detectRepositoryChanges();

        ArgumentCaptor<RepositorySyncJob> captor = ArgumentCaptor.forClass(RepositorySyncJob.class);
        verify(syncJobRepository).save(captor.capture());
        RepositorySyncJob job = captor.getValue();
        assertThat(job.getFromRevision()).isNull();
        assertThat(job.getToRevision()).isEqualTo("e".repeat(40));
        assertThat(job.getReason()).isEqualTo(RepositorySyncJob.SyncReason.INITIAL_IMPORT);
        assertThat(job.getStatus()).isEqualTo(RepositorySyncJob.SyncStatus.PENDING);
        assertThat(job.getProject().getId()).isEqualTo(source.getProject().getId());
        assertThat(job.getSource().getId()).isEqualTo(source.getId());
    }

    @Test
    void schedulesForwardSyncJobTargetingImmutableObservedShaWhenIngestionBehind() {
        Source source = eligibleSource("forward");
        UUID projectId = source.getProject().getId();
        String ingested = "1".repeat(40);
        String observed = "f".repeat(40);
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(source));
        when(freshnessService.latest(projectId, source.getId())).thenReturn(
                Optional.of(freshnessResponse(source.getId(), ingested)));
        when(probe.probeHead(source)).thenReturn(observed);
        when(freshnessService.recordObservedRevision(any(), any(), any()))
                .thenReturn(mock(ProjectFreshnessResponse.class));
        when(syncJobRepository.existsBySourceIdAndStatusIn(eq(source.getId()), any()))
                .thenReturn(false);

        detector.detectRepositoryChanges();

        ArgumentCaptor<RepositorySyncJob> captor = ArgumentCaptor.forClass(RepositorySyncJob.class);
        verify(syncJobRepository).save(captor.capture());
        RepositorySyncJob job = captor.getValue();
        assertThat(job.getFromRevision()).isEqualTo(ingested);
        assertThat(job.getToRevision()).isEqualTo(observed);
        assertThat(job.getReason()).isEqualTo(
                RepositorySyncJob.SyncReason.REPOSITORY_CHANGE_DETECTED);
    }

    @Test
    void doesNotDuplicateJobsWhenOneIsAlreadyScheduledOrRunning() {
        Source source = eligibleSource("guarded");
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(source));
        when(freshnessService.latest(source.getProject().getId(), source.getId()))
                .thenReturn(Optional.empty());
        when(probe.probeHead(source)).thenReturn("9".repeat(40));
        when(freshnessService.recordObservedRevision(any(), any(), any()))
                .thenReturn(mock(ProjectFreshnessResponse.class));
        when(syncJobRepository.existsBySourceIdAndStatusIn(eq(source.getId()), any()))
                .thenReturn(true);

        detector.detectRepositoryChanges();

        verify(syncJobRepository, never()).save(any());
    }

    @Test
    void doesNotScheduleJobWhenObservedMatchesIngestedRevision() {
        Source source = eligibleSource("caught-up");
        UUID projectId = source.getProject().getId();
        String revision = "7".repeat(40);
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(source));
        when(freshnessService.latest(projectId, source.getId())).thenReturn(
                Optional.of(freshnessResponse(source.getId(), revision)));
        when(probe.probeHead(source)).thenReturn(revision);
        when(freshnessService.recordObservedRevision(any(), any(), any()))
                .thenReturn(mock(ProjectFreshnessResponse.class));

        detector.detectRepositoryChanges();

        verify(syncJobRepository, never()).save(any());
        verify(syncJobRepository, never()).existsBySourceIdAndStatusIn(any(), any());
    }

    @Test
    void schedulesInitialImportEvenWhenObservationIsUnchangedButIngestionBehind() {
        Source source = eligibleSource("behind-stable");
        UUID projectId = source.getProject().getId();
        String head = "4".repeat(40);
        when(sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY)).thenReturn(List.of(source));
        // previous observation == HEAD (no change), but nothing ingested yet
        when(freshnessService.latest(projectId, source.getId())).thenReturn(
                Optional.of(freshnessResponse(source.getId(), null)));
        when(probe.probeHead(source)).thenReturn(head);
        when(freshnessService.recordObservedRevision(any(), any(), any()))
                .thenReturn(mock(ProjectFreshnessResponse.class));
        when(syncJobRepository.existsBySourceIdAndStatusIn(eq(source.getId()), any()))
                .thenReturn(false);

        detector.detectRepositoryChanges();

        ArgumentCaptor<RepositorySyncJob> captor = ArgumentCaptor.forClass(RepositorySyncJob.class);
        verify(syncJobRepository).save(captor.capture());
        assertThat(captor.getValue().getToRevision()).isEqualTo(head);
        assertThat(captor.getValue().getReason())
                .isEqualTo(RepositorySyncJob.SyncReason.INITIAL_IMPORT);
    }

    private ProjectFreshnessResponse freshnessResponse(UUID sourceId, String ingestedRevision) {
        return new ProjectFreshnessResponse(ProjectFreshnessResponse.PROJECTION_VERSION,
                UUID.randomUUID(), UUID.randomUUID(),
                new ProjectFreshnessResponse.Source(sourceId, "repo", "main",
                        "origin/main", "a".repeat(40), ingestedRevision),
                java.time.Instant.now(),
                com.hopeful117.devlogai.projectfreshness.ProjectFreshnessStatus.STALE,
                com.hopeful117.devlogai.projectfreshness.ProjectRefreshGuidance.REFRESH_RECOMMENDED,
                null, new ProjectFreshnessResponse.ReviewCounts(0, 0, 0, 0));
    }

    private Source eligibleSource(String name) {
        return Source.builder().id(UUID.randomUUID())
                .type(SourceType.GIT_REPOSITORY)
                .name(name)
                .repositoryUrl("https://example.test/" + name + ".git")
                .defaultBranch("main")
                .active(true)
                .project(Project.builder().id(UUID.randomUUID()).build())
                .build();
    }
}
