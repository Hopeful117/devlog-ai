package com.hopeful117.devlogai.repositorycontext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidenceScore;

public record RepositoryEvidence(
        RepositoryContextLayer layer,
        String kind,
        String reference,
        String summary,
        Instant occurredAt,
        EvidenceScore score,
        List<String> relatedReferences,
        EvidenceProvenance provenance,
        Map<String, String> extractionMetadata,
        int estimatedTokens,
        List<String> rankingReasons
) {
    public RepositoryEvidence {
        relatedReferences = List.copyOf(relatedReferences);
        extractionMetadata = Map.copyOf(extractionMetadata);
        rankingReasons = List.copyOf(rankingReasons);
    }

    public int relevanceScore() {
        return score.finalScore();
    }

    public RepositoryEvidence withRanking(EvidenceScore score, List<String> reasons) {
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, extractionMetadata,
                estimatedTokens, reasons);
    }

    public record EvidenceProvenance(
            String sourceType,
            String repositoryLocation,
            String originatingFile,
            String identifier
    ) {
    }
}
