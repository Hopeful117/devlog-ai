package com.hopeful117.devlogai.contextmaintenance.agent;

import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterCategory;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateClusterResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateMemberResponse;
import com.hopeful117.devlogai.insight.dto.response.InsightDuplicateRecommendation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateAmbiguityResolutionAgentTest {

    private final DuplicateAmbiguityResolutionAgent agent = new DuplicateAmbiguityResolutionAgent();

    @Test
    void shouldReturnEmptyForNonAmbiguousFindingType() {
        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE,
                buildMembers("same-source", "same-source")
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_EXACT_DUPLICATE", cluster);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForNullCluster() {
        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE", null);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyForInsufficientMembers() {
        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE,
                List.of(buildMember("source-a"))
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE", cluster);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldClassifySameFamilySemanticDuplicateAsLikelyDuplicate() {
        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE,
                buildMembers("architecture-docs", "architecture-docs")
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE", cluster);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.HIGH, result.get().confidenceLevel());
        assertEquals(MaintenanceAssessmentRecommendedAction.RESOLVE, result.get().recommendedAction());
    }

    @Test
    void shouldClassifyCrossFamilySemanticDuplicateAsUncertain() {
        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE,
                buildMembers("architecture-docs", "technology-docs")
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE", cluster);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.MEDIUM, result.get().confidenceLevel());
        assertEquals(MaintenanceAssessmentRecommendedAction.ESCALATE, result.get().recommendedAction());
    }

    @Test
    void shouldClassifyRicherSuccessorWithHighDeltaAsEnrichment() {
        InsightDuplicateMemberResponse rich = buildMemberWithContent(
                "architecture-docs", "Rich title", "Detailed content with many words. ".repeat(10), "Comprehensive rationale"
        );
        InsightDuplicateMemberResponse poor = buildMemberWithContent(
                "architecture-docs", "Poor title", "Short", null
        );

        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_RICHER_SUCCESSOR,
                List.of(rich, poor)
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_OVERLAP_REVIEW", cluster);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.LIKELY_ENRICHMENT,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.HIGH, result.get().confidenceLevel());
        assertEquals(MaintenanceAssessmentRecommendedAction.RESOLVE, result.get().recommendedAction());
    }

    @Test
    void shouldClassifyMarginalRichnessDifferenceAsUncertain() {
        InsightDuplicateMemberResponse member1 = buildMember("architecture-docs");
        InsightDuplicateMemberResponse member2 = buildMember("architecture-docs");

        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_RICHER_SUCCESSOR,
                List.of(member1, member2)
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_OVERLAP_REVIEW", cluster);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.MEDIUM, result.get().confidenceLevel());
        assertEquals(MaintenanceAssessmentRecommendedAction.ESCALATE, result.get().recommendedAction());
    }

    @Test
    void shouldClassifyReviewRequiredAsUncertain() {
        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.REVIEW_REQUIRED,
                buildMembers("architecture-docs", "technology-docs")
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_OVERLAP_REVIEW", cluster);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.UNCERTAIN,
                result.get().semanticClassification());
        assertEquals(MaintenanceAssessmentConfidenceLevel.MEDIUM, result.get().confidenceLevel());
        assertEquals(MaintenanceAssessmentRecommendedAction.ESCALATE, result.get().recommendedAction());
    }

    @Test
    void shouldReturnEmptyForLowConfidenceAssessment() {
        InsightDuplicateMemberResponse member1 = buildMemberWithContent(
                "source-a", "Title A", "Content A", null
        );
        InsightDuplicateMemberResponse member2 = buildMemberWithContent(
                "source-b", "Title B", "Content B", null
        );

        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE,
                List.of(member1, member2)
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE", cluster);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentConfidenceLevel.MEDIUM, result.get().confidenceLevel());
    }

    @Test
    void shouldHandleOverlapReviewFindingType() {
        InsightDuplicateClusterResponse cluster = buildCluster(
                InsightDuplicateClusterCategory.LIKELY_SEMANTIC_DUPLICATE,
                buildMembers("same-source", "same-source")
        );

        Optional<DuplicateAmbiguityResolutionAgent.AgentAssessmentResult> result =
                agent.evaluate("TRUSTED_KNOWLEDGE_OVERLAP_REVIEW", cluster);

        assertTrue(result.isPresent());
        assertEquals(MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                result.get().semanticClassification());
    }

    private InsightDuplicateClusterResponse buildCluster(
            InsightDuplicateClusterCategory category,
            List<InsightDuplicateMemberResponse> members
    ) {
        return new InsightDuplicateClusterResponse(
                "test-cluster::" + category.name(),
                category,
                InsightDuplicateRecommendation.REVIEW_MANUALLY,
                "Test rationale for " + category.name(),
                members
        );
    }

    private List<InsightDuplicateMemberResponse> buildMembers(String sourceType1, String sourceType2) {
        return List.of(buildMember(sourceType1), buildMember(sourceType2));
    }

    private InsightDuplicateMemberResponse buildMember(String sourceType) {
        return buildMemberWithContent(sourceType, "Test Title", "Test content", "Test rationale");
    }

    private InsightDuplicateMemberResponse buildMemberWithContent(
            String sourceType,
            String title,
            String content,
            String rationale
    ) {
        return new InsightDuplicateMemberResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                InsightType.ARCHITECTURAL,
                InsightSeverity.WARNING,
                sourceType,
                title,
                content,
                rationale,
                new BigDecimal("0.8"),
                2,
                Instant.now()
        );
    }
}
