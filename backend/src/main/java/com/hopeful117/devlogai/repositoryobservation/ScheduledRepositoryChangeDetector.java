package com.hopeful117.devlogai.repositoryobservation;

import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
import com.hopeful117.devlogai.repositorysync.RepositorySyncJob;
import com.hopeful117.devlogai.repositorysync.RepositorySyncJobRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outer detection adapter (ADR-062): a thin scheduled probe producing
 * observations only. For every eligible active Git source it observes the
 * current immutable HEAD revision and records it through the freshness
 * boundary. It never imports, synchronizes, analyzes or mutates anything —
 * STALE is an observation, not a command.
 *
 * <p>Concurrency: fixedDelay guarantees non-overlapping cycles; the default
 * single-thread task scheduler serializes all scheduled work. Single-instance
 * deployment assumed.
 *
 * <p>After recording an observed revision, if the revision is newer than the
 * current ingestedRevision, a RepositorySyncJob is created to synchronize
 * deterministic repository state from the previous ingested revision to the
 * newly observed revision. The job is persisted; execution is handled by the
 * sync pipeline outside this observer.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledRepositoryChangeDetector {

    private final SourceRepository sources;
    private final ProjectFreshnessService freshnessService;
    private final RepositoryRevisionProbe revisionProbe;
    private final RepositorySyncJobRepository syncJobRepository;

    @Scheduled(
            fixedDelayString = "${devlog.repository-observation.interval:300s}",
            initialDelayString = "${devlog.repository-observation.initial-delay:30s}"
    )
    public void detectRepositoryChanges() {
        List<Source> eligible = sources.findByTypeAndActiveTrueOrderByProjectIdAscCreatedAtAscIdAsc(
                SourceType.GIT_REPOSITORY);
        for (Source source : eligible) {
            observeQuietly(source);
        }
    }

    private void observeQuietly(Source source) {
        String previous = null;
        try {
            UUID projectId = source.getProject().getId();
            previous = previousObservedRevision(projectId, source);
            String observed = revisionProbe.probeHead(source);
            ProjectFreshnessResponse recorded = freshnessService.recordObservedRevision(
                    projectId, source.getId(), observed);
            boolean changed = previous == null || !previous.equalsIgnoreCase(observed);
            if (changed) {
                log.info("Repository revision observed for source {} (project {}): "
                                + "{} -> {} ({}, baseline {}, guidance {})",
                        source.getId(), projectId,
                        previous == null ? "unknown" : previous.substring(0, Math.min(12, previous.length())),
                        observed.substring(0, Math.min(12, observed.length())),
                        recorded.status(),
                        recorded.baseline() == null || recorded.baseline().analyzedRevision() == null
                                ? "none" : recorded.baseline().analyzedRevision()
                                                .substring(0, Math.min(12, recorded.baseline().analyzedRevision().length())),
                        recorded.guidance());
            } else {
                log.debug("Repository revision unchanged for source {}: {} ({})",
                        source.getId(), observed.substring(0, 12), recorded.status());
            }
            // Scheduling depends on deterministic state being behind — NOT on the
            // observation having just changed: an unchanged HEAD can still require
            // synchronization when ingestion never ran or previously failed.
            scheduleSyncIfBehind(source, projectId, observed);
        } catch (RuntimeException failure) {
            log.warn("Repository HEAD observation failed for source {}; previous "
                            + "checkpoint preserved ({}): {}",
                    source.getId(),
                    previous == null ? "none" : previous.substring(0, Math.min(12, previous.length())),
                    failure.getMessage());
        }
    }

    private void scheduleSyncIfBehind(Source source, UUID projectId, String observed) {
        String ingested = ingestedRevision(projectId, source);
        boolean behind = observed != null
                && (ingested == null || !observed.equalsIgnoreCase(ingested));
        if (!behind || syncJobRepository.existsBySourceIdAndStatusIn(
                source.getId(), List.of(RepositorySyncJob.SyncStatus.PENDING,
                        RepositorySyncJob.SyncStatus.RUNNING))) {
            return;
        }
        boolean initialImport = ingested == null;
        RepositorySyncJob job = RepositorySyncJob.builder()
                .project(source.getProject())
                .source(source)
                .fromRevision(initialImport ? null : ingested)
                .toRevision(observed)
                .reason(initialImport
                        ? RepositorySyncJob.SyncReason.INITIAL_IMPORT
                        : RepositorySyncJob.SyncReason.REPOSITORY_CHANGE_DETECTED)
                .status(RepositorySyncJob.SyncStatus.PENDING)
                .attempt(0)
                .build();
        syncJobRepository.save(job);
        log.info("Repository sync job scheduled for source {}: {} -> {}",
                source.getId(),
                initialImport ? "initial" : ingested.substring(0, Math.min(12, ingested.length())),
                observed.substring(0, Math.min(12, observed.length())));
    }

    private String previousObservedRevision(UUID projectId, Source source) {
        Optional<ProjectFreshnessResponse> latest = freshnessService.latest(
                projectId, source.getId());
        return latest.map(response -> response.source().currentRevision()).orElse(null);
    }

    private String ingestedRevision(UUID projectId, Source source) {
        Optional<ProjectFreshnessResponse> latest = freshnessService.latest(projectId, source.getId());
        return latest.map(response -> response.source().ingestedRevision()).orElse(null);
    }
}
