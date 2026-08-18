package com.hopeful117.devlogai.temporal.service;

import com.hopeful117.devlogai.collection.workspace.GitCommandException;
import com.hopeful117.devlogai.history.entity.ProjectCommit;
import com.hopeful117.devlogai.history.model.FileChangeType;
import com.hopeful117.devlogai.history.repository.ProjectCommitRepository;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.temporal.domain.TemporalAssessment;
import com.hopeful117.devlogai.temporal.port.RepositoryStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemporalAssessmentServiceImpl implements TemporalAssessmentService {

    private final RepositoryStatePort repositoryStatePort;
    private final ProjectCommitRepository projectCommitRepository;

    @Override
    @Transactional(readOnly = true)
    public TemporalAssessment assess(Insight insight) {
        if (insight.getStatus() != InsightStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Temporal assessment not applicable for non-ACTIVE Insight status: " + insight.getStatus());
        }

        UUID insightId = insight.getId();
        List<String> evidenceReferences = insight.getEvidenceReferences();

        if (evidenceReferences == null || evidenceReferences.isEmpty()) {
            return TemporalAssessment.of(
                    insightId,
                    List.of("Insufficient evidence: no evidence references to evaluate"),
                    TemporalAssessment.Conclusion.UNKNOWN,
                    TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
        }

        var analysis = insight.getAnalysis();
        var selectedSource = analysis.getSelectedSource();
        String targetRevision = analysis.getTargetRevision();

        if (selectedSource == null) {
            return TemporalAssessment.of(
                    insightId,
                    List.of("Insufficient evidence: Analysis.selectedSource unavailable"),
                    TemporalAssessment.Conclusion.UNKNOWN,
                    TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
        }

        if (targetRevision == null || targetRevision.isBlank()) {
            return TemporalAssessment.of(
                    insightId,
                    List.of("Insufficient evidence: baseline revision (Analysis.targetRevision) unavailable"),
                    TemporalAssessment.Conclusion.UNKNOWN,
                    TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
        }

        UUID sourceId = selectedSource.getId();
        UUID projectId = insight.getProject().getId();

        String currentKnownRevision = findCurrentKnownRevision(sourceId);
        if (currentKnownRevision == null) {
            return TemporalAssessment.of(
                    insightId,
                    List.of("Insufficient evidence: currentKnownRevision cannot be determined for source"),
                    TemporalAssessment.Conclusion.UNKNOWN,
                    TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
        }

        List<String> evidence = new ArrayList<>();
        boolean anySuspectedStale = false;
        boolean anyEvaluable = false;

        List<String> enrichment = enrichWithCommitHistory(insight, projectId, createdAt(insight), evidenceReferences, sourceId);

        for (String evidenceRef : evidenceReferences) {
            try {
                boolean baselinePresent = repositoryStatePort.isFilePresentAtRevision(
                        selectedSource, targetRevision, evidenceRef);
                boolean currentPresent = repositoryStatePort.isFilePresentAtRevision(
                        selectedSource, currentKnownRevision, evidenceRef);

                if (baselinePresent && !currentPresent) {
                    anySuspectedStale = true;
                    evidence.add("File '" + evidenceRef + "' present at baseline '" + targetRevision
                            + "' but absent at currentKnownRevision '" + currentKnownRevision + "'");
                } else if (baselinePresent && currentPresent) {
                    anyEvaluable = true;
                } else if (!baselinePresent) {
                    evidence.add("Reference '" + evidenceRef + "' was not present at baseline '" + targetRevision
                            + "'; does not prove temporal degradation — skipped");
                }
            } catch (GitCommandException e) {
                return TemporalAssessment.of(
                        insightId,
                        List.of("Insufficient evidence: repository state verification unavailable ("
                                + e.getMessage() + ")"),
                        TemporalAssessment.Conclusion.UNKNOWN,
                        TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
            }
        }

        if (anySuspectedStale) {
            evidence.addAll(enrichment);
            return TemporalAssessment.of(
                    insightId,
                    evidence,
                    TemporalAssessment.Conclusion.SUSPECTED_STALE,
                    TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
        }

        if (!anyEvaluable) {
            return TemporalAssessment.of(
                    insightId,
                    List.of("Insufficient evidence: no evidence references evaluable for staleness"),
                    TemporalAssessment.Conclusion.UNKNOWN,
                    TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
        }

        evidence.add("All " + evidenceReferences.size() + " evidence references verified present at both baseline '"
                + targetRevision + "' and currentKnownRevision '" + currentKnownRevision + "'");
        evidence.addAll(enrichment);

        return TemporalAssessment.of(
                insightId,
                evidence,
                TemporalAssessment.Conclusion.CURRENT,
                TemporalAssessment.ReasoningOrigin.DETERMINISTIC);
    }

    private Instant createdAt(Insight insight) {
        return insight.getCreatedAt();
    }

    private String findCurrentKnownRevision(UUID sourceId) {
        if (sourceId == null) {
            return null;
        }
        return projectCommitRepository
                .findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId)
                .map(ProjectCommit::getCommitHash)
                .orElse(null);
    }

    private List<String> enrichWithCommitHistory(Insight insight, UUID projectId, Instant createdAt,
            List<String> evidenceReferences, UUID sourceId) {
        List<String> enrichment = new ArrayList<>();
        try {
            var commits = projectCommitRepository
                    .findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(projectId, createdAt);

            var deletedMatches = commits.stream()
                    .filter(commit -> sourceMatches(commit, sourceId))
                    .flatMap(commit -> commit.getChangedFiles().stream())
                    .filter(f -> f.getChangeType() == FileChangeType.DELETED)
                    .filter(f -> evidenceReferences.contains(f.getOldPath()))
                    .sorted(Comparator.comparing(cf -> cf.getCommit().getCommittedAt()))
                    .toList();

            for (var deletedFile : deletedMatches) {
                var commit = deletedFile.getCommit();
                enrichment.add("Corroborating: file '" + deletedFile.getOldPath()
                        + "' was deleted in commit '" + commit.getCommitHash()
                        + "' (committed at " + commit.getCommittedAt() + ")");
            }
        } catch (Exception e) {
            enrichment.add("Note: commit-history enrichment unavailable (" + e.getMessage() + ")");
        }
        return enrichment;
    }

    private boolean sourceMatches(ProjectCommit commit, UUID sourceId) {
        try {
            return commit.getSource() != null && commit.getSource().getId().equals(sourceId);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
