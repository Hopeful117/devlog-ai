package com.hopeful117.devlogai.repositorycontext.selection;

import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextProfileDefinition;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidencePrecisionPolicy;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeFloorSelectionTest {
    private final BudgetedDiverseEvidenceSelector selector =
            new BudgetedDiverseEvidenceSelector();

    private static final EvidencePrecisionPolicy POLICY =
            new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 100, 75);

    @Test
    void abundantGitEvidenceCannotStarveRelevantTrustedKnowledge() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int index = 0; index < 70; index++)
            ranked.add(evidence("commit-" + index, "COMMIT", 90 - index / 3));
        ranked.add(evidence("insight-1", "INSIGHT", 64));
        ranked.add(evidence("story-1", "ENGINEERING_STORY", 55));
        ranked.add(evidence("fact-1", "FACT", 45));

        var result = selector.select(ranked, request(60, 6000));

        assertTrue(countKind(result, "INSIGHT") >= 1);
        assertTrue(countKind(result, "ENGINEERING_STORY") >= 1);
        assertTrue(countKind(result, "FACT") >= 1);
        assertTrue(result.decisions().stream().anyMatch(value ->
                value.reason().equals("SELECTED_BY_CATEGORY_FLOOR")));
        // Git still dominates the composition (>= half of the budget)
        assertTrue(countKind(result, "COMMIT") >= 30);
    }

    @Test
    void floorsNeverSelectIrrelevantKnowledge() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int index = 0; index < 65; index++)
            ranked.add(evidence("commit-" + index, "COMMIT", 80));
        ranked.add(evidence("weak-insight", "INSIGHT", 20));

        var result = selector.select(ranked, request(60, 6000));

        assertEquals(0, countKind(result, "INSIGHT"));
        assertTrue(result.decisions().stream().anyMatch(value ->
                value.evidenceReference().equals("weak-insight")
                        && value.reason().equals("INSUFFICIENT_RELEVANCE")));
    }

    @Test
    void unusedFloorCapacityReturnsToOrdinaryRankSelection() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int index = 0; index < 30; index++)
            ranked.add(evidence("commit-" + index, "COMMIT", 90));
        for (int index = 0; index < 45; index++)
            ranked.add(evidence("source-" + index, "SOURCE_FILE", 60));
        for (int index = 0; index < 45; index++)
            ranked.add(evidence("config-" + index, "CONFIG_FILE", 65));
        ranked.add(evidence("insight-1", "INSIGHT", 60));

        var result = selector.select(ranked, request(60, 6000));

        assertEquals(60, result.selected().size());
        assertEquals(1, countKind(result, "INSIGHT"));
        assertTrue(countKind(result, "COMMIT") >= 25);
    }

    @Test
    void floorsRespectItemAndTokenBudgets() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        ranked.add(evidence("insight-1", "INSIGHT", 64, 900));
        ranked.add(evidence("story-1", "ENGINEERING_STORY", 60, 900));
        ranked.add(evidence("fact-1", "FACT", 50, 900));
        for (int index = 0; index < 20; index++)
            ranked.add(evidence("commit-" + index, "COMMIT", 40));

        var result = selector.select(ranked, request(60, 2000));

        assertTrue(result.usedTokens() <= 2000);
        // the third knowledge item no longer fits the token budget and is skipped
        assertEquals(1, countKind(result, "INSIGHT"));
        assertEquals(1, countKind(result, "ENGINEERING_STORY"));
        assertEquals(0, countKind(result, "FACT"));
    }

    @Test
    void emptyKnowledgeCategoriesSimplyLeaveFloorsUnfilled() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int index = 0; index < 30; index++)
            ranked.add(evidence("source-" + index, "SOURCE_FILE", 75));

        var result = selector.select(ranked, request(60, 6000));

        assertEquals(30, result.selected().size());
        assertTrue(result.decisions().stream().noneMatch(value ->
                value.reason().equals("SELECTED_BY_CATEGORY_FLOOR")));
    }

    private long countKind(EvidenceSelector.SelectionResult result, String kind) {
        return result.selected().stream()
                .filter(value -> value.kind().equals(kind)).count();
    }

    private ContextRequest request(int maximumItems, int maximumTokens) {
        var profile = new ContextProfileDefinition("profile",
                ContextProfile.ENGINEERING_STORY, "v1", Map.of(),
                List.of(RepositoryContextLayer.RELATED_SOURCE_CODE), 1, 100, POLICY);
        var plan = new ContextPlan("v2", List.of(profile), Map.of(),
                profile.preferredLayers(), 1, POLICY, List.of());
        return new ContextRequest(
                org.mockito.Mockito.mock(
                        com.hopeful117.devlogai.analysis.context.AnalysisContext.class),
                org.mockito.Mockito.mock(
                        com.hopeful117.devlogai.intent.model.IntentDefinition.class),
                null, List.of(), plan, new RepositoryContext.ContextBudget(
                maximumItems, 500, 20, maximumTokens));
    }

    private RepositoryEvidence evidence(String reference, String kind, int score) {
        return evidence(reference, kind, score, 10);
    }

    private RepositoryEvidence evidence(
            String reference, String kind, int score, int tokens) {
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                kind, reference, reference, Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), score, List.of()),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                "DETERMINISTIC_EXTRACTION", "repository", reference, reference),
                Map.of(), tokens, List.of());
    }
}
