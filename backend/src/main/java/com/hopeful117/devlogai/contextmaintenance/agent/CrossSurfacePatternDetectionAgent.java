package com.hopeful117.devlogai.contextmaintenance.agent;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class CrossSurfacePatternDetectionAgent {

    private static final int MIN_SURFACES_FOR_CORRELATED_STALENESS = 2;
    private static final int MIN_DUPLICATE_FINDINGS_FOR_PATTERN = 2;

    public Optional<AgentAssessmentResult> evaluate(List<MaintenanceFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return Optional.empty();
        }

        List<MaintenanceFinding> activeFindings = findings.stream()
                .filter(f -> f.getStatus() == MaintenanceFindingStatus.OPEN
                        || f.getStatus() == MaintenanceFindingStatus.ACKNOWLEDGED)
                .toList();

        if (activeFindings.isEmpty()) {
            return Optional.empty();
        }

        Optional<AgentAssessmentResult> stalenessPattern = detectCorrelatedStaleness(activeFindings);
        if (stalenessPattern.isPresent()) {
            return stalenessPattern;
        }

        Optional<AgentAssessmentResult> duplicatePattern = detectCorrelatedDuplicateDebt(activeFindings);
        if (duplicatePattern.isPresent()) {
            return duplicatePattern;
        }

        return Optional.empty();
    }

    private Optional<AgentAssessmentResult> detectCorrelatedStaleness(List<MaintenanceFinding> findings) {
        Set<MaintenanceContextSurface> staleSurfaces = findStaleSurfaces(findings);

        if (staleSurfaces.size() < MIN_SURFACES_FOR_CORRELATED_STALENESS) {
            return Optional.empty();
        }

        List<MaintenanceFinding> contributingFindings = findings.stream()
                .filter(f -> isStalenessFinding(f) && staleSurfaces.contains(f.getContextSurface()))
                .toList();

        MaintenanceAssessmentConfidenceLevel confidence = computeStalenessConfidence(staleSurfaces.size());
        if (isLowConfidence(confidence)) {
            return Optional.empty();
        }

        String rationale = buildStalenessRationale(staleSurfaces, contributingFindings);
        String supportingSignals = buildSupportingSignals(contributingFindings);

        return Optional.of(new AgentAssessmentResult(
                MaintenanceAssessmentSemanticClassification.CORRELATED_STALENESS,
                confidence,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                rationale,
                supportingSignals,
                contributingFindings.stream().map(MaintenanceFinding::getId).toList()
        ));
    }

    private Optional<AgentAssessmentResult> detectCorrelatedDuplicateDebt(List<MaintenanceFinding> findings) {
        List<MaintenanceFinding> duplicateFindings = findings.stream()
                .filter(f -> isDuplicateDebtFinding(f))
                .toList();

        if (duplicateFindings.size() < MIN_DUPLICATE_FINDINGS_FOR_PATTERN) {
            return Optional.empty();
        }

        Set<MaintenanceContextSurface> surfaces = duplicateFindings.stream()
                .map(MaintenanceFinding::getContextSurface)
                .collect(Collectors.toSet());

        MaintenanceAssessmentConfidenceLevel confidence = computeDuplicateConfidence(duplicateFindings.size());
        if (isLowConfidence(confidence)) {
            return Optional.empty();
        }

        String rationale = buildDuplicateRationale(duplicateFindings, surfaces);
        String supportingSignals = buildSupportingSignals(duplicateFindings);

        return Optional.of(new AgentAssessmentResult(
                MaintenanceAssessmentSemanticClassification.CORRELATED_STALENESS,
                confidence,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                rationale,
                supportingSignals,
                duplicateFindings.stream().map(MaintenanceFinding::getId).toList()
        ));
    }

    private Set<MaintenanceContextSurface> findStaleSurfaces(List<MaintenanceFinding> findings) {
        return findings.stream()
                .filter(this::isStalenessFinding)
                .map(MaintenanceFinding::getContextSurface)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isStalenessFinding(MaintenanceFinding finding) {
        return finding.getIssueType() == MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING
                || finding.getIssueType() == MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH
                || finding.getIssueType() == MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT;
    }

    private boolean isDuplicateDebtFinding(MaintenanceFinding finding) {
        return finding.getIssueType() == MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_EXACT_DUPLICATE
                || finding.getIssueType() == MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE
                || finding.getIssueType() == MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW;
    }

    private MaintenanceAssessmentConfidenceLevel computeStalenessConfidence(int surfaceCount) {
        if (surfaceCount >= 3) {
            return MaintenanceAssessmentConfidenceLevel.HIGH;
        }
        if (surfaceCount == 2) {
            return MaintenanceAssessmentConfidenceLevel.MEDIUM;
        }
        return MaintenanceAssessmentConfidenceLevel.LOW;
    }

    private MaintenanceAssessmentConfidenceLevel computeDuplicateConfidence(int findingCount) {
        if (findingCount >= 3) {
            return MaintenanceAssessmentConfidenceLevel.HIGH;
        }
        if (findingCount == 2) {
            return MaintenanceAssessmentConfidenceLevel.MEDIUM;
        }
        return MaintenanceAssessmentConfidenceLevel.LOW;
    }

    private boolean isLowConfidence(MaintenanceAssessmentConfidenceLevel level) {
        return level == MaintenanceAssessmentConfidenceLevel.LOW
                || level == MaintenanceAssessmentConfidenceLevel.VERY_LOW;
    }

    private String buildStalenessRationale(
            Set<MaintenanceContextSurface> staleSurfaces,
            List<MaintenanceFinding> contributingFindings
    ) {
        String surfaceNames = staleSurfaces.stream()
                .map(MaintenanceContextSurface::name)
                .collect(Collectors.joining(", "));
        String findingSummaries = contributingFindings.stream()
                .map(f -> "- %s: %s".formatted(f.getIssueType(), f.getSummary()))
                .collect(Collectors.joining("\n"));

        return """
                Correlated staleness detected across %d context surfaces: [%s].
                Multiple maintenance signals suggest broader context degradation:
                %s
                These findings together indicate a systemic staleness pattern that warrants priority review."""
                .formatted(staleSurfaces.size(), surfaceNames, findingSummaries);
    }

    private String buildDuplicateRationale(
            List<MaintenanceFinding> duplicateFindings,
            Set<MaintenanceContextSurface> surfaces
    ) {
        String findingSummaries = duplicateFindings.stream()
                .map(f -> "- %s: %s".formatted(f.getIssueType(), f.getSummary()))
                .collect(Collectors.joining("\n"));

        return """
                Correlated duplicate debt detected across %d finding(s) on surface(s) [%s].
                Multiple duplicate-related findings suggest a systemic knowledge quality issue:
                %s
                These findings together indicate a pattern that warrants priority review."""
                .formatted(
                        duplicateFindings.size(),
                        surfaces.stream().map(MaintenanceContextSurface::name).collect(Collectors.joining(", ")),
                        findingSummaries
                );
    }

    private String buildSupportingSignals(List<MaintenanceFinding> contributingFindings) {
        StringBuilder signals = new StringBuilder();
        signals.append("Contributing finding count: %d. ".formatted(contributingFindings.size()));
        signals.append("Surfaces: %s. ".formatted(
                contributingFindings.stream()
                        .map(f -> f.getContextSurface().name())
                        .distinct()
                        .collect(Collectors.joining(", "))));
        signals.append("Issue types: %s. ".formatted(
                contributingFindings.stream()
                        .map(f -> f.getIssueType().name())
                        .distinct()
                        .collect(Collectors.joining(", "))));
        signals.append("Finding IDs: %s".formatted(
                contributingFindings.stream()
                        .map(f -> f.getId().toString())
                        .collect(Collectors.joining(", "))));
        return signals.toString();
    }

    public record AgentAssessmentResult(
            MaintenanceAssessmentSemanticClassification semanticClassification,
            MaintenanceAssessmentConfidenceLevel confidenceLevel,
            MaintenanceAssessmentRecommendedAction recommendedAction,
            String rationale,
            String supportingSignals,
            List<UUID> contributingFindingIds
    ) {
    }
}
