package com.hopeful117.devlogai.repositorycontext.selection;

import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class BudgetedDiverseEvidenceSelectorTest {
    private final BudgetedDiverseEvidenceSelector selector =
            new BudgetedDiverseEvidenceSelector();

    @Test
    void boundsRepeatedKindsAndRetainsStrongRelevantOverflow() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        ranked.add(evidence("test-strong", "TEST_FILE", 90));
        for (int index = 0; index < 39; index++)
            ranked.add(evidence("test-" + index, "TEST_FILE", 70 - index / 2));
        addCategory(ranked, "source", "SOURCE_FILE", 5, 70);
        addCategory(ranked, "config", "CONFIG_FILE", 5, 68);
        addCategory(ranked, "module", "MODULE", 4, 66);
        addCategory(ranked, "insight", "INSIGHT", 4, 64);
        ranked.add(evidence("weak", "DOCUMENT", 20));
        ContextRequest request = request(60, 6000,
                new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 100, 75));

        EvidenceSelector.SelectionResult result = selector.select(ranked, request);

        assertEquals(15, result.selected().stream()
                .filter(value -> value.kind().equals("TEST_FILE")).count());
        assertTrue(result.selected().stream()
                .filter(value -> value.kind().equals("TEST_FILE")).count()
                < result.selected().size() / 2.0);
        assertTrue(result.selected().stream().anyMatch(
                value -> value.reference().equals("test-strong")));
        assertTrue(result.decisions().stream().anyMatch(value ->
                value.reason().equals("CATEGORY_CONCENTRATION_LIMIT")));
        assertTrue(result.decisions().stream().anyMatch(value ->
                value.reason().equals("INSUFFICIENT_RELEVANCE")));
    }

    private void addCategory(List<RepositoryEvidence> target, String prefix,
            String kind, int count, int initialScore) {
        for (int index = 0; index < count; index++)
            target.add(evidence(prefix + "-" + index, kind, initialScore - index));
    }

    @Test
    void distinguishesDuplicatesAndTokenBudget() {
        RepositoryEvidence first = evidence("same", "SOURCE_FILE", 70);
        List<RepositoryEvidence> ranked = List.of(first,
                evidence("same", "SOURCE_FILE", 65),
                evidence("large", "CONFIG_FILE", 60, 50));

        EvidenceSelector.SelectionResult result = selector.select(ranked,
                request(10, 20, EvidencePrecisionPolicy.UNRESTRICTED));

        assertEquals(1, result.selected().size());
        assertTrue(result.decisions().stream().anyMatch(value ->
                value.reason().equals("DUPLICATE_REFERENCE")));
        assertTrue(result.decisions().stream().anyMatch(value ->
                value.reason().equals("TOKEN_BUDGET_EXCEEDED")));
    }

    @Test
    void allowsStrongEvidenceBeyondTheOrdinaryKindAllowance() {
        List<RepositoryEvidence> ranked = List.of(
                evidence("strong-1", "TEST_FILE", 95),
                evidence("strong-2", "TEST_FILE", 90),
                evidence("strong-3", "TEST_FILE", 85),
                evidence("source", "SOURCE_FILE", 70),
                evidence("config", "CONFIG_FILE", 65),
                evidence("module", "MODULE", 60),
                evidence("insight", "INSIGHT", 55),
                evidence("adr", "DECISION", 50));

        EvidenceSelector.SelectionResult result = selector.select(ranked,
                request(20, 1000,
                        new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 100, 75)));

        assertEquals(3, result.selected().stream()
                .filter(value -> value.kind().equals("TEST_FILE")).count());
        assertTrue(result.decisions().stream().anyMatch(value ->
                value.evidenceReference().equals("strong-3")
                        && value.reason().equals("SELECTED_BY_STRONG_RELEVANCE")));
    }

    @Test
    void handlesEmptyInputAndReportsItemBudgetSeparately() {
        ContextRequest emptyRequest = request(
                10, 1000, EvidencePrecisionPolicy.UNRESTRICTED);
        EvidenceSelector.SelectionResult empty = selector.select(List.of(), emptyRequest);

        assertTrue(empty.selected().isEmpty());
        assertTrue(empty.decisions().isEmpty());
        assertEquals(0, empty.usedTokens());

        EvidenceSelector.SelectionResult limited = selector.select(List.of(
                evidence("source", "SOURCE_FILE", 70),
                evidence("config", "CONFIG_FILE", 65)),
                request(1, 1000, EvidencePrecisionPolicy.UNRESTRICTED));

        assertEquals(1, limited.selected().size());
        assertTrue(limited.decisions().stream().anyMatch(value ->
                value.reason().equals("EVIDENCE_ITEM_BUDGET_EXCEEDED")));
    }

    private ContextRequest request(int maximumItems, int maximumTokens,
            EvidencePrecisionPolicy policy) {
        var profile = new ContextProfileDefinition("profile",
                ContextProfile.ENGINEERING_STORY, "v1", Map.of(),
                List.of(RepositoryContextLayer.RELATED_SOURCE_CODE), 1, 100, policy);
        var plan = new ContextPlan("v2", List.of(profile), Map.of(),
                profile.preferredLayers(), 1, policy, List.of());
        return new ContextRequest(mock(com.hopeful117.devlogai.analysis.context.AnalysisContext.class),
                mock(com.hopeful117.devlogai.intent.model.IntentDefinition.class),
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

    @Test
    void ceilingEnforcementPreventsCategoryDominance() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int i = 0; i < 50; i++)
            ranked.add(evidence("commit-" + i, "COMMIT_DIFF", 90 - i / 3));
        addCategory(ranked, "git", "GIT_HISTORY", 10, 80);
        addCategory(ranked, "insight", "INSIGHT", 5, 70);
        addCategory(ranked, "adr", "DECISION", 3, 65);
        ContextRequest request = request(60, 6000,
                new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 20, 75));

        EvidenceSelector.SelectionResult result = selector.select(ranked, request);

        long commitDiffCount = result.selected().stream()
                .filter(v -> v.kind().equals("COMMIT_DIFF")).count();
        assertTrue(commitDiffCount <= 12,
                "COMMIT_DIFF should be capped at 20% of 60 = 12, was " + commitDiffCount);
    }

    @Test
    void strongRelevanceCannotBypassCategoryCeiling() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int i = 0; i < 30; i++)
            ranked.add(evidence("commit-strong-" + i, "COMMIT_DIFF", 95));
        addCategory(ranked, "git", "GIT_HISTORY", 10, 80);
        addCategory(ranked, "insight", "INSIGHT", 8, 70);
        addCategory(ranked, "adr", "DECISION", 5, 65);
        ContextRequest request = request(60, 6000,
                new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 20, 75));

        EvidenceSelector.SelectionResult result = selector.select(ranked, request);

        long commitDiffCount = result.selected().stream()
                .filter(v -> v.kind().equals("COMMIT_DIFF")).count();
        assertTrue(commitDiffCount <= 12,
                "Strong relevance COMMIT_DIFF (score >= 75) should not bypass ceiling, was " + commitDiffCount);
    }

    @Test
    void knowledgeFloorPreservedWithCeiling() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int i = 0; i < 50; i++)
            ranked.add(evidence("commit-" + i, "COMMIT_DIFF", 90 - i / 3));
        addCategory(ranked, "fact", "FACT", 10, 70);
        addCategory(ranked, "insight", "INSIGHT", 10, 65);
        ContextRequest request = request(60, 6000,
                new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 20, 75));

        EvidenceSelector.SelectionResult result = selector.select(ranked, request);

        long knowledgeCount = result.selected().stream()
                .filter(v -> Set.of("INSIGHT", "FACT", "DECISION", "ARTIFACT",
                        "MILESTONE", "CHALLENGE", "ENGINEERING_EVENT", "OBSERVATION")
                        .contains(v.kind())).count();
        assertTrue(knowledgeCount >= 2,
                "Knowledge floor should reserve at least 2 slots, was " + knowledgeCount);
    }

    @Test
    void globalBudgetFilledWhenDiverseEvidenceExists() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int i = 0; i < 12; i++)
            ranked.add(evidence("commit-" + i, "COMMIT_DIFF", 90 - i));
        for (int i = 0; i < 15; i++)
            ranked.add(evidence("git-" + i, "GIT_HISTORY", 85 - i));
        for (int i = 0; i < 15; i++)
            ranked.add(evidence("insight-" + i, "INSIGHT", 75 - i));
        for (int i = 0; i < 10; i++)
            ranked.add(evidence("adr-" + i, "DECISION", 70 - i));
        for (int i = 0; i < 10; i++)
            ranked.add(evidence("source-" + i, "SOURCE_FILE", 65 - i));
        for (int i = 0; i < 10; i++)
            ranked.add(evidence("config-" + i, "CONFIG_FILE", 60 - i));
        ContextRequest request = request(60, 6000,
                new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 20, 75));

        EvidenceSelector.SelectionResult result = selector.select(ranked, request);

        assertEquals(60, result.selected().size(),
                "Global budget should be filled when diverse evidence exists");
    }

    @Test
    void sparseCategoriesAllowRedistribution() {
        List<RepositoryEvidence> ranked = new ArrayList<>();
        for (int i = 0; i < 12; i++)
            ranked.add(evidence("commit-" + i, "COMMIT_DIFF", 90 - i));
        for (int i = 0; i < 50; i++)
            ranked.add(evidence("git-" + i, "GIT_HISTORY", 85 - i));
        ContextRequest request = request(60, 6000,
                new EvidencePrecisionPolicy("test", "v1", 50, 35, 25, 20, 75));

        EvidenceSelector.SelectionResult result = selector.select(ranked, request);

        long commitDiffCount = result.selected().stream()
                .filter(v -> v.kind().equals("COMMIT_DIFF")).count();
        long gitHistoryCount = result.selected().stream()
                .filter(v -> v.kind().equals("GIT_HISTORY")).count();
        assertEquals(12, commitDiffCount,
                "COMMIT_DIFF should be capped at 12");
        assertEquals(12, gitHistoryCount,
                "Sparse categories remain capped when no other eligible kinds exist");
        assertEquals(24, result.selected().size(),
                "Hard ceilings limit total selection when diversity is insufficient");
    }
}
