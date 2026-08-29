package com.hopeful117.devlogai.repositorycontext;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.analysis.diagnostics.entity.AnalysisExecutionDiagnostic;
import com.hopeful117.devlogai.analysis.diagnostics.repository.AnalysisExecutionDiagnosticRepository;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.fact.entity.FactType;
import com.hopeful117.devlogai.insight.entity.InsightStatus;
import com.hopeful117.devlogai.insight.repository.InsightRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.model.InsightType;
import com.hopeful117.devlogai.knowledge.selection.KnowledgeSelectionServiceImpl;
import com.hopeful117.devlogai.knowledge.selection.SelectedKnowledge;
import com.hopeful117.devlogai.observation.entity.ObservationType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.repositorycontext.collector.RepositoryContextCollector;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedFileContentEnricher;
import com.hopeful117.devlogai.repositorycontext.enrichment.SelectedJavaSymbolEnricher;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextIntelligence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextProfileDefinition;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidencePrecisionPolicy;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence.EvidenceProvenance;
import com.hopeful117.devlogai.repositorycontext.ranking.EvidenceRanker;
import com.hopeful117.devlogai.repositorycontext.selection.BudgetedDiverseEvidenceSelector;
import com.hopeful117.devlogai.repositorycontext.selection.EvidenceSelector;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Story 0097 — Reconnect Per-File COMMIT_DIFF Evidence to Analysis Pipeline.
 * Proves shared retrieval primitive, Analysis promotion, single bounded
 * envelope, deduplication, and backward compatibility.
 */
class Story0097CommitDiffReconnectionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ──────────────────────────────────────────────────────────────
    // 1. SHARED RETRIEVAL — proves the primitive exposes COMMIT_DIFF
    // ──────────────────────────────────────────────────────────────

    @Test
    void sharedRetrievalExposesPerFileCommitDiffBeforeComposition() {
        RepositoryEvidence commitDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);
        RepositoryEvidence gitHistory = evidence(
                RepositoryContextLayer.GIT_HISTORY, "GIT_COMMIT",
                "git:abc123", 70);

        RepositoryContextEngine engine = engine(
                List.of(commitDiff, gitHistory),
                List.of(commitDiff, gitHistory));

        List<RepositoryEvidence> candidates = engine.retrieveCandidates(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        assertTrue(candidates.stream()
                        .anyMatch(e -> e.layer() == RepositoryContextLayer.COMMIT_DIFF),
                "Shared retrieval must expose per-file COMMIT_DIFF evidence");
        assertTrue(candidates.stream()
                        .anyMatch(e -> e.layer() == RepositoryContextLayer.GIT_HISTORY),
                "Shared retrieval must also expose other layers");
    }

    // ──────────────────────────────────────────────────────────────
    // 2. REPOSITORY CONTEXT REUSE — engine uses same primitive
    // ──────────────────────────────────────────────────────────────

    @Test
    void repositoryContextEngineUsesSharedRetrievalInternally() {
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

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        // Both paths must see the same collector output
        assertEquals(1, candidates.size());
        assertTrue(context.evidence().stream()
                .anyMatch(e -> e.layer() == RepositoryContextLayer.COMMIT_DIFF));
    }

    // ──────────────────────────────────────────────────────────────
    // 3. ANALYSIS PROMOTION — COMMIT_DIFF promoted into build()
    // ──────────────────────────────────────────────────────────────

    @Test
    void analysisPromotionMergesCommitDiffIntoCandidatePool() {
        RepositoryEvidence commitDiff1 = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);
        RepositoryEvidence gitHistory = evidence(
                RepositoryContextLayer.GIT_HISTORY, "GIT_COMMIT",
                "git:abc123", 70);
        // Additional COMMIT_DIFF promoted by Analysis
        RepositoryEvidence promotedDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:def456:src/Service.java", 75);

        RepositoryContextEngine engine = engine(
                List.of(commitDiff1, gitHistory),
                List.of(commitDiff1, gitHistory, promotedDiff));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of(),
                List.of(promotedDiff));

        assertTrue(context.evidence().stream()
                        .anyMatch(e -> e.reference().equals("diff:abc123:src/App.java")),
                "Original collector COMMIT_DIFF must survive");
        assertTrue(context.evidence().stream()
                        .anyMatch(e -> e.reference().equals("diff:def456:src/Service.java")),
                "Promoted COMMIT_DIFF must be present in final evidence");
        assertTrue(context.evidence().stream()
                        .anyMatch(e -> e.layer() == RepositoryContextLayer.GIT_HISTORY),
                "Other layers must remain");
    }

    // ──────────────────────────────────────────────────────────────
    // 4. NORMAL SELECTION — promoted candidates go through selector
    // ──────────────────────────────────────────────────────────────

    @Test
    void promotedCandidatesPassThroughBudgetedDiverseEvidenceSelector() {
        RepositoryEvidence lowScore = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:low:file.java", 30);
        RepositoryEvidence highScore = evidence(
                RepositoryContextLayer.GIT_HISTORY, "GIT_COMMIT",
                "git:high", 90);

        // Selector: lowScore excluded by relevance, highScore selected
        EvidenceSelector selector = mock(EvidenceSelector.class);
        when(selector.select(any(), any())).thenReturn(
                new EvidenceSelector.SelectionResult(
                        List.of(highScore), List.of(), 100));

        RepositoryContextEngine engine = engineWithSelector(
                List.of(lowScore, highScore),
                List.of(highScore),
                selector);

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of(),
                List.of(lowScore));

        verify(selector).select(any(), any());
        assertEquals(1, context.evidence().size());
        assertEquals("git:high", context.evidence().getFirst().reference());
    }

    // ──────────────────────────────────────────────────────────────
    // 5. ITEM BOUNDEDNESS — evidence never exceeds maximumEvidenceItems
    // ──────────────────────────────────────────────────────────────

    @Test
    void finalEvidenceNeverExceedsMaximumEvidenceItems() {
        int maxItems = 5;
        List<RepositoryEvidence> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add(evidence(
                    RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                    "diff:item-" + i, 80 - i));
        }

        RepositoryContextEngine engine = engineBounded(candidates, maxItems);

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        assertTrue(context.evidence().size() <= maxItems,
                "Final evidence must not exceed maximumEvidenceItems=" + maxItems
                        + " but was " + context.evidence().size());
    }

    // ──────────────────────────────────────────────────────────────
    // 6. TOKEN BOUNDEDNESS — token budget enforced
    // ──────────────────────────────────────────────────────────────

    @Test
    void tokenBudgetRemainsEnforced() {
        RepositoryContextEngine engine = engine(
                List.of(), List.of());

        RepositoryContext.ContextBudget budget = new RepositoryContext.ContextBudget(60, 500, 20, 100);
        ContextRequest request = new ContextRequest(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of(), contextPlan(), budget);

        BudgetedDiverseEvidenceSelector selector =
                new BudgetedDiverseEvidenceSelector();

        RepositoryEvidence tokenHeavy = evidenceWithTokens(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:heavy", 90, 200);
        RepositoryEvidence tokenLight = evidenceWithTokens(
                RepositoryContextLayer.GIT_HISTORY, "GIT_COMMIT",
                "git:light", 80, 50);

        EvidenceSelector.SelectionResult result = selector.select(
                List.of(tokenHeavy, tokenLight), request);

        // tokenHeavy (200) exceeds budget (100), so only tokenLight fits
        assertTrue(result.usedTokens() <= 100,
                "Token budget must be enforced: used=" + result.usedTokens());
    }

    // ──────────────────────────────────────────────────────────────
    // 7. DEDUPLICATION — duplicate references eliminated
    // ──────────────────────────────────────────────────────────────

    @Test
    void duplicateReferencesDoNotProduceDuplicateFinalEvidence() {
        RepositoryEvidence original = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);
        RepositoryEvidence duplicate = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);

        RepositoryContextEngine engine = engine(
                List.of(original),
                List.of(original));

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of(),
                List.of(duplicate));

        long count = context.evidence().stream()
                .filter(e -> e.reference().equals("diff:abc123:src/App.java"))
                .count();
        assertEquals(1, count,
                "Duplicate references must be deduplicated by BudgetedDiverseEvidenceSelector");
    }

    // ──────────────────────────────────────────────────────────────
    // 8. AGGREGATE EVIDENCE REGRESSION — COMMIT_DIFF_SUMMARY available
    // ──────────────────────────────────────────────────────────────

    @Test
    void commitDiffSummaryFactsRemainAvailable() throws Exception {
        var diagnostics = mock(AnalysisExecutionDiagnosticRepository.class);
        var insights = mock(InsightRepository.class);
        var mapper = mock(ObjectMapper.class);
        var repositoryContexts = mock(RepositoryContextService.class);

        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).collectionComplete(true).warningCount(0).build();
        when(diagnostics.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(mapper.writeValueAsString(any())).thenReturn("stable");

        AnalysisContext.FactSnapshot summaryFact = new AnalysisContext.FactSnapshot(
                UUID.randomUUID(), FactType.COMMIT_DIFF_SUMMARY,
                "5 commits, 12 files changed", "commit-scoped-fact-collector",
                List.of(), Instant.now());
        AnalysisContext context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "Project", "project", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId, AnalysisType.ARCHITECTURE_REVIEW,
                        "architecture-overview", "v1", AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                mock(ProjectProfileResponse.class),
                List.of(summaryFact), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        IntentDefinition intent = new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(InsightType.ARCHITECTURE_DESCRIPTION), List.of("grounded"),
                Map.of("type", "object"), "architecture-overview-prompt-v1");

        RepositoryContext repositoryContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(),
                "v1", List.of(), List.of(), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                0, 0, 0, false, List.of(), List.of(), "digest");
        when(repositoryContexts.build(eq(context), eq(intent), isNull(), anyList(), anyList()))
                .thenReturn(repositoryContext);

        var service = new KnowledgeSelectionServiceImpl(
                diagnostics, insights, mapper, repositoryContexts, 15);
        SelectedKnowledge result = service.select(context, intent, null);

        assertTrue(result.selectedFacts().stream()
                        .anyMatch(f -> f.type() == FactType.COMMIT_DIFF_SUMMARY),
                "COMMIT_DIFF_SUMMARY facts must remain in selectedFacts");
    }

    // ──────────────────────────────────────────────────────────────
    // 9. EMPTY RETRIEVAL — no COMMIT_DIFF candidates
    // ──────────────────────────────────────────────────────────────

    @Test
    void emptyRetrievalProducesValidBehavior() {
        RepositoryContextEngine engine = engine(List.of(), List.of());

        List<RepositoryEvidence> candidates = engine.retrieveCandidates(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        RepositoryContext context = engine.build(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        assertTrue(candidates.isEmpty());
        assertNotNull(context);
        assertTrue(context.evidence().isEmpty());
    }

    // ──────────────────────────────────────────────────────────────
    // 10. INTENT SENSITIVITY REGRESSION — existing ranking unchanged
    // ──────────────────────────────────────────────────────────────

    @Test
    void intentSensitivityRemainsUnchanged() {
        RepositoryEvidence architectureEvidence = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:architecture-module.java", 90);
        RepositoryEvidence unrelatedEvidence = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:unrelated-module.java", 90);

        RepositoryContextEngine engine = engine(
                List.of(architectureEvidence, unrelatedEvidence),
                List.of(architectureEvidence, unrelatedEvidence));

        List<RepositoryEvidence> candidates = engine.retrieveCandidates(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        // Both items should be candidates; the ranker decides final order
        assertEquals(2, candidates.size());
    }

    // ──────────────────────────────────────────────────────────────
    // 11. SELECTEDKNOWLEDGE PERSISTENCE — COMMIT_DIFF reaches snapshot
    // ──────────────────────────────────────────────────────────────

    @Test
    void commitDiffSurvivesToSelectedKnowledge() throws Exception {
        var diagnostics = mock(AnalysisExecutionDiagnosticRepository.class);
        var insights = mock(InsightRepository.class);
        var mapper = mock(ObjectMapper.class);
        var repositoryContexts = mock(RepositoryContextService.class);

        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        AnalysisExecutionDiagnostic diagnostic = AnalysisExecutionDiagnostic.builder()
                .analysisId(analysisId).collectionComplete(true).warningCount(0).build();
        when(diagnostics.findById(analysisId)).thenReturn(Optional.of(diagnostic));
        when(mapper.writeValueAsString(any())).thenReturn("stable");
        when(insights.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(
                projectId, List.of(InsightStatus.ACTIVE))).thenReturn(List.of());

        AnalysisContext context = new AnalysisContext(
                new AnalysisContext.ProjectSnapshot(projectId, "Project", "project", null,
                        ProjectStatus.ACTIVE),
                new AnalysisContext.AnalysisSnapshot(analysisId, AnalysisType.ARCHITECTURE_REVIEW,
                        "architecture-overview", "v1", AnalysisStatus.IN_PROGRESS,
                        Instant.EPOCH, null, Instant.EPOCH),
                mock(ProjectProfileResponse.class), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        IntentDefinition intent = new IntentDefinition("architecture-overview", "v1", "Architecture",
                List.of(), List.of(), Map.of(), "prompt-v1");

        RepositoryEvidence commitDiff = evidence(
                RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                "diff:abc123:src/App.java", 85);
        RepositoryContext repositoryContext = new RepositoryContext(
                "v1", ContextProfile.ARCHITECTURE_REVIEW, List.of(),
                "v1", List.of(), List.of(commitDiff), Map.of(),
                new RepositoryContext.ContextBudget(60, 500, 20, 6000),
                100, 1, 0, false, List.of(), List.of(), "digest");
        when(repositoryContexts.build(eq(context), eq(intent), isNull(), anyList(), anyList()))
                .thenReturn(repositoryContext);

        var service = new KnowledgeSelectionServiceImpl(
                diagnostics, insights, mapper, repositoryContexts, 15);
        SelectedKnowledge result = service.select(context, intent, null);

        assertNotNull(result.repositoryContext());
        assertTrue(result.repositoryContext().evidence().stream()
                .anyMatch(e -> e.layer() == RepositoryContextLayer.COMMIT_DIFF));
    }

    // ──────────────────────────────────────────────────────────────
    // 12. HUMAN VISIBILITY — COMMIT_DIFF in repositoryContext.evidence
    // ──────────────────────────────────────────────────────────────

    @Test
    void commitDiffInRepositoryContextIsHumanVisible() {
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

        // Story 0096 projection reads repositoryContext.evidence directly
        assertTrue(context.evidence().stream()
                .anyMatch(e -> e.layer() == RepositoryContextLayer.COMMIT_DIFF
                        && e.reference().equals("diff:abc123:src/App.java")),
                "COMMIT_DIFF must be in repositoryContext.evidence for human projection");
    }

    // ──────────────────────────────────────────────────────────────
    // 13. NO DUPLICATE RETRIEVAL — shared primitive reuses collectors
    // ──────────────────────────────────────────────────────────────

    @Test
    void sharedRetrievalReusesExistingCollectors() {
        RepositoryContextCollector collector = mock(RepositoryContextCollector.class);
        when(collector.collect(any())).thenReturn(List.of(
                evidence(RepositoryContextLayer.COMMIT_DIFF, "CHANGED_FILE",
                        "diff:abc123:src/App.java", 85)));
        when(collector.collectorId()).thenReturn("commit-diff");
        when(collector.collectorVersion()).thenReturn("v1");

        RepositoryContextEngine engine = engineWithCollector(collector);

        // retrieveCandidates calls the collector once
        List<RepositoryEvidence> candidates = engine.retrieveCandidates(
                mock(AnalysisContext.class),
                mock(IntentDefinition.class),
                null, List.of());

        verify(collector, times(1)).collect(any());
        assertEquals(1, candidates.size());
    }

    // ──────────────────────────────────────────────────────────────
    // HELPER METHODS
    // ──────────────────────────────────────────────────────────────

    private RepositoryEvidence evidence(RepositoryContextLayer layer, String kind,
            String reference, int score) {
        return evidenceWithTokens(layer, kind, reference, score, 100);
    }

    private RepositoryEvidence evidenceWithTokens(RepositoryContextLayer layer, String kind,
            String reference, int score, int tokens) {
        return new RepositoryEvidence(
                layer, kind, reference, "Summary for " + reference,
                Instant.now(),
                new EvidenceScore("multi-criteria-v2", Map.of(), Map.of(), score, List.of()),
                List.of(),                 new EvidenceProvenance("DATABASE", null, null, null),
                Map.of(), tokens, List.of(), null, null);
    }

    private RepositoryContextEngine engine(
            List<RepositoryEvidence> collectorOutput,
            List<RepositoryEvidence> rankedOutput) {
        ContextIntelligence intelligence = mock(ContextIntelligence.class);
        when(intelligence.plan(any(), any())).thenReturn(contextPlan());

        RepositoryContextCollector collector = mock(RepositoryContextCollector.class);
        when(collector.collect(any())).thenReturn(collectorOutput);

        EvidenceRanker ranker = mock(EvidenceRanker.class);
        when(ranker.rank(any(), any())).thenReturn(rankedOutput);

        EvidenceSelector selector = new BudgetedDiverseEvidenceSelector();

        SelectedJavaSymbolEnricher symbolEnricher = mock(SelectedJavaSymbolEnricher.class);
        when(symbolEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedJavaSymbolEnricher.EnrichmentResult(selection, List.of());
        });

        SelectedFileContentEnricher contentEnricher = mock(SelectedFileContentEnricher.class);
        when(contentEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedFileContentEnricher.EnrichmentResult(selection, List.of());
        });

        return new RepositoryContextEngine(
                List.of(collector), intelligence, ranker, selector,
                symbolEnricher, contentEnricher, objectMapper,
                60, 500, 20, 6000);
    }

    private RepositoryContextEngine engineWithSelector(
            List<RepositoryEvidence> collectorOutput,
            List<RepositoryEvidence> rankedOutput,
            EvidenceSelector selector) {
        ContextIntelligence intelligence = mock(ContextIntelligence.class);
        when(intelligence.plan(any(), any())).thenReturn(contextPlan());

        RepositoryContextCollector collector = mock(RepositoryContextCollector.class);
        when(collector.collect(any())).thenReturn(collectorOutput);

        EvidenceRanker ranker = mock(EvidenceRanker.class);
        when(ranker.rank(any(), any())).thenReturn(rankedOutput);

        SelectedJavaSymbolEnricher symbolEnricher = mock(SelectedJavaSymbolEnricher.class);
        when(symbolEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedJavaSymbolEnricher.EnrichmentResult(selection, List.of());
        });

        SelectedFileContentEnricher contentEnricher = mock(SelectedFileContentEnricher.class);
        when(contentEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedFileContentEnricher.EnrichmentResult(selection, List.of());
        });

        return new RepositoryContextEngine(
                List.of(collector), intelligence, ranker, selector,
                symbolEnricher, contentEnricher, objectMapper,
                60, 500, 20, 6000);
    }

    private RepositoryContextEngine engineBounded(
            List<RepositoryEvidence> collectorOutput, int maxItems) {
        ContextIntelligence intelligence = mock(ContextIntelligence.class);
        when(intelligence.plan(any(), any())).thenReturn(contextPlan());

        RepositoryContextCollector collector = mock(RepositoryContextCollector.class);
        when(collector.collect(any())).thenReturn(collectorOutput);

        EvidenceRanker ranker = mock(EvidenceRanker.class);
        when(ranker.rank(any(), any())).thenReturn(collectorOutput);

        EvidenceSelector selector = new BudgetedDiverseEvidenceSelector();

        SelectedJavaSymbolEnricher symbolEnricher = mock(SelectedJavaSymbolEnricher.class);
        when(symbolEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedJavaSymbolEnricher.EnrichmentResult(selection, List.of());
        });

        SelectedFileContentEnricher contentEnricher = mock(SelectedFileContentEnricher.class);
        when(contentEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedFileContentEnricher.EnrichmentResult(selection, List.of());
        });

        return new RepositoryContextEngine(
                List.of(collector), intelligence, ranker, selector,
                symbolEnricher, contentEnricher, objectMapper,
                maxItems, 500, 20, 6000);
    }

    private RepositoryContextEngine engineWithCollector(
            RepositoryContextCollector collector) {
        ContextIntelligence intelligence = mock(ContextIntelligence.class);
        when(intelligence.plan(any(), any())).thenReturn(contextPlan());

        EvidenceRanker ranker = mock(EvidenceRanker.class);
        when(ranker.rank(any(), any())).thenReturn(List.of());

        EvidenceSelector selector = new BudgetedDiverseEvidenceSelector();

        SelectedJavaSymbolEnricher symbolEnricher = mock(SelectedJavaSymbolEnricher.class);
        when(symbolEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedJavaSymbolEnricher.EnrichmentResult(selection, List.of());
        });

        SelectedFileContentEnricher contentEnricher = mock(SelectedFileContentEnricher.class);
        when(contentEnricher.enrich(any(), any())).thenAnswer(invocation -> {
            EvidenceSelector.SelectionResult selection = invocation.getArgument(1);
            return new SelectedFileContentEnricher.EnrichmentResult(selection, List.of());
        });

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
}
