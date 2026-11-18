package com.hopeful117.devlogai.repositorycontext.ranking;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextPlan;
import com.hopeful117.devlogai.repositorycontext.intelligence.ContextProfileDefinition;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceCriterion;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidencePrecisionPolicy;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import com.hopeful117.devlogai.intent.model.UserGuidance;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeterministicEvidenceRankerTest {
    private final DeterministicEvidenceRanker ranker =
            new DeterministicEvidenceRanker(new RetrievalFusionService(1.0, 1.0));

    @Test
    void suppressesCommonTermsAndPreservesDiscriminatingPathTerms() {
        List<RepositoryEvidence> candidates = List.of(
                evidence("a", "TEST_FILE", "backend generic test", "src/AuthTokenTest.java"),
                evidence("b", "TEST_FILE", "backend generic test", "src/OtherTest.java"),
                evidence("c", "SOURCE_FILE", "backend generic source", "src/Other.java"),
                evidence("d", "CONFIG_FILE", "backend generic config", "application.yml"));
        ContextRequest request = request("Update backend test auth token", precision());

        List<RepositoryEvidence> ranked = ranker.rank(candidates, request);
        RepositoryEvidence auth = ranked.stream()
                .filter(value -> value.reference().equals("a")).findFirst().orElseThrow();
        RepositoryEvidence other = ranked.stream()
                .filter(value -> value.reference().equals("b")).findFirst().orElseThrow();

        assertTrue(auth.score().criteria().get(EvidenceCriterion.SEMANTIC_RELEVANCE)
                > other.score().criteria().get(EvidenceCriterion.SEMANTIC_RELEVANCE));
        assertTrue(auth.rankingReasons().stream().anyMatch(
                value -> value.contains("common=backend,test")));
        assertEquals("multi-criteria-v2", auth.score().policyVersion());
        assertEquals(ranked, ranker.rank(candidates, request));
    }

    @Test
    void keepsSingleCandidateTermsUsefulAndStable() {
        RepositoryEvidence candidate = evidence(
                "single", "SOURCE_FILE", "payment gateway", "src/PaymentGateway.java");
        List<RepositoryEvidence> ranked = ranker.rank(List.of(candidate),
                request("Payment gateway", precision()));

        assertTrue(ranked.getFirst().score().criteria()
                .get(EvidenceCriterion.SEMANTIC_RELEVANCE) > 0);
        assertEquals("single", ranked.getFirst().reference());
    }

    @Test
    void unrestrictedPolicyPreservesFixedCommonTermContribution() {
        List<RepositoryEvidence> candidates = List.of(
                evidence("one", "SOURCE_FILE", "shared term", "src/One.java"),
                evidence("two", "TEST_FILE", "shared term", "src/TwoTest.java"));

        List<RepositoryEvidence> ranked = ranker.rank(candidates,
                request("Shared term", EvidencePrecisionPolicy.UNRESTRICTED));

        assertEquals(50, ranked.getFirst().score().criteria()
                .get(EvidenceCriterion.SEMANTIC_RELEVANCE));
        assertTrue(ranked.getFirst().rankingReasons().stream()
                .anyMatch(value -> value.contains("matched=shared,term;common=")));
    }

    @Test
    void preservesUncappedStrengthWhenSemanticCriteriaSaturate() {
        List<RepositoryEvidence> candidates = List.of(
                evidence("weaker", "SOURCE_FILE",
                        "selected content allocation policy", "src/A.java"),
                evidence("stronger", "SOURCE_FILE",
                        "selected content allocation precision policy",
                        "src/Z.java"));

        List<RepositoryEvidence> ranked = ranker.rank(candidates,
                request("selected content allocation precision policy",
                        EvidencePrecisionPolicy.UNRESTRICTED));
        RepositoryEvidence weaker = ranked.stream()
                .filter(value -> value.reference().equals("weaker"))
                .findFirst().orElseThrow();
        RepositoryEvidence stronger = ranked.stream()
                .filter(value -> value.reference().equals("stronger"))
                .findFirst().orElseThrow();

        assertEquals(100, weaker.score().criteria()
                .get(EvidenceCriterion.SEMANTIC_RELEVANCE));
        assertEquals(100, stronger.score().criteria()
                .get(EvidenceCriterion.SEMANTIC_RELEVANCE));
        assertTrue(stronger.score().matchStrength().semantic()
                > weaker.score().matchStrength().semantic());
    }

    @Test
    void requestedFileReceivesDeterministicGuidanceBoost() {
        List<RepositoryEvidence> candidates = List.of(
                evidence("config", "CONFIG_FILE", "compose configuration", "docker-compose.yml"),
                evidence("other", "CONFIG_FILE", "compose configuration", "application.yml"));

        UserGuidance guidance = new UserGuidance(
                "Inspect the requested file", "kiko", "focused", "analytical",
                "engineering-story-preparation", List.of("docker-compose.yml"));
        List<RepositoryEvidence> ranked = ranker.rank(candidates,
                request("Inspect configuration", precision(), guidance));

        RepositoryEvidence requested = ranked.stream()
                .filter(value -> value.reference().equals("config")).findFirst().orElseThrow();
        RepositoryEvidence other = ranked.stream()
                .filter(value -> value.reference().equals("other")).findFirst().orElseThrow();
        assertEquals(100, requested.score().criteria()
                .get(EvidenceCriterion.USER_GUIDANCE_BOOST));
        assertTrue(requested.score().criteria().get(EvidenceCriterion.USER_GUIDANCE_BOOST)
                > other.score().criteria().get(EvidenceCriterion.USER_GUIDANCE_BOOST));
    }

    @Test
    void semanticCandidatesReceiveCompositeScoreWhenSemanticCollectorEnabled() {
        RepositoryEvidence lexicalEvidence = evidence(
                "lex", "SOURCE_FILE", "authentication gateway", "src/Auth.java");
        RepositoryEvidence semanticEvidence = semanticEvidence(
                "sem", "SOURCE_FILE", "authentication gateway", "src/Auth.java");
        List<RepositoryEvidence> candidates = List.of(lexicalEvidence, semanticEvidence);
        ContextRequest request = request("authentication gateway", precision());

        List<RepositoryEvidence> ranked = ranker.rank(candidates, request);

        // With semantic enabled, the semantic candidate gets a composite score from RRF
        RepositoryEvidence sem = ranked.stream()
                .filter(e -> e.reference().equals("sem")).findFirst().orElseThrow();
        // RRF score should be non-zero (rank 1 in semantic channel)
        assertTrue(sem.score().criteria().get(EvidenceCriterion.SEMANTIC_RELEVANCE) > 0);
    }

    @Test
    void noSemanticCandidatesPreservesExistingTermModelBehavior() {
        List<RepositoryEvidence> candidates = List.of(
                evidence("a", "SOURCE_FILE", "payment gateway service", "src/PayGateway.java"),
                evidence("b", "SOURCE_FILE", "unrelated file", "src/Other.java"));
        ContextRequest request = request("payment gateway", precision());

        List<RepositoryEvidence> withFusion = ranker.rank(candidates, request);
        // Without semantic candidates, fuse() returns empty map → term-model scores apply
        DeterministicEvidenceRanker baseRanker =
                new DeterministicEvidenceRanker(new RetrievalFusionService(1.0, 1.0));
        List<RepositoryEvidence> withoutFusion = baseRanker.rank(candidates, request);

        for (RepositoryEvidence ev : withFusion) {
            RepositoryEvidence match = withoutFusion.stream()
                    .filter(e -> e.reference().equals(ev.reference()))
                    .findFirst().orElseThrow();
            assertEquals(match.score().criteria().get(EvidenceCriterion.SEMANTIC_RELEVANCE),
                    ev.score().criteria().get(EvidenceCriterion.SEMANTIC_RELEVANCE),
                    "SEMANTIC_RELEVANCE should be unchanged when no semantic candidates");
        }
    }

    @Test
    void nullOccurredAt_receivesNeutralRecencyScore_notZero() {
        RepositoryEvidence evidence = evidenceWithOccurredAt(
                "adr", "DECISION", "ADR content", "docs/ADR-001.md", null);
        List<RepositoryEvidence> ranked = ranker.rank(List.of(evidence),
                requestWithRecencyWeight("ADR", precision()));

        int recency = ranked.get(0).score().criteria().get(EvidenceCriterion.RECENCY);
        assertEquals(50, recency);
    }

    @Test
    void nullOccurredAtAdr_outranks_veryOldDatedAdr() {
        RepositoryEvidence undated = evidenceWithOccurredAt(
                "undated", "DECISION", "ADR content", "docs/ADR-001.md", null);
        RepositoryEvidence old = evidenceWithOccurredAt(
                "old", "DECISION", "ADR content", "docs/ADR-002.md",
                Instant.parse("2020-01-01T00:00:00Z"));
        List<RepositoryEvidence> ranked = ranker.rank(List.of(undated, old),
                requestWithRecencyWeight("ADR", precision()));

        int undatedRecency = ranked.stream().filter(e -> e.reference().equals("undated"))
                .findFirst().orElseThrow().score().criteria().get(EvidenceCriterion.RECENCY);
        int oldRecency = ranked.stream().filter(e -> e.reference().equals("old"))
                .findFirst().orElseThrow().score().criteria().get(EvidenceCriterion.RECENCY);

        assertTrue(undatedRecency > oldRecency,
                "Undated ADR should have RECENCY 50, 5-year-old ADR should have RECENCY 10");
    }

    private ContextRequest request(String objective, EvidencePrecisionPolicy policy) {
        return request(objective, policy, null);
    }

    private ContextRequest request(String objective, EvidencePrecisionPolicy policy,
            UserGuidance guidance) {
        AnalysisContext context = mock(AnalysisContext.class);
        AnalysisContext.AnalysisSnapshot analysis = mock(
                AnalysisContext.AnalysisSnapshot.class);
        when(context.analysis()).thenReturn(analysis);
        when(analysis.createdAt()).thenReturn(Instant.parse("2026-08-08T12:00:00Z"));
        IntentDefinition intent = mock(IntentDefinition.class);
        when(intent.id()).thenReturn("engineering-story-preparation");
        when(intent.objective()).thenReturn(objective);
        Map<EvidenceCriterion, Integer> weights = Map.of(
                EvidenceCriterion.SEMANTIC_RELEVANCE, 100,
                EvidenceCriterion.ARCHITECTURAL_RELEVANCE, 0,
                EvidenceCriterion.HISTORICAL_RELEVANCE, 0,
                EvidenceCriterion.RECENCY, 0,
                EvidenceCriterion.CONFIDENCE, 0,
                EvidenceCriterion.USER_GUIDANCE_BOOST, 0);
        var profile = new ContextProfileDefinition("engineering-story-v1",
                ContextProfile.ENGINEERING_STORY, "v1", weights,
                List.of(RepositoryContextLayer.RELATED_SOURCE_CODE), 1, 100, policy);
        var plan = new ContextPlan("v2", List.of(profile), weights,
                profile.preferredLayers(), 1, policy, List.of());
        return new ContextRequest(context, intent, guidance, List.of(), plan,
                new RepositoryContext.ContextBudget(60, 500, 20, 6000));
    }

    private EvidencePrecisionPolicy precision() {
        return new EvidencePrecisionPolicy("test", "v1", 50, 0, 25, 100, 75);
    }

    private ContextRequest requestWithRecencyWeight(
            String objective, EvidencePrecisionPolicy policy) {
        AnalysisContext context = mock(AnalysisContext.class);
        AnalysisContext.AnalysisSnapshot analysis = mock(AnalysisContext.AnalysisSnapshot.class);
        when(context.analysis()).thenReturn(analysis);
        when(analysis.createdAt()).thenReturn(Instant.parse("2026-08-08T12:00:00Z"));
        IntentDefinition intent = mock(IntentDefinition.class);
        when(intent.id()).thenReturn("engineering-story-preparation");
        when(intent.objective()).thenReturn(objective);
        Map<EvidenceCriterion, Integer> weights = Map.of(
                EvidenceCriterion.SEMANTIC_RELEVANCE, 0,
                EvidenceCriterion.ARCHITECTURAL_RELEVANCE, 0,
                EvidenceCriterion.HISTORICAL_RELEVANCE, 0,
                EvidenceCriterion.RECENCY, 100,
                EvidenceCriterion.CONFIDENCE, 0,
                EvidenceCriterion.USER_GUIDANCE_BOOST, 0);
        var profile = new ContextProfileDefinition("engineering-story-v1",
                ContextProfile.ENGINEERING_STORY, "v1", weights,
                List.of(RepositoryContextLayer.RELATED_SOURCE_CODE), 1, 100, policy);
        var plan = new ContextPlan("v2", List.of(profile), weights,
                profile.preferredLayers(), 1, policy, List.of());
        return new ContextRequest(context, intent, null, List.of(), plan,
                new RepositoryContext.ContextBudget(60, 500, 20, 6000));
    }

    private RepositoryEvidence evidenceWithOccurredAt(
            String reference, String kind, String summary, String file, Instant occurredAt) {
        return new RepositoryEvidence(RepositoryContextLayer.ADR,
                kind, reference, summary, occurredAt, EvidenceScore.unscored(),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                "CORE_KNOWLEDGE", "repository", file, reference),
                Map.of(), 10, List.of());
    }

    private RepositoryEvidence evidence(
            String reference, String kind, String summary, String file) {
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                kind, reference, summary, Instant.EPOCH, EvidenceScore.unscored(),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                "DETERMINISTIC_EXTRACTION", "repository", file, reference),
                Map.of(), 10, List.of());
    }

    private RepositoryEvidence semanticEvidence(
            String reference, String kind, String summary, String file) {
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                kind, reference, summary, Instant.EPOCH, EvidenceScore.unscored(),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                "SEMANTIC_RETRIEVAL", "repository", file, reference),
                Map.of(), 10, List.of());
    }
}
