package com.hopeful117.devlogai.repositorysync;

import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Owns every durable {@link RepositorySyncJob} state transition. All database
 * work happens inside short transactions here so that the executor can run
 * Git/network phases without holding a transaction open.
 */
@Service
@RequiredArgsConstructor
class RepositorySyncJobStateService {

    private final RepositorySyncJobRepository jobs;
    private final SourceRepository sources;

    /**
     * Claims a pending job by marking it RUNNING and returning an immutable
     * identifier snapshot together with the fully loaded Source, so Phase 1
     * can run outside any transaction without touching lazy associations.
     */
    @Transactional
    SyncTarget claim(UUID jobId) {
        RepositorySyncJob job = jobs.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("RepositorySyncJob", jobId));
        if (job.getStatus() != RepositorySyncJob.SyncStatus.PENDING) {
            return null;
        }
        Source source = sources.findWithProjectById(job.getSource().getId())
                .orElseThrow(() -> new EntityNotFoundException("Source", job.getSource().getId()));
        if (!source.isActive()) {
            return null;
        }
        job.setStatus(RepositorySyncJob.SyncStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobs.save(job);
        return new SyncTarget(job.getId(), job.getProject().getId(),
                job.getSource().getId(), job.getFromRevision(), job.getToRevision(), source);
    }

    @Transactional
    void markCompleted(UUID jobId) {
        RepositorySyncJob job = jobs.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("RepositorySyncJob", jobId));
        job.setStatus(RepositorySyncJob.SyncStatus.COMPLETED);
        job.setCompletedAt(Instant.now());
        job.setFailure(null);
        jobs.save(job);
    }

    @Transactional
    void markFailed(UUID jobId, String failure) {
        RepositorySyncJob job = jobs.findById(jobId)
                .orElseThrow(() -> new EntityNotFoundException("RepositorySyncJob", jobId));
        job.setStatus(RepositorySyncJob.SyncStatus.FAILED);
        job.setCompletedAt(Instant.now());
        job.setFailure(failure);
        jobs.save(job);
    }

    /**
     * Crash recovery: a process death while RUNNING leaves a zombie job.
     * Re-import is replay-safe (SHA deduplication), so interrupted jobs are
     * returned to PENDING at startup instead of being lost.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    void requeueInterruptedJobs() {
        List<RepositorySyncJob> interrupted =
                jobs.findByStatus(RepositorySyncJob.SyncStatus.RUNNING);
        for (RepositorySyncJob job : interrupted) {
            job.setStatus(RepositorySyncJob.SyncStatus.PENDING);
            job.setStartedAt(null);
            jobs.save(job);
        }
        if (!interrupted.isEmpty()) {
            org.slf4j.LoggerFactory.getLogger(RepositorySyncJobStateService.class)
                    .warn("Requeued {} repository synchronization job(s) left RUNNING "
                            + "by a previous execution", interrupted.size());
        }
    }

    record SyncTarget(UUID jobId, UUID projectId, UUID sourceId,
                      String fromRevision, String toRevision,
                      com.hopeful117.devlogai.source.entity.Source source) {
    }
}