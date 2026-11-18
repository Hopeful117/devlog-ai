package com.hopeful117.devlogai.repositorycontext.ranking;

import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalFusionServiceTest {

    private final RetrievalFusionService fusion = new RetrievalFusionService(1.0, 1.0);

    @Test
    void emptySemanticListReturnsEmptyMap() {
        List<RepositoryEvidence> lexical = List.of(evidence("a"), evidence("b"));
        Map<String, Integer> result = fusion.fuse(lexical, List.of(), 60);
        assertTrue(result.isEmpty());
    }

    @Test
    void documentInBothListsScoresHigherThanEitherAlone() {
        // "shared" appears in both; "lexOnly" is lexical-only; "semOnly" is semantic-only
        List<RepositoryEvidence> lexical = List.of(evidence("shared"), evidence("lexOnly"));
        List<RepositoryEvidence> semantic = List.of(evidence("shared"), evidence("semOnly"));

        Map<String, Integer> scores = fusion.fuse(lexical, semantic, 60);

        int scoreShared = scores.get("shared");
        int scoreLexOnly = scores.get("lexOnly");
        int scoreSemOnly = scores.get("semOnly");

        assertTrue(scoreShared > scoreLexOnly,
                "shared(%d) should beat lexOnly(%d)".formatted(scoreShared, scoreLexOnly));
        assertTrue(scoreShared > scoreSemOnly,
                "shared(%d) should beat semOnly(%d)".formatted(scoreShared, scoreSemOnly));
    }

    @Test
    void rrfMonotonicityHigherRankPositionProducesHigherScore() {
        List<RepositoryEvidence> lexical = List.of(evidence("rank1"), evidence("rank2"), evidence("rank3"));
        List<RepositoryEvidence> semantic = List.of(evidence("semOnly"));

        Map<String, Integer> scores = fusion.fuse(lexical, semantic, 60);

        int s1 = scores.get("rank1");
        int s2 = scores.get("rank2");
        int s3 = scores.get("rank3");

        assertTrue(s1 >= s2, "rank1(%d) >= rank2(%d)".formatted(s1, s2));
        assertTrue(s2 >= s3, "rank2(%d) >= rank3(%d)".formatted(s2, s3));
    }

    @Test
    void smallerKAmplifiesDifferenceBetweenRanks() {
        List<RepositoryEvidence> lexical = List.of(evidence("first"), evidence("second"));
        List<RepositoryEvidence> semantic = List.of(evidence("semOnly"));

        Map<String, Integer> scoresHighK = fusion.fuse(lexical, semantic, 1000);
        Map<String, Integer> scoresLowK = fusion.fuse(lexical, semantic, 1);

        int diffHighK = scoresHighK.get("first") - scoresHighK.get("second");
        int diffLowK = scoresLowK.get("first") - scoresLowK.get("second");

        assertTrue(diffLowK >= diffHighK,
                "lower k should produce at least as large rank differentiation");
    }

    @Test
    void documentAtRankOneInBothChannelsScoresOneHundred() {
        List<RepositoryEvidence> lexical = List.of(evidence("top"));
        List<RepositoryEvidence> semantic = List.of(evidence("top"));

        Map<String, Integer> scores = fusion.fuse(lexical, semantic, 60);

        assertEquals(100, scores.get("top"));
    }

    @Test
    void higherSemanticWeightElevatesSemanticRank() {
        RetrievalFusionService semHeavy = new RetrievalFusionService(1.0, 3.0);

        // "semGood" is rank-1 in semantic, rank-2 in lexical
        // "lexGood" is rank-1 in lexical, rank-2 in semantic
        List<RepositoryEvidence> lexical = List.of(evidence("lexGood"), evidence("semGood"));
        List<RepositoryEvidence> semantic = List.of(evidence("semGood"), evidence("lexGood"));

        Map<String, Integer> scoresEqual = fusion.fuse(lexical, semantic, 60);
        Map<String, Integer> scoresSemHeavy = semHeavy.fuse(lexical, semantic, 60);

        // With equal weights, lexGood and semGood might be equal (symmetric)
        // With semantic-heavy weights, semGood should score higher
        assertTrue(scoresSemHeavy.get("semGood") >= scoresSemHeavy.get("lexGood"),
                "semGood should benefit from semantic-heavy weighting");
    }

    @Test
    void allCandidatesIncludedInResultMap() {
        List<RepositoryEvidence> lexical = List.of(evidence("a"), evidence("b"));
        List<RepositoryEvidence> semantic = List.of(evidence("b"), evidence("c"));

        Map<String, Integer> scores = fusion.fuse(lexical, semantic, 60);

        assertTrue(scores.containsKey("a"), "lexical-only 'a' should be included");
        assertTrue(scores.containsKey("b"), "shared 'b' should be included");
        assertTrue(scores.containsKey("c"), "semantic-only 'c' should be included");
        assertFalse(scores.containsKey("d"), "unknown 'd' should not be included");
    }

    private RepositoryEvidence evidence(String reference) {
        return new RepositoryEvidence(
                RepositoryContextLayer.RELATED_SOURCE_CODE,
                "SOURCE_FILE", reference, "summary for " + reference,
                Instant.EPOCH, EvidenceScore.unscored(), List.of(),
                new RepositoryEvidence.EvidenceProvenance(
                        "DETERMINISTIC_EXTRACTION", "repository", null, reference),
                Map.of(), 10, List.of());
    }
}
