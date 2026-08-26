package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.profile.entity.ProjectProfileSnapshot;
import com.hopeful117.devlogai.profile.repository.ProjectProfileSnapshotRepository;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.repository.ValidatableProposalRepository;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class ProjectFreshnessPersistenceService {
    private final ProjectRepository projects;
    private final SourceRepository sources;
    private final ProjectProfileSnapshotRepository profiles;
    private final ValidatableProposalRepository proposals;
    private final ProjectSourceFreshnessRepository freshness;
    private final ProjectFreshnessClassifier classifier;

    @Transactional
    ProjectFreshnessResponse save(UUID projectId, UUID sourceId, String requestedRevision,
            String currentRevision, Instant checkedAt) {
        var project = projects.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));
        Source source = activeSource(projectId, sourceId);
        Optional<ProjectProfileSnapshot> baseline = profiles.findLatestComparable(
                projectId, sourceId, PageRequest.of(0, 1)).stream().findFirst();
        String rawBaseline = baseline.map(value -> value.getResolvedRevisions()
                        .get(sourceId.toString())).map(String::valueOf).orElse(null);
        String normalizedCurrent = GitCommitIdentity.normalize(currentRevision)
                .orElseThrow(() -> new IllegalStateException("Git returned an invalid commit identity"));
        String normalizedBaseline = GitCommitIdentity.normalize(rawBaseline).orElse(null);
        Analysis analysis = baseline.map(ProjectProfileSnapshot::getAnalysis).orElse(null);
        ProjectSourceFreshness entity = freshness.findByProjectIdAndSourceId(projectId, sourceId)
                .orElseGet(() -> ProjectSourceFreshness.builder().id(UUID.randomUUID()).build());
        // Observations never mutate ingestion state: only a completed deterministic
        // synchronization may advance ingestedRevision.
        String ingested = entity.getIngestedRevision();
        var classification = classifier.classify(baseline.isPresent(),
                currentRevision, rawBaseline, ingested);
        entity.setProject(project);
        entity.setSource(source);
        entity.setBaselineAnalysis(analysis);
        entity.setStatus(classification.status());
        entity.setGuidance(classification.guidance());
        entity.setRequestedRevision(requestedRevision);
        entity.setCurrentRevision(normalizedCurrent);
        entity.setBaselineRevision(normalizedBaseline);
        entity.setIngestedRevision(ingested);
        entity.setCheckedAt(checkedAt);
        return response(freshness.save(entity));
    }

    /**
     * Advances the deterministic ingestion checkpoint after a completed
     * repository synchronization. Never called speculatively: only the sync
     * pipeline may invoke this, and only after all deterministic SYNC stages
     * have been persisted successfully.
     */
    @Transactional
    ProjectFreshnessResponse recordIngestedRevision(UUID projectId, UUID sourceId,
            String ingestedRevision, Instant checkedAt) {
        if (!projects.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
        Source source = activeSource(projectId, sourceId);
        String normalizedIngested = GitCommitIdentity.normalize(ingestedRevision)
                .orElseThrow(() -> new IllegalStateException(
                        "Ingested revision is not a valid commit identity"));
        ProjectSourceFreshness entity = freshness.findByProjectIdAndSourceId(projectId, sourceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Freshness checkpoint for Source", sourceId));
        Optional<ProjectProfileSnapshot> baseline = profiles.findLatestComparable(
                projectId, sourceId, PageRequest.of(0, 1)).stream().findFirst();
        String rawBaseline = baseline.map(value -> value.getResolvedRevisions()
                        .get(sourceId.toString())).map(String::valueOf).orElse(null);
        var classification = classifier.classify(baseline.isPresent(),
                entity.getCurrentRevision(), rawBaseline, normalizedIngested);
        entity.setStatus(classification.status());
        entity.setGuidance(classification.guidance());
        entity.setIngestedRevision(normalizedIngested);
        entity.setCheckedAt(checkedAt);
        return response(freshness.save(entity));
    }

    @Transactional
    ProjectFreshnessResponse recordObservation(UUID projectId, UUID sourceId,
            Analysis baselineAnalysis, String observedRevision, Instant checkedAt) {
        var project = projects.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project", projectId));
        Source source = activeSource(projectId, sourceId);
        String normalizedCurrent = GitCommitIdentity.normalize(observedRevision)
                .orElseThrow(() -> new IllegalStateException(
                        "Observed revision is not a valid commit identity"));
        ProjectSourceFreshness entity = freshness.findByProjectIdAndSourceId(projectId, sourceId)
                .orElseGet(() -> ProjectSourceFreshness.builder().id(UUID.randomUUID()).build());
        entity.setProject(project);
        entity.setSource(source);
        entity.setBaselineAnalysis(baselineAnalysis);
        entity.setStatus(ProjectFreshnessStatus.CURRENT);
        entity.setGuidance(ProjectRefreshGuidance.REFRESH_NOT_NEEDED);
        entity.setRequestedRevision(requestedRevisionLabel(source));
        entity.setCurrentRevision(normalizedCurrent);
        entity.setBaselineRevision(normalizedCurrent);
        // Ingestion state is owned by the sync pipeline and intentionally untouched here.
        entity.setCheckedAt(checkedAt);
        return response(freshness.save(entity));
    }

    private String requestedRevisionLabel(Source source) {
        return source.getDefaultBranch() == null || source.getDefaultBranch().isBlank()
                ? "origin/HEAD"
                : "origin/" + source.getDefaultBranch();
    }

    @Transactional(readOnly = true)
    Optional<ProjectFreshnessResponse> latest(UUID projectId, UUID sourceId) {
        if (!projects.existsById(projectId)) {
            throw new EntityNotFoundException("Project", projectId);
        }
        activeSource(projectId, sourceId);
        return freshness.findByProjectIdAndSourceId(projectId, sourceId).map(this::response);
    }

    private Source activeSource(UUID projectId, UUID sourceId) {
        Source source = sources.findByIdAndProject_IdAndActiveTrue(sourceId, projectId)
                .orElseThrow(() -> new EntityNotFoundException("Active project Source", sourceId));
        if (source.getType() != SourceType.GIT_REPOSITORY) {
            throw new IllegalArgumentException("Project freshness requires an active Git Source");
        }
        return source;
    }

    private ProjectFreshnessResponse response(ProjectSourceFreshness value) {
        Analysis analysis = value.getBaselineAnalysis();
        var baseline = analysis == null ? null : new ProjectFreshnessResponse.Baseline(
                analysis.getId(), analysis.getCompletedAt(), value.getBaselineRevision());
        UUID analysisId = analysis == null ? null : analysis.getId();
        var counts = analysisId == null ? new ProjectFreshnessResponse.ReviewCounts(0, 0, 0, 0)
                : new ProjectFreshnessResponse.ReviewCounts(
                        proposals.countByAnalysisId(analysisId),
                        proposals.countByAnalysisIdAndStatus(analysisId, ProposalStatus.PROPOSED),
                        proposals.countByAnalysisIdAndStatus(analysisId, ProposalStatus.ACCEPTED),
                        proposals.countByAnalysisIdAndStatus(analysisId, ProposalStatus.REJECTED));
        Source source = value.getSource();
        return new ProjectFreshnessResponse(ProjectFreshnessResponse.PROJECTION_VERSION,
                value.getId(), value.getProject().getId(),
                new ProjectFreshnessResponse.Source(source.getId(), source.getName(),
                        source.getDefaultBranch(), value.getRequestedRevision(),
                        value.getCurrentRevision(), value.getIngestedRevision()), value.getCheckedAt(), value.getStatus(),
                value.getGuidance(), baseline, counts);
    }
}
