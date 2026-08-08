package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SelectedContentAllocationPolicyTest {
    private final SelectedContentAllocationPolicy policy =
            new SelectedContentAllocationPolicy();

    @Test
    void usesTypedMatchStrengthBeforeAlphabeticalReference() {
        RepositoryEvidence distractor = evidence(
                "file:aaa/Distractor.java", 49, 125, 125);
        RepositoryEvidence central = evidence(
                "file:zzz/SelectedContentPolicy.java", 49, 225, 200);

        List<SelectedContentAllocationPolicy.Allocation> result =
                policy.allocate(List.of(distractor, central));

        assertEquals(central.reference(), result.getFirst().evidence().reference());
        assertEquals(1, result.getFirst().rank());
        assertEquals(List.of("FINAL_SCORE=49", "SEMANTIC_MATCH_STRENGTH=225",
                "GUIDANCE_MATCH_STRENGTH=200"), result.getFirst().reasons());
    }

    @Test
    void remainsStableWhenWeakerDistractorsAreRenamedOrReordered() {
        RepositoryEvidence central = evidence(
                "file:mmm/Central.java", 49, 200, 175);
        List<String> first = policy.allocate(List.of(
                        evidence("file:aaa/First.java", 49, 100, 100), central,
                        evidence("file:zzz/Last.java", 49, 125, 100)))
                .stream().map(value -> value.evidence().reference()).toList();
        List<String> renamed = policy.allocate(List.of(
                        evidence("file:000/Renamed.java", 49, 125, 100),
                        evidence("file:yyy/Other.java", 49, 100, 100), central))
                .stream().map(value -> value.evidence().reference()).toList();

        assertEquals(central.reference(), first.getFirst());
        assertEquals(central.reference(), renamed.getFirst());
    }

    @Test
    void usesReferenceOnlyWhenAllMeaningfulSignalsTie() {
        List<SelectedContentAllocationPolicy.Allocation> result = policy.allocate(List.of(
                evidence("file:z.java", 49, 100, 75),
                evidence("file:a.java", 49, 100, 75)));

        assertEquals("file:a.java", result.getFirst().evidence().reference());
    }

    private RepositoryEvidence evidence(
            String reference,
            int finalScore,
            int semanticStrength,
            int guidanceStrength
    ) {
        EvidenceScore score = new EvidenceScore("test", Map.of(), Map.of(), finalScore,
                List.of(), new EvidenceScore.MatchStrength(
                        semanticStrength, guidanceStrength));
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                "SOURCE_FILE", reference, reference, Instant.EPOCH, score,
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                "REPOSITORY_STRUCTURE", "repository", reference, reference),
                Map.of(), 10, List.of());
    }
}
