package com.hopeful117.devlogai.repositorycontext.intelligence;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DeterministicContextIntelligenceTest {
    private final DeterministicContextIntelligence intelligence =
            new DeterministicContextIntelligence();

    @Test
    void composesVersionedProfilesReferencedByIntent() {
        ContextPlan plan = intelligence.plan(context(), intent(
                List.of("architecture-v1", "history-v1")));

        assertEquals("context-intelligence-v2", plan.planVersion());
        assertEquals(List.of("architecture-v1", "history-v1"), plan.profileKeys());
        assertEquals(ContextProfile.ARCHITECTURE_REVIEW, plan.primaryProfile());
        assertEquals(18, plan.composedWeights().get(
                EvidenceCriterion.SEMANTIC_RELEVANCE));
        assertEquals(23, plan.composedWeights().get(
                EvidenceCriterion.ARCHITECTURAL_RELEVANCE));
        assertEquals(23, plan.composedWeights().get(
                EvidenceCriterion.HISTORICAL_RELEVANCE));
        assertEquals(3, plan.minimumDiverseLayers());
        assertEquals(EvidencePrecisionPolicy.UNRESTRICTED.maximumKindSharePercentage(),
                plan.precisionPolicy().maximumKindSharePercentage());
    }

    @Test
    void rejectsUnknownProfileAndUsesDeterministicFallbackForLegacyIntent() {
        AnalysisContext analysisContext = context();
        IntentDefinition unknownProfileIntent = intent(List.of("unknown-v1"));
        assertThrows(IllegalArgumentException.class,
                () -> intelligence.plan(analysisContext, unknownProfileIntent));

        ContextPlan fallback = intelligence.plan(context(), intent(List.of()));

        assertEquals(List.of("architecture-v1", "history-v1"),
                fallback.profileKeys());
    }

    private IntentDefinition intent(List<String> profiles) {
        return new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "template-v1", profiles);
    }

    @Test
    void resolvesEngineeringStoryProfile() {
        ContextPlan plan = intelligence.plan(context(), intent(
                List.of("engineering-story-v1")));

        assertEquals(List.of("engineering-story-v1"), plan.profileKeys());
        assertEquals(ContextProfile.ENGINEERING_STORY, plan.primaryProfile());
        assertEquals(15, plan.composedWeights().get(
                EvidenceCriterion.SEMANTIC_RELEVANCE));
        assertEquals(15, plan.composedWeights().get(
                EvidenceCriterion.ARCHITECTURAL_RELEVANCE));
        assertEquals(25, plan.composedWeights().get(
                EvidenceCriterion.HISTORICAL_RELEVANCE));
        assertEquals(20, plan.composedWeights().get(
                EvidenceCriterion.RECENCY));
        assertEquals(20, plan.composedWeights().get(
                EvidenceCriterion.CONFIDENCE));
        assertEquals(5, plan.composedWeights().get(
                EvidenceCriterion.USER_GUIDANCE_BOOST));
        assertEquals(3, plan.minimumDiverseLayers());
        assertEquals("engineering-story-precision:v1",
                plan.precisionPolicy().key());
        assertEquals(25, plan.precisionPolicy().maximumKindSharePercentage());
        assertEquals(35, plan.precisionPolicy().minimumRelevanceScore());
        assertTrue(plan.explanations().stream().anyMatch(
                value -> value.startsWith("PRECISION_POLICY:")));
        assertTrue(plan.preferredLayers().contains(
                RepositoryContextLayer.GIT_HISTORY));
        assertTrue(plan.preferredLayers().contains(
                RepositoryContextLayer.COMMIT_DIFF));
        assertTrue(plan.preferredLayers().contains(
                RepositoryContextLayer.ADR));
        assertTrue(plan.preferredLayers().contains(
                RepositoryContextLayer.PROJECT_DOCUMENTATION));
        assertTrue(plan.preferredLayers().contains(
                RepositoryContextLayer.ROADMAP));
    }

    private AnalysisContext context() {
        return new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(UUID.randomUUID(), "Project", "project",
                        null, ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(UUID.randomUUID(),
                        AnalysisType.ARCHITECTURE_REVIEW,
                        "architecture-overview", "v1", AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                mock(com.hopeful117.devlogai.profile.dto.ProjectProfileResponse.class),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
    }
}
