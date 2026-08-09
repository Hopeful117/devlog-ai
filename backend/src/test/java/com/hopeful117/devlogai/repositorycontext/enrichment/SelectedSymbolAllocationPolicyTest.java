package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedSymbolAllocationPolicyTest {
    @Test
    void ordersByScoreThenTypedStrengthThenReference() {
        var policy = new SelectedSymbolAllocationPolicy();
        RepositoryEvidence weak = evidence("file:a.java", 49, 100);
        RepositoryEvidence strong = evidence("file:z.java", 49, 300);

        var allocations = policy.allocate(List.of(weak, strong));

        assertEquals(strong.reference(), allocations.getFirst().evidence().reference());
        assertEquals(1, allocations.getFirst().rank());
        assertTrue(allocations.getFirst().reasons().contains(
                "SEMANTIC_MATCH_STRENGTH=300"));
    }

    private RepositoryEvidence evidence(String reference, int score, int semantic) {
        return new RepositoryEvidence(RepositoryContextLayer.RELATED_SOURCE_CODE,
                "SOURCE_FILE", reference, reference, Instant.EPOCH,
                new EvidenceScore("test", Map.of(), Map.of(), score, List.of(),
                        new EvidenceScore.MatchStrength(semantic, semantic)),
                List.of(), new RepositoryEvidence.EvidenceProvenance(
                        "REPOSITORY_STRUCTURE", "source", reference.substring(5), reference),
                Map.of("resolvedRevision", "abc"), 10, List.of());
    }
}
