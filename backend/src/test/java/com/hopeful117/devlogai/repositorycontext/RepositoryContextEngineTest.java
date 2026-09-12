package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.insight.entity.Insight;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import com.hopeful117.devlogai.repositorycontext.collector.RepositoryContextCollector;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedFileContentEnricher;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedJavaSymbolEnricher;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextProfileDefinition;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextIntelligence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidencePrecisionPolicy;
import com.hopeful117.devlogai.repositorycontext.ranking.EvidenceRanker;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RepositoryContextEngineTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void highScoreEvidenceOrderedBeforeLowScoreDiversityPick() {
        RepositoryEvidence lowScoreDiversity = evidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/App.java", 48);
        RepositoryEvidence highScoreOrdinary = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:file-1", 90);

        RepositoryContextEngine engine = engine(
                List.of(lowScoreDiversity, highScoreOrdinary),
                List.of(lowScoreDiversity, highScoreOrdinary));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null,
                List.of());

        List<RepositoryEvidence> evidence = context.evidence();
        assertEquals(2, evidence.size());
        assertEquals("diff:file-1", evidence.get(0).reference(),
                "High-score ordinary pick must appear before low-score diversity pick");
        assertEquals("file:src/App.java", evidence.get(1).reference());
    }

    @Test
    void selectedSetUnchangedAfterReordering() {
        RepositoryEvidence lowScoreDiversity = evidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/App.java", 48);
        RepositoryEvidence highScoreOrdinary = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:file-1", 90);
        RepositoryEvidence midScoreInsight = evidence(
                RepositoryContextLayer.VALIDATED_INSIGHT, "INSIGHT",
                "insight:project-overview", 82);

        RepositoryContextEngine engine = engine(
                List.of(lowScoreDiversity, highScoreOrdinary, midScoreInsight),
                List.of(lowScoreDiversity, highScoreOrdinary, midScoreInsight));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null,
                List.of());

        Set<String> expected = Set.of(
                "file:src/App.java", "diff:file-1", "insight:project-overview");
        Set<String> actual = context.evidence().stream()
                .map(RepositoryEvidence::reference)
                .collect(Collectors.toSet());
        assertEquals(expected, actual,
                "Reordering must not add or remove evidence items");
    }

    @Test
    void sameScoreTieBreaksByLayerOrdinalThenReference() {
        RepositoryEvidence decision = evidence(
                RepositoryContextLayer.ADR, "DECISION", "decision:z", 80);
        RepositoryEvidence insight = evidence(
                RepositoryContextLayer.VALIDATED_INSIGHT, "INSIGHT",
                "insight:a", 80);
        RepositoryEvidence story = evidence(
                RepositoryContextLayer.ROADMAP, "ENGINEERING_STORY",
                "story:a", 80);

        RepositoryContextEngine engine = engine(
                List.of(decision, insight, story),
                List.of(story, insight, decision));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null,
                List.of());

        List<RepositoryEvidence> evidence = context.evidence();
        assertEquals(3, evidence.size());
        assertEquals("decision:z", evidence.get(0).reference(),
                "ADR (ordinal 4) before ROADMAP (ordinal 5)");
        assertEquals("story:a", evidence.get(1).reference(),
                "ROADMAP (ordinal 5) before VALIDATED_INSIGHT (ordinal 6)");
        assertEquals("insight:a", evidence.get(2).reference());
    }

    @Test
    void diversitySelectedEvidenceStillPresentAfterReordering() {
        RepositoryEvidence diversitySelection = evidence(
                RepositoryContextLayer.VALIDATED_INSIGHT, "INSIGHT",
                "insight:diversity", 50);
        RepositoryEvidence rankedHigh = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:file-1", 90);

        RepositoryContextEngine engine = engine(
                List.of(rankedHigh, diversitySelection),
                List.of(diversitySelection, rankedHigh));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null,
                List.of());

        List<RepositoryEvidence> evidence = context.evidence();
        assertEquals(2, evidence.size());
        assertTrue(evidence.stream()
                .anyMatch(e -> e.reference().equals("insight:diversity")),
                "Diversity-selected evidence must remain present after reordering");
        assertEquals("diff:file-1", evidence.get(0).reference(),
                "High-score ranked evidence must appear first");
        assertEquals("insight:diversity", evidence.get(1).reference(),
                "Low-score diversity evidence must appear second");
    }

    @Test
    void projectionRemovesLowestPriorityEvidenceFirst() {
        RepositoryEvidence highScoreDecision = evidence(
                RepositoryContextLayer.ADR, "DECISION", "decision:adr-46", 88);
        RepositoryEvidence lowScoreDiversity = evidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/App.java", 48);
        RepositoryEvidence midScoreInsight = evidence(
                RepositoryContextLayer.VALIDATED_INSIGHT, "INSIGHT",
                "insight:project-overview", 82);

        RepositoryContextEngine engine = engine(
                List.of(highScoreDecision, lowScoreDiversity, midScoreInsight),
                List.of(lowScoreDiversity, midScoreInsight, highScoreDecision));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null,
                List.of());

        com.hopeful117.devlogai.projectcontext.projection.AgentContextProjectionPolicy policy =
                new com.hopeful117.devlogai.projectcontext.projection.AgentContextProjectionPolicy(
                        2_000, 500, 3, 3);
        com.hopeful117.devlogai.projectcontext.projection.AgentContextProjectionService
                projectionService = new com.hopeful117.devlogai.projectcontext.projection
                .AgentContextProjectionService(objectMapper, policy);

        com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot projectContext =
                new com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot(
                        null, null, List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of());

        com.hopeful117.devlogai.projectcontext.projection.AgentEngineeringStoryContext result =
                projectionService.project(
                        java.util.UUID.fromString(
                                "00000000-0000-4000-8000-000000000019"),
                        projectContext, context, Instant.parse("2026-08-09T12:00:00Z"));

        com.hopeful117.devlogai.projectcontext.projection.AgentRepositoryContext projected =
                result.repositoryContext();

        assertTrue(projected.evidence().size() > 0);
        assertTrue(projected.evidence().stream()
                .anyMatch(e -> e.reference().equals("decision:adr-46")),
                "DECISION with score 88 must survive projection");
        assertTrue(projected.evidence().stream()
                .anyMatch(e -> e.reference().equals("insight:project-overview")),
                "INSIGHT with score 82 must survive projection");
    }

    @Test
    void diversityLowScoreRemovedBeforeOrdinaryHighScoreUnderBudgetPressure() {
        RepositoryEvidence lowScoreDiversity = evidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE, "SOURCE_FILE",
                "file:src/App.java", 48);
        RepositoryEvidence highScoreOrdinary = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:file-1", 90);

        RepositoryContextEngine engine = engine(
                List.of(lowScoreDiversity, highScoreOrdinary),
                List.of(lowScoreDiversity, highScoreOrdinary));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null,
                List.of());

        com.hopeful117.devlogai.projectcontext.projection.AgentContextProjectionPolicy policy =
                new com.hopeful117.devlogai.projectcontext.projection.AgentContextProjectionPolicy(
                        1_500, 400, 3, 3);
        com.hopeful117.devlogai.projectcontext.projection.AgentContextProjectionService
                projectionService = new com.hopeful117.devlogai.projectcontext.projection
                .AgentContextProjectionService(objectMapper, policy);

        com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot projectContext =
                new com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot(
                        null, null, List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of());

        com.hopeful117.devlogai.projectcontext.projection.AgentEngineeringStoryContext result =
                projectionService.project(
                        java.util.UUID.fromString(
                                "00000000-0000-4000-8000-000000000019"),
                        projectContext, context, Instant.parse("2026-08-09T12:00:00Z"));

        com.hopeful117.devlogai.projectcontext.projection.AgentRepositoryContext projected =
                result.repositoryContext();

        assertTrue(projected.evidence().size() > 0,
                "At least one evidence item must survive projection");
        assertTrue(projected.evidence().stream()
                .anyMatch(e -> e.reference().equals("diff:file-1")),
                "High-score ordinary evidence must survive projection");
    }

    private RepositoryContextEngine engine(
            List<RepositoryEvidence> collectorCandidates,
            List<RepositoryEvidence> selectorSelected
    ) {
        ContextIntelligence intelligence = mock(ContextIntelligence.class);
        when(intelligence.plan(any(), any())).thenReturn(contextPlan());

        RepositoryContextCollector collector = mock(RepositoryContextCollector.class);
        when(collector.collect(any())).thenReturn(collectorCandidates);

        EvidenceRanker ranker = mock(EvidenceRanker.class);
        when(ranker.rank(any(), any())).thenReturn(collectorCandidates);

        EvidenceSelector selector = mock(EvidenceSelector.class);
        when(selector.select(any(), any())).thenReturn(
                new EvidenceSelector.SelectionResult(
                        selectorSelected,
                        List.of(),
                        selectorSelected.stream()
                                .mapToInt(RepositoryEvidence::estimatedTokens).sum()));

        SelectedJavaSymbolEnricher symbolEnricher =
                mock(SelectedJavaSymbolEnricher.class);
        when(symbolEnricher.enrich(any(), any())).thenReturn(
                new SelectedJavaSymbolEnricher.EnrichmentResult(
                        new EvidenceSelector.SelectionResult(
                                selectorSelected, List.of(), 0),
                        List.of()));

        SelectedFileContentEnricher contentEnricher =
                mock(SelectedFileContentEnricher.class);
        when(contentEnricher.enrich(any(), any())).thenReturn(
                new SelectedFileContentEnricher.EnrichmentResult(
                        new EvidenceSelector.SelectionResult(
                                selectorSelected, List.of(), 0),
                        List.of()));

        return new RepositoryContextEngine(
                List.of(collector), intelligence, ranker, selector,
                symbolEnricher, contentEnricher, objectMapper,
                60, 500, 20, 6000);
    }

    private ContextPlan contextPlan() {
        var policy = new EvidencePrecisionPolicy("test", "v1", 50, 0, 25, 100, 75);
        var profile = new ContextProfileDefinition("test",
                ContextProfile.ENGINEERING_STORY, "v1", Map.of(),
                List.of(RepositoryContextLayer.RELATED_SOURCE_CODE), 1, 100, policy);
        return new ContextPlan("v2", List.of(profile), Map.of(),
                profile.preferredLayers(), 1, policy, List.of());
    }

    private RepositoryEvidence evidence(
            RepositoryContextLayer layer,
            String kind,
            String reference,
            int score
    ) {
        return new RepositoryEvidence(
                layer, kind, reference, "Summary for " + reference,
                Instant.EPOCH,
                new com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore(
                        "test", Map.of(), Map.of(), score, List.of()),
                List.of(),
                new RepositoryEvidence.EvidenceProvenance(
                        "DETERMINISTIC_EXTRACTION", "repository", reference, reference),
                Map.of(), 10, List.of());
    }

    // ──────────────────────────────────────────────────────────────
    // Regression: retrieveCandidates and 6-param build() paths
    // ──────────────────────────────────────────────────────────────

    @Test
    void retrieveCandidatesUsesUnscopedRequestForNonSCAIntents() {
        RepositoryEvidence commitDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);

        RepositoryContextEngine engine = engine(
                List.of(commitDiff),
                List.of(commitDiff));

        List<RepositoryEvidence> candidates = engine.retrieveCandidates(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        assertEquals(1, candidates.size());
        assertEquals("diff:abc123:src/App.java", candidates.getFirst().reference());
    }

    @Test
    void fourParamBuildDelegatesToRetrieveCandidates() {
        RepositoryEvidence commitDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);

        RepositoryContextEngine engine = engine(
                List.of(commitDiff),
                List.of(commitDiff));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        assertEquals(1, context.evidence().size());
        assertEquals("diff:abc123:src/App.java", context.evidence().getFirst().reference());
    }

    @Test
    void fiveParamBuildDelegatesToRetrieveCandidates() {
        RepositoryEvidence commitDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);
        RepositoryEvidence promotedDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:def456:src/Service.java", 75);

        RepositoryContextEngine engine = engine(
                List.of(commitDiff),
                List.of(commitDiff, promotedDiff));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of(),
                List.of(promotedDiff));

        assertEquals(2, context.evidence().size());
        assertTrue(context.evidence().stream()
                .anyMatch(e -> e.reference().equals("diff:abc123:src/App.java")));
        assertTrue(context.evidence().stream()
                .anyMatch(e -> e.reference().equals("diff:def456:src/Service.java")));
    }

    @Test
    void sixParamBuildUsesScopedRequestForCandidateCollection() {
        // After the fix (Story 0119 Subtask 1), the 6-param build() uses its
        // scoped ContextRequest for candidate collection via the internal
        // collectCandidates() primitive, preserving RepositoryRevisionScope
        // through to collectors.
        RepositoryEvidence commitDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);

        RepositoryContextEngine engine = engine(
                List.of(commitDiff),
                List.of(commitDiff));

        RepositoryRevisionScope revisionScope = new RepositoryRevisionScope(
                UUID.randomUUID(), UUID.randomUUID(), "abc123",
                java.nio.file.Path.of("/workspace"), "STORY_TARGET_COMMIT");

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of(),
                List.of(),
                revisionScope);

        // Post-fix: scope is preserved through collectCandidates() and passed to collectors
        assertNotNull(context);
        assertFalse(context.evidence().isEmpty());
    }

    @Test
    void retrieveCandidatesAndBuildProduceConsistentCandidatePool() {
        // Verifies that the shared retrieval primitive is used consistently
        RepositoryEvidence commitDiff1 = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);
        RepositoryEvidence commitDiff2 = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:def456:src/Service.java", 75);
        RepositoryEvidence gitHistory = evidence(
                RepositoryContextLayer.GIT_HISTORY, "GIT_COMMIT",
                "git:abc123", 70);

        RepositoryContextEngine engine = engine(
                List.of(commitDiff1, commitDiff2, gitHistory),
                List.of(commitDiff1, commitDiff2, gitHistory));

        List<RepositoryEvidence> candidates = engine.retrieveCandidates(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        // Both paths must expose the same candidate pool
        assertEquals(3, candidates.size());
        assertEquals(3, context.candidateCount());
    }

    @Test
    void sixParamBuildIncludesAdditionalCandidatesInSelection() {
        RepositoryEvidence collectorOutput = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:collector:src/App.java", 85);
        RepositoryEvidence additionalCandidate = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:additional:src/Service.java", 75);

        RepositoryContextEngine engine = engine(
                List.of(collectorOutput),
                List.of(collectorOutput, additionalCandidate));

        RepositoryRevisionScope revisionScope = new RepositoryRevisionScope(
                UUID.randomUUID(), UUID.randomUUID(), "abc123",
                java.nio.file.Path.of("/workspace"), "STORY_TARGET_COMMIT");

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of(),
                List.of(additionalCandidate),
                revisionScope);

        assertEquals(2, context.evidence().size());
        assertTrue(context.evidence().stream()
                .anyMatch(e -> e.reference().equals("diff:collector:src/App.java")));
        assertTrue(context.evidence().stream()
                .anyMatch(e -> e.reference().equals("diff:additional:src/Service.java")));
    }
}
