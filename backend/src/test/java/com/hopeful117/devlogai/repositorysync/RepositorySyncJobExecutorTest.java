package com.hopeful117.devlogai.repositorysync;

import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.repositorysync.RepositorySyncJobStateService.SyncTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositorySyncJobExecutorTest {

    private static final String FROM = "1".repeat(40);
    private static final String TO = "2".repeat(40);

    @Mock RepositorySyncJobRepository jobs;
    @Mock RepositorySyncJobStateService state;
    @Mock WorkspaceManager workspaceManager;
    @Mock GitCommandExecutor git;
    @Mock ProjectHistoryService historyService;
    @Mock ProjectFreshnessService freshnessService;

    private RepositorySyncJobExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new RepositorySyncJobExecutor(jobs, state, workspaceManager,
                git, historyService, freshnessService);
    }

    private com.hopeful117.devlogai.source.entity.Source labSource(UUID id) {
        return com.hopeful117.devlogai.source.entity.Source.builder().id(id)
                .name("lab").repositoryUrl("/tmp/lab.git").defaultBranch("main")
                .active(true).build();
    }

    private void givenPendingJob(UUID jobId) {
        when(jobs.findByStatusOrderByCreatedAtAsc(RepositorySyncJob.SyncStatus.PENDING))
                .thenReturn(java.util.List.of(
                        RepositorySyncJob.builder().id(jobId).build()));
    }

    @Test
    void successfulSyncPersistsDeterministicStateThenAdvancesCheckpointBeforeCompletion() {
        UUID jobId = UUID.randomUUID();
        givenPendingJob(jobId);
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        Path workspacePath = Path.of("/tmp/ws");
        when(state.claim(jobId)).thenReturn(
                new SyncTarget(jobId, projectId, sourceId, FROM, TO, labSource(sourceId)));
        when(workspaceManager.synchronize(any(), eq(TO))).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, TO));
        when(historyService.importHistory(any(com.hopeful117.devlogai.source.entity.Source.class), any(com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace.class))).thenReturn(
                new HistoryImportResult(sourceId, TO, 3, 2, 1));

        executor.executePendingJobs();

        InOrder order = inOrder(historyService, freshnessService, state);
        order.verify(historyService).importHistory(any(com.hopeful117.devlogai.source.entity.Source.class), any(com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace.class));
        order.verify(freshnessService).recordIngestedRevision(projectId, sourceId, TO);
        order.verify(state).markCompleted(jobId);
        verify(git).execute(eq(workspacePath),
                eq(java.util.List.of("merge-base", "--is-ancestor", FROM, TO)));
    }

    @Test
    void initialImportTargetsObservedShaWithoutAncestryVerification() {
        UUID jobId = UUID.randomUUID();
        givenPendingJob(jobId);
        when(state.claim(jobId)).thenReturn(
                new SyncTarget(jobId, UUID.randomUUID(), UUID.randomUUID(), null, TO, labSource(UUID.randomUUID())));
        when(workspaceManager.synchronize(any(), eq(TO))).thenReturn(
                new SynchronizedWorkspace(UUID.randomUUID(), Path.of("/tmp/ws"), TO));
        when(historyService.importHistory(any(com.hopeful117.devlogai.source.entity.Source.class), any(com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace.class))).thenReturn(
                new HistoryImportResult(UUID.randomUUID(), TO, 5, 5, 0));

        executor.executePendingJobs();

        verify(git, never()).execute(any(), any());
        verify(state).markCompleted(jobId);
    }

    @Test
    void failedImportNeverAdvancesIngestedRevisionAndMarksJobFailed() {
        UUID jobId = UUID.randomUUID();
        givenPendingJob(jobId);
        UUID sourceId = UUID.randomUUID();
        when(state.claim(jobId)).thenReturn(
                new SyncTarget(jobId, UUID.randomUUID(), sourceId, FROM, TO, labSource(sourceId)));
        when(workspaceManager.synchronize(any(), eq(TO))).thenReturn(
                new SynchronizedWorkspace(sourceId, Path.of("/tmp/ws"), TO));
        when(historyService.importHistory(any(com.hopeful117.devlogai.source.entity.Source.class), any(com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace.class)))
                .thenThrow(new RuntimeException("database unavailable"));

        executor.executePendingJobs();

        verify(freshnessService, never()).recordIngestedRevision(any(), any(), any());
        verify(state, never()).markCompleted(jobId);
        verify(state).markFailed(eq(jobId), contains("RuntimeException"));
    }

    @Test
    void divergedHistoryFailsSafelyWithoutImportingOrAdvancingCheckpoint() {
        UUID jobId = UUID.randomUUID();
        givenPendingJob(jobId);
        UUID sourceId = UUID.randomUUID();
        Path workspacePath = Path.of("/tmp/ws");
        when(state.claim(jobId)).thenReturn(
                new SyncTarget(jobId, UUID.randomUUID(), sourceId, FROM, TO, labSource(sourceId)));
        when(workspaceManager.synchronize(any(), eq(TO))).thenReturn(
                new SynchronizedWorkspace(sourceId, workspacePath, TO));
        when(git.execute(any(), any())).thenThrow(
                new GitCommandException("merge-base failed"));

        executor.executePendingJobs();

        verify(historyService, never()).importHistory(any(com.hopeful117.devlogai.source.entity.Source.class), any(com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace.class));
        verify(freshnessService, never()).recordIngestedRevision(any(), any(), any());
        verify(state).markFailed(eq(jobId), contains("not an ancestor"));
    }

    @Test
    void failureDetailsAreSanitizedAgainstCredentialLeakage() {
        UUID jobId = UUID.randomUUID();
        givenPendingJob(jobId);
        UUID sourceId = UUID.randomUUID();
        when(state.claim(jobId)).thenReturn(
                new SyncTarget(jobId, UUID.randomUUID(), sourceId, FROM, TO, labSource(sourceId)));
        when(workspaceManager.synchronize(any(), eq(TO))).thenReturn(
                new SynchronizedWorkspace(sourceId, Path.of("/tmp/ws"), TO));
        when(historyService.importHistory(any(com.hopeful117.devlogai.source.entity.Source.class), any(com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace.class))).thenThrow(new RuntimeException(
                "cannot reach https://user:secret-token@git.example.com/repo.git"));

        executor.executePendingJobs();

        org.mockito.ArgumentCaptor<String> failure =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(state).markFailed(eq(jobId), failure.capture());
        org.assertj.core.api.Assertions.assertThat(failure.getValue())
                .contains("[redacted]")
                .doesNotContain("secret-token")
                .doesNotContain("git.example.com");
    }

    @Test
    void claimedByAnotherWorkerIsSkippedWithoutSideEffects() {
        UUID jobId = UUID.randomUUID();
        givenPendingJob(jobId);
        when(state.claim(jobId)).thenReturn(null);

        executor.executePendingJobs();

        verifyNoInteractions(workspaceManager, historyService, freshnessService);
        verify(state, never()).markCompleted(any());
        verify(state, never()).markFailed(any(), anyString());
    }

    @Test
    void runningJobKeepsItsImmutableTargetWhenHeadAdvances() {
        UUID jobId = UUID.randomUUID();
        givenPendingJob(jobId);
        UUID sourceId = UUID.randomUUID();
        String movedHead = "c".repeat(40);
        when(state.claim(jobId)).thenReturn(
                new SyncTarget(jobId, UUID.randomUUID(), sourceId, FROM, TO, labSource(sourceId)));
        when(workspaceManager.synchronize(any(), eq(TO))).thenReturn(
                new SynchronizedWorkspace(sourceId, Path.of("/tmp/ws"), TO));
        when(historyService.importHistory(any(com.hopeful117.devlogai.source.entity.Source.class), any(com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace.class))).thenReturn(
                new HistoryImportResult(sourceId, TO, 1, 1, 0));

        executor.executePendingJobs();

        verify(workspaceManager).synchronize(any(), eq(TO));
        org.mockito.Mockito.verify(workspaceManager, never()).synchronize(any(), eq(movedHead));
        verify(freshnessService).recordIngestedRevision(any(), any(), eq(TO));
    }
}