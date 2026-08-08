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
        List<String> rankingReasons,
        RepositoryEvidenceContent content
) {
    public RepositoryEvidence {
        relatedReferences = List.copyOf(relatedReferences);
        extractionMetadata = Map.copyOf(extractionMetadata);
        rankingReasons = List.copyOf(rankingReasons);
    }

    public RepositoryEvidence(
            RepositoryContextLayer layer, String kind, String reference,
            String summary, Instant occurredAt, EvidenceScore score,
            List<String> relatedReferences, EvidenceProvenance provenance,
            Map<String, String> extractionMetadata, int estimatedTokens,
            List<String> rankingReasons
    ) {
        this(layer, kind, reference, summary, occurredAt, score,
                relatedReferences, provenance, extractionMetadata,
                estimatedTokens, rankingReasons, null);
    }

    public int relevanceScore() {
        return score.finalScore();
    }

    public RepositoryEvidence withRanking(EvidenceScore score, List<String> reasons) {
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, extractionMetadata,
                estimatedTokens, reasons, content);
    }

    public RepositoryEvidence withExtractionMetadata(Map<String, String> metadata) {
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, metadata, estimatedTokens,
                rankingReasons, content);
    }

    public RepositoryEvidence withContent(RepositoryEvidenceContent value) {
        int tokens = estimatedTokens;
        if (value != null && value.text() != null) {
            tokens = Math.max(1, (summary.length() + reference.length()
                    + value.text().length() + 3) / 4);
        }
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, extractionMetadata, tokens,
                rankingReasons, value);
    }

    public record EvidenceProvenance(
            String sourceType,
            String repositoryLocation,
            String originatingFile,
            String identifier
    ) {
    }
}
