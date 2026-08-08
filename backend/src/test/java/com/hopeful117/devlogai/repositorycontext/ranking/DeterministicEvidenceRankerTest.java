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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeterministicEvidenceRankerTest {
    private final DeterministicEvidenceRanker ranker = new DeterministicEvidenceRanker();

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

    private ContextRequest request(String objective, EvidencePrecisionPolicy policy) {
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
        return new ContextRequest(context, intent, null, List.of(), plan,
                new RepositoryContext.ContextBudget(60, 500, 20, 6000));
    }

    private EvidencePrecisionPolicy precision() {
        return new EvidencePrecisionPolicy("test", "v1", 50, 0, 25, 75);
    }

    private RepositoryEvidence evidence(
            String reference, String kind, String summary, String file) {
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                kind, reference, summary, Instant.EPOCH, EvidenceScore.unscored(),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                "DETERMINISTIC_EXTRACTION", "repository", file, reference),
                Map.of(), 10, List.of());
    }
}
