package com.hopeful117.devlogai.repositorycontext.intelligence;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DeterministicContextIntelligenceTest {
    private final DeterministicContextIntelligence intelligence =
            new DeterministicContextIntelligence();

    @Test
    void composesVersionedProfilesReferencedByIntent() {
        ContextPlan plan = intelligence.plan(context(), intent(
                List.of("architecture-v1", "history-v1")));

        assertEquals("context-intelligence-v1", plan.planVersion());
        assertEquals(List.of("architecture-v1", "history-v1"), plan.profileKeys());
        assertEquals(ContextProfile.ARCHITECTURE_REVIEW, plan.primaryProfile());
        assertEquals(18, plan.composedWeights().get(
                EvidenceCriterion.SEMANTIC_RELEVANCE));
        assertEquals(23, plan.composedWeights().get(
                EvidenceCriterion.ARCHITECTURAL_RELEVANCE));
        assertEquals(23, plan.composedWeights().get(
                EvidenceCriterion.HISTORICAL_RELEVANCE));
        assertEquals(3, plan.minimumDiverseLayers());
    }

    @Test
    void rejectsUnknownProfileAndUsesDeterministicFallbackForLegacyIntent() {
        assertThrows(IllegalArgumentException.class,
                () -> intelligence.plan(context(), intent(List.of("unknown-v1"))));

        ContextPlan fallback = intelligence.plan(context(), intent(List.of()));

        assertEquals(List.of("architecture-v1", "history-v1"),
                fallback.profileKeys());
    }

    private IntentDefinition intent(List<String> profiles) {
        return new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "template-v1", profiles);
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
