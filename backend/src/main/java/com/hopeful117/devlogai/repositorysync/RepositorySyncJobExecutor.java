package com.hopeful117.devlogai.repositorysync;

import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.collection.workspace.GitCommandExecutor;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.history.dto.HistoryImportResult;
import com.hopeful117.devlogai.history.service.ProjectHistoryService;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Deterministic repository synchronization pipeline (ADR-062 lifecycle,
 * investigation "Deterministic Repository Synchronization").
 *
 * <p>Executes PENDING {@link RepositorySyncJob}s in explicit phases:</p>
 *
 * <pre>
 * Phase 1 — Git/repository I/O (no database transaction held):
 *           synchronize the managed workspace to the immutable toRevision SHA
 *           and verify ancestry for incremental targets.
 * Phase 2 — deterministic DB persistence: import commit metadata and changed
 *           paths through the existing history infrastructure (SHA dedup).
 * Phase 3 — durable outputs: advance ingestedRevision FIRST, then mark the
 *           job COMPLETED, so a crash can never lose a completed ingestion.
 * </pre>
 *
 * <p>The pipeline never invokes AI, proposal generation, semantic analysis or
 * Understanding. A failed job never advances ingestedRevision.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RepositorySyncJobExecutor {

    private final RepositorySyncJobRepository jobs;
    private final RepositorySyncJobStateService state;
    private final WorkspaceManager workspaceManager;
    private final GitCommandExecutor git;
    private final ProjectHistoryService historyService;
    private final ProjectFreshnessService freshnessService;

    @Scheduled(
            fixedDelayString = "${devlog.repository-sync.interval:60s}",
            initialDelayString = "${devlog.repository-sync.initial-delay:15s}"
    )
    public void executePendingJobs() {
        List<RepositorySyncJob> pending =
                jobs.findByStatusOrderByCreatedAtAsc(RepositorySyncJob.SyncStatus.PENDING);
        for (RepositorySyncJob job : pending) {
            executeQuietly(job.getId());
        }
    }

    private void executeQuietly(java.util.UUID jobId) {
        try {
            execute(jobId);
        } catch (RuntimeException failure) {
            log.warn("Repository synchronization job {} failed unexpectedly: {}",
                    jobId, failure.getClass().getSimpleName());
        }
    }

    private void execute(java.util.UUID jobId) {
        RepositorySyncJobStateService.SyncTarget target = state.claim(jobId);
        if (target == null) {
            return;
        }
        try {
            // Phase 1 — Git I/O outside any transaction, against the fully
            // loaded Source captured inside the claim transaction.
            SynchronizedWorkspace workspace =
                    workspaceManager.synchronize(target.source(), target.toRevision());
            verifyAncestry(workspace.path(), target.fromRevision(), target.toRevision());

            // Phase 2 — deterministic persistence (own transaction).
            HistoryImportResult result = historyService.importHistory(target.source(), workspace);

            // Phase 3 — checkpoint first, then completion marker.
            freshnessService.recordIngestedRevision(
                    target.projectId(), target.sourceId(), workspace.resolvedRevision());
            state.markCompleted(jobId);
            log.info("Repository synchronization completed for source {}: "
                            + "{} commit(s) discovered, {} imported, ingested revision now {}",
                    target.sourceId(), result.discoveredCommits(), result.importedCommits(),
                    workspace.resolvedRevision().substring(0,
                            Math.min(12, workspace.resolvedRevision().length())));
        } catch (RuntimeException failure) {
            String sanitized = sanitize(failure);
            state.markFailed(jobId, sanitized);
            log.warn("Repository synchronization job {} failed for source {}: {}",
                    jobId, target.sourceId(), sanitized);
        }
    }

    /**
     * Force-push / non-fast-forward guard: an incremental job whose fromRevision
     * is not an ancestor of its immutable target indicates rewritten history.
     * The job fails safely; checkpoints are preserved and no persisted history
     * is deleted. Full repair workflows remain out of scope.
     */
    private void verifyAncestry(java.nio.file.Path workspacePath,
            String fromRevision, String toRevision) {
        if (fromRevision == null || fromRevision.equalsIgnoreCase(toRevision)) {
            return;
        }
        try {
            git.execute(workspacePath,
                    List.of("merge-base", "--is-ancestor", fromRevision, toRevision));
        } catch (GitCommandException exception) {
            throw new RepositorySyncDivergenceException(fromRevision, toRevision);
        }
    }

    private String sanitize(RuntimeException failure) {
        String raw = failure.getMessage() == null ? "" : failure.getMessage();
        String redacted = raw.replaceAll("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+", "[redacted]");
        String bounded = redacted.length() > 300 ? redacted.substring(0, 300) : redacted;
        return failure.getClass().getSimpleName() + ": " + bounded;
    }

    /**
     * Signals unsupported repository divergence without leaking repository
     * identity or credentials into persisted failure details.
     */
    static class RepositorySyncDivergenceException extends RuntimeException {
        RepositorySyncDivergenceException(String fromRevision, String toRevision) {
            super("Repository history diverged: ingested revision "
                    + abbreviate(fromRevision) + " is not an ancestor of target "
                    + abbreviate(toRevision)
                    + "; manual repair is required before synchronization can continue");
        }

        private static String abbreviate(String revision) {
            return revision == null ? "unknown"
                    : revision.substring(0, Math.min(12, revision.length()));
        }
    }
}