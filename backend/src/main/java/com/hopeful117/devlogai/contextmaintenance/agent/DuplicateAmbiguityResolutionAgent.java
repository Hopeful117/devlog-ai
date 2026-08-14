package com.hopeful117.devlogai.contextmaintenance.agent;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterCategory;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateMemberResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicateAmbiguityResolutionAgent {

    private static final int RICHNESS_delta_THRESHOLD = 2;

    public Optional<AgentAssessmentResult> evaluate(
            String findingIssueType,
            InsightDuplicateClusterResponse cluster
    ) {
        if (!isAmbiguousFindingType(findingIssueType)) {
            log.debug("Skipping non-ambiguous finding type: {}", findingIssueType);
            return Optional.empty();
        }
        if (cluster == null || cluster.members() == null || cluster.members().size() < 2) {
            log.debug("Skipping cluster with insufficient members");
            return Optional.empty();
        }

        AgentAssessmentResult result = switch (cluster.category()) {
            case LIKELY_SEMANTIC_DUPLICATE -> evaluateSemanticDuplicate(cluster);
            case LIKELY_RICHER_SUCCESSOR -> evaluateRicherSuccessor(cluster);
            case REVIEW_REQUIRED -> evaluateReviewRequired(cluster);
            default -> null;
        };

        if (result == null) {
            return Optional.empty();
        }

        if (isLowConfidence(result.confidenceLevel())) {
            log.debug("Suppressing low-confidence assessment for cluster: {}", cluster.clusterKey());
            return Optional.empty();
        }

        log.info("Produced assessment for cluster={} classification={} confidence={} action={}",
                cluster.clusterKey(), result.semanticClassification(),
                result.confidenceLevel(), result.recommendedAction());
        return Optional.of(result);
    }

    private AgentAssessmentResult evaluateSemanticDuplicate(InsightDuplicateClusterResponse cluster) {
        List<InsightDuplicateMemberResponse> members = cluster.members();
        boolean sameFamily = members.stream()
                .map(InsightDuplicateMemberResponse::sourceType)
                .distinct()
                .count() == 1;

        if (sameFamily) {
            InsightDuplicateMemberResponse richest = findRichest(members);
            return new AgentAssessmentResult(
                    MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                    MaintenanceAssessmentConfidenceLevel.HIGH,
                    MaintenanceAssessmentRecommendedAction.RESOLVE,
                    buildRationale(cluster, "same-family semantic duplicate"),
                    buildSupportingSignals(members, richest)
            );
        }

        return new AgentAssessmentResult(
                MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                MaintenanceAssessmentConfidenceLevel.MEDIUM,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                buildRationale(cluster, "cross-family semantic overlap"),
                buildSupportingSignals(members, findRichest(members))
        );
    }

    private AgentAssessmentResult evaluateRicherSuccessor(InsightDuplicateClusterResponse cluster) {
        List<InsightDuplicateMemberResponse> members = cluster.members();
        InsightDuplicateMemberResponse richest = findRichest(members);
        InsightDuplicateMemberResponse second = findSecondRichest(members);

        int richnessDelta = computeRichnessScore(richest) - computeRichnessScore(second);
        boolean hasProvenanceAdvantage = hasRicherProvenance(richest, second);

        if (richnessDelta >= RICHNESS_delta_THRESHOLD || hasProvenanceAdvantage) {
            return new AgentAssessmentResult(
                    MaintenanceAssessmentSemanticClassification.LIKELY_ENRICHMENT,
                    MaintenanceAssessmentConfidenceLevel.HIGH,
                    MaintenanceAssessmentRecommendedAction.RESOLVE,
                    buildRicherSuccessorRationale(cluster, richest, richnessDelta, hasProvenanceAdvantage),
                    buildSupportingSignals(members, richest)
            );
        }

        return new AgentAssessmentResult(
                MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                MaintenanceAssessmentConfidenceLevel.MEDIUM,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                buildRationale(cluster, "marginal richness difference"),
                buildSupportingSignals(members, richest)
        );
    }

    private AgentAssessmentResult evaluateReviewRequired(InsightDuplicateClusterResponse cluster) {
        List<InsightDuplicateMemberResponse> members = cluster.members();
        InsightDuplicateMemberResponse richest = findRichest(members);

        return new AgentAssessmentResult(
                MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                MaintenanceAssessmentConfidenceLevel.MEDIUM,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                buildRationale(cluster, "ambiguous overlap requiring human judgment"),
                buildSupportingSignals(members, richest)
        );
    }

    private boolean isAmbiguousFindingType(String issueType) {
        return "TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE".equals(issueType)
                || "TRUSTED_KNOWLEDGE_OVERLAP_REVIEW".equals(issueType);
    }

    private boolean isLowConfidence(MaintenanceAssessmentConfidenceLevel level) {
        return level == MaintenanceAssessmentConfidenceLevel.LOW
                || level == MaintenanceAssessmentConfidenceLevel.VERY_LOW;
    }

    private InsightDuplicateMemberResponse findRichest(List<InsightDuplicateMemberResponse> members) {
        return members.stream()
                .max(Comparator.comparingInt(this::computeRichnessScore)
                        .thenComparing(InsightDuplicateMemberResponse::createdAt, Comparator.reverseOrder())
                        .thenComparing(InsightDuplicateMemberResponse::insightId, Comparator.reverseOrder()))
                .orElseThrow();
    }

    private InsightDuplicateMemberResponse findSecondRichest(List<InsightDuplicateMemberResponse> members) {
        List<InsightDuplicateMemberResponse> sorted = members.stream()
                .sorted(Comparator.comparingInt(this::computeRichnessScore).reversed()
                        .thenComparing(InsightDuplicateMemberResponse::createdAt, Comparator.reverseOrder())
                        .thenComparing(InsightDuplicateMemberResponse::insightId, Comparator.reverseOrder()))
                .toList();
        return sorted.size() > 1 ? sorted.get(1) : sorted.getFirst();
    }

    private int computeRichnessScore(InsightDuplicateMemberResponse member) {
        int score = 0;
        if (member.sourceType() != null && !member.sourceType().isBlank()) score += 4;
        if (member.rationale() != null && !member.rationale().isBlank()) score += 3;
        if (member.confidence() != null) score += 1;
        if (member.evidenceReferenceCount() > 0) score += 2;
        String content = member.content() == null ? "" : member.content();
        score += Math.min(3, content.length() / 80);
        return score;
    }

    private boolean hasRicherProvenance(
            InsightDuplicateMemberResponse candidate,
            InsightDuplicateMemberResponse other
    ) {
        boolean candidateHasSource = candidate.sourceType() != null && !candidate.sourceType().isBlank();
        boolean otherHasSource = other.sourceType() != null && !other.sourceType().isBlank();
        boolean candidateHasRationale = candidate.rationale() != null && !candidate.rationale().isBlank();
        boolean otherHasRationale = other.rationale() != null && !other.rationale().isBlank();

        return (candidateHasSource && !otherHasSource)
                || (candidateHasRationale && !otherHasRationale)
                || candidate.evidenceReferenceCount() > other.evidenceReferenceCount();
    }

    private String buildRationale(InsightDuplicateClusterResponse cluster, String reason) {
        return "Deterministic cluster '%s' classified as %s. %s. Rationale: %s"
                .formatted(
                        cluster.clusterKey(),
                        cluster.category(),
                        reason,
                        cluster.rationale()
                );
    }

    private String buildRicherSuccessorRationale(
            InsightDuplicateClusterResponse cluster,
            InsightDuplicateMemberResponse richest,
            int richnessDelta,
            boolean hasProvenanceAdvantage
    ) {
        StringBuilder rationale = new StringBuilder();
        rationale.append("Deterministic cluster '%s' has a materially richer record. ".formatted(cluster.clusterKey()));
        rationale.append("Richest member: '%s' (insightId=%s). ".formatted(richest.title(), richest.insightId()));
        if (richnessDelta >= RICHNESS_delta_THRESHOLD) {
            rationale.append("Richness score delta: %d. ".formatted(richnessDelta));
        }
        if (hasProvenanceAdvantage) {
            rationale.append("Has provenance advantage (sourceType, rationale, or evidence references). ");
        }
        rationale.append("Original rationale: %s".formatted(cluster.rationale()));
        return rationale.toString();
    }

    private String buildSupportingSignals(
            List<InsightDuplicateMemberResponse> members,
            InsightDuplicateMemberResponse richest
    ) {
        StringBuilder signals = new StringBuilder();
        signals.append("Member count: %d. ".formatted(members.size()));
        signals.append("Richest member: '%s' (richness=%d). ".formatted(
                richest.title(), computeRichnessScore(richest)));
        signals.append("Source types: %s. ".formatted(
                members.stream()
                        .map(m -> m.sourceType() == null ? "none" : m.sourceType())
                        .distinct()
                        .toList()));
        signals.append("Insight types: %s. ".formatted(
                members.stream()
                        .map(m -> m.type().name())
                        .distinct()
                        .toList()));
        return signals.toString();
    }

    public record AgentAssessmentResult(
            MaintenanceAssessmentSemanticClassification semanticClassification,
            MaintenanceAssessmentConfidenceLevel confidenceLevel,
            MaintenanceAssessmentRecommendedAction recommendedAction,
            String rationale,
            String supportingSignals
    ) {
    }
}
