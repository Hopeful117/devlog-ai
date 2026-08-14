package com.hopeful117.devlogai.contextmaintenance.agent;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFinding;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CrossSurfacePatternDetectionAgentTest {

    private final CrossSurfacePatternDetectionAgent agent = new CrossSurfacePatternDetectionAgent();

    @Test
    void shouldReturnEmptyForNullFindings() {
        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForEmptyFindings() {
        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForSingleStaleFinding() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldDetectCorrelatedStalenessAcrossTwoSurfaces() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING),
                buildFinding(MaintenanceContextSurface.PROJECT_PROJECTION,
                        MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.CORRELATED_STALENESS,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.MEDIUM, result.get().confidenceLevel());
        assertEquals(MaintenanceAssessmentRecommendedAction.ESCALATE, result.get().recommendedAction());
        assertEquals(2, result.get().contributingFindingIds().size());
    }

    @Test
    void shouldDetectCorrelatedStalenessAcrossThreeSurfaces() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING),
                buildFinding(MaintenanceContextSurface.PROJECT_PROJECTION,
                        MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH),
                buildFinding(MaintenanceContextSurface.INTERNAL_HUMAN_CONTEXT,
                        MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.CORRELATED_STALENESS,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.HIGH, result.get().confidenceLevel());
        assertEquals(3, result.get().contributingFindingIds().size());
    }

    @Test
    void shouldNotDetectPatternForSameSurfaceFindings() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING),
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldDetectCorrelatedDuplicateDebt() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE),
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.CORRELATED_STALENESS,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.MEDIUM, result.get().confidenceLevel());
        assertEquals(2, result.get().contributingFindingIds().size());
    }

    @Test
    void shouldNotConsiderResolvedFindings() {
        List<MaintenanceFinding> findings = List.of(
                buildFindingWithStatus(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                        MaintenanceFindingStatus.RESOLVED),
                buildFinding(MaintenanceContextSurface.PROJECT_PROJECTION,
                        MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldPrioritizeStalenessOverDuplicatePattern() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING),
                buildFinding(MaintenanceContextSurface.PROJECT_PROJECTION,
                        MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH),
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE),
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_OVERLAP_REVIEW)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.CORRELATED_STALENESS,
                result.get().semanticClassification());
        assertEquals(2, result.get().contributingFindingIds().size());
    }

    @Test
    void shouldReturnEmptyForSingleDuplicateFinding() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForAcknowledgedFindings() {
        List<MaintenanceFinding> findings = List.of(
                buildFindingWithStatus(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING,
                        MaintenanceFindingStatus.ACKNOWLEDGED),
                buildFinding(MaintenanceContextSurface.PROJECT_PROJECTION,
                        MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.CORRELATED_STALENESS,
                result.get().semanticClassification());
    }

    @Test
    void shouldDetectPatternOnlyWithOpenFindings() {
        List<MaintenanceFinding> findings = List.of(
                buildFinding(MaintenanceContextSurface.PROJECT_UNDERSTANDING,
                        MaintenanceFindingIssueType.STALE_PROJECT_UNDERSTANDING),
                buildFinding(MaintenanceContextSurface.PROJECT_PROJECTION,
                        MaintenanceFindingIssueType.MISSING_PROJECTION_REFRESH),
                buildFindingWithStatus(MaintenanceContextSurface.INTERNAL_HUMAN_CONTEXT,
                        MaintenanceFindingIssueType.STALE_HUMAN_CONTEXT_INPUT,
                        MaintenanceFindingStatus.DISMISSED)
        );

        Optional<CrossSurfacePatternDetectionAgent.AgentAssessmentResult> result = agent.evaluate(findings);

        assertTrue(result.isPresent());
        assertEquals(2, result.get().contributingFindingIds().size());
    }

    private MaintenanceFinding buildFinding(MaintenanceContextSurface surface, MaintenanceFindingIssueType issueType) {
        return buildFindingWithStatus(surface, issueType, MaintenanceFindingStatus.OPEN);
    }

    private MaintenanceFinding buildFindingWithStatus(
            MaintenanceContextSurface surface,
            MaintenanceFindingIssueType issueType,
            MaintenanceFindingStatus status
    ) {
        return MaintenanceFinding.builder()
                .id(UUID.randomUUID())
                .project(com.hopeful117.devlogai.project.entity.Project.builder()
                        .id(UUID.randomUUID())
                        .build())
                .contextSurface(surface)
                .issueType(issueType)
                .severity(MaintenanceFindingSeverity.MEDIUM)
                .status(status)
                .suggestedAction(MaintenanceSuggestedActionCategory.REVIEW)
                .humanReviewRequired(true)
                .summary("Test finding for " + issueType.name())
                .details("Test details")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}
