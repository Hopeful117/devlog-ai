package com.hopeful117.devlogai.repositoryobservation;

import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessResponse;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessService;
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
 * deployment assumed.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledRepositoryChangeDetector {

    private final SourceRepository sources;
    private final ProjectFreshnessService freshnessService;
    private final RepositoryRevisionProbe revisionProbe;

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
        } catch (RuntimeException failure) {
            log.warn("Repository HEAD observation failed for source {}; previous "
                            + "checkpoint preserved ({}): {}",
                    source.getId(),
                    previous == null ? "none" : previous.substring(0, Math.min(12, previous.length())),
                    failure.getMessage());
        }
    }

    private String previousObservedRevision(UUID projectId, Source source) {
        Optional<ProjectFreshnessResponse> latest = freshnessService.latest(
                projectId, source.getId());
        return latest.map(response -> response.source().currentRevision()).orElse(null);
    }
}
