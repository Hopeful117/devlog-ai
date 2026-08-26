package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectFreshnessService {
    private final ProjectRepository projects;
    private final SourceRepository sources;
    private final WorkspaceManager workspaces;
    private final ProjectFreshnessPersistenceService persistence;

    public ProjectFreshnessResponse check(UUID projectId, UUID sourceId) {
        if (!projects.existsById(projectId)) throw new EntityNotFoundException("Project", projectId);
        Source source = sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("Active project Source", sourceId));
        if (source.getType() != SourceType.GIT_REPOSITORY) {
            throw new IllegalArgumentException("Project freshness requires an active Git Source");
        }
        try {
            var revision = workspaces.resolveCurrentRevision(source);
            return persistence.save(projectId, sourceId, revision.requestedRevision(),
                    revision.resolvedRevision(), Instant.now());
        } catch (RuntimeException failure) {
            if (failure instanceof EntityNotFoundException || failure instanceof IllegalArgumentException) {
                throw failure;
            }
            throw new SourceRevisionUnavailableException(sourceId, failure);
        }
    }

    public Optional<ProjectFreshnessResponse> latest(UUID projectId, UUID sourceId) {
        return persistence.latest(projectId, sourceId);
    }

    /**
     * Records a repository HEAD observation obtained outside this service
     * (automatic change detection, ADR-062). The observation is classified
     * through the existing freshness semantics against the latest comparable
     * knowledge baseline: detection may advance observedRevision and the
     * check time — never baselineRevision, never any knowledge state.
     */
    public ProjectFreshnessResponse recordObservedRevision(UUID projectId,
            UUID sourceId, String observedRevision) {
        if (!projects.existsById(projectId)) throw new EntityNotFoundException("Project", projectId);
        Source source = sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("Active project Source", sourceId));
        if (source.getType() != SourceType.GIT_REPOSITORY) {
            throw new IllegalArgumentException("Project freshness requires an active Git Source");
        }
        String requested = source.getDefaultBranch() == null || source.getDefaultBranch().isBlank()
                ? "origin/HEAD"
                : "origin/" + source.getDefaultBranch();
        try {
            return persistence.save(projectId, sourceId, requested,
                    observedRevision, Instant.now());
        } catch (RuntimeException failure) {
            if (failure instanceof EntityNotFoundException || failure instanceof IllegalArgumentException) {
                throw failure;
            }
            throw new SourceRevisionUnavailableException(sourceId, failure);
        }
    }

    /**
     * Advances the deterministic ingestion checkpoint (ingestedRevision) for a
     * Source after a completed repository synchronization. This is the ONLY
     * path through which ingestedRevision may advance; it is invoked by the
     * sync pipeline exclusively after all deterministic SYNC stages persisted
     * successfully. Understanding state is never touched here.
     */
    public ProjectFreshnessResponse recordIngestedRevision(UUID projectId,
            UUID sourceId, String ingestedRevision) {
        if (!projects.existsById(projectId)) throw new EntityNotFoundException("Project", projectId);
        try {
            return persistence.recordIngestedRevision(projectId, sourceId,
                    ingestedRevision, Instant.now());
        } catch (RuntimeException failure) {
            if (failure instanceof EntityNotFoundException || failure instanceof IllegalArgumentException) {
                throw failure;
            }
            throw new SourceRevisionUnavailableException(sourceId, failure);
        }
    }

    public ProjectFreshnessResponse recordObservedBaseline(UUID projectId, UUID sourceId,
            com.hopeful117.devlogai.analysis.entity.Analysis baselineAnalysis,
            String observedRevision) {
        if (!projects.existsById(projectId)) throw new EntityNotFoundException("Project", projectId);
        try {
            return persistence.recordObservation(projectId, sourceId, baselineAnalysis,
                    observedRevision, Instant.now());
        } catch (RuntimeException failure) {
            if (failure instanceof EntityNotFoundException || failure instanceof IllegalArgumentException) {
                throw failure;
            }
            throw new SourceRevisionUnavailableException(sourceId, failure);
        }
    }

    public ProjectFreshnessSummary summary(UUID projectId) {
        if (!projects.existsById(projectId)) throw new EntityNotFoundException("Project", projectId);
        List<Source> active = sources.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(projectId);
        int maximum = 10;
        List<ProjectFreshnessResponse> checked = active.stream().limit(maximum)
                .map(source -> persistence.latest(projectId, source.getId()))
                .flatMap(Optional::stream).toList();
        int considered = Math.min(active.size(), maximum);
        return new ProjectFreshnessSummary(ProjectFreshnessSummary.PROJECTION_VERSION,
                projectId, checked, considered - checked.size(), active.size() > maximum);
    }
}
