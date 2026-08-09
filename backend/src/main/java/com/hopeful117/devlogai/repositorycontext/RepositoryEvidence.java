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
        RepositoryEvidenceContent content,
        RepositoryEvidenceSymbols symbols
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
                estimatedTokens, rankingReasons, null, null);
    }

    public RepositoryEvidence(
            RepositoryContextLayer layer, String kind, String reference,
            String summary, Instant occurredAt, EvidenceScore score,
            List<String> relatedReferences, EvidenceProvenance provenance,
            Map<String, String> extractionMetadata, int estimatedTokens,
            List<String> rankingReasons, RepositoryEvidenceContent content
    ) {
        this(layer, kind, reference, summary, occurredAt, score,
                relatedReferences, provenance, extractionMetadata,
                estimatedTokens, rankingReasons, content, null);
    }

    public int relevanceScore() {
        return score.finalScore();
    }

    public RepositoryEvidence withRanking(EvidenceScore score, List<String> reasons) {
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, extractionMetadata,
                estimatedTokens, reasons, content, symbols);
    }

    public RepositoryEvidence withExtractionMetadata(Map<String, String> metadata) {
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, metadata, estimatedTokens,
                rankingReasons, content, symbols);
    }

    public RepositoryEvidence withContent(RepositoryEvidenceContent value) {
        int tokens = estimateTokens(value, symbols);
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, extractionMetadata, tokens,
                rankingReasons, value, symbols);
    }

    public RepositoryEvidence withSymbols(RepositoryEvidenceSymbols value) {
        int tokens = estimateTokens(content, value);
        return new RepositoryEvidence(layer, kind, reference, summary, occurredAt,
                score, relatedReferences, provenance, extractionMetadata, tokens,
                rankingReasons, content, value);
    }

    private int estimateTokens(
            RepositoryEvidenceContent contentValue,
            RepositoryEvidenceSymbols symbolValue
    ) {
        int characters = summary.length() + reference.length()
                + contentCharacters(contentValue) + symbolCharacters(symbolValue);
        return Math.max(estimatedTokens, Math.max(1, (characters + 3) / 4));
    }

    private int contentCharacters(RepositoryEvidenceContent value) {
        if (value == null) return 0;
        int total = java.util.stream.Stream.of(
                        value.text(), value.reason(), value.policyId(),
                        value.policyVersion(), value.revision(),
                        value.allocationPolicyId(), value.allocationPolicyVersion())
                .filter(java.util.Objects::nonNull)
                .mapToInt(String::length).sum();
        return total + value.allocationReasons().stream().mapToInt(String::length).sum();
    }

    private int symbolCharacters(RepositoryEvidenceSymbols value) {
        if (value == null) return 0;
        int total = java.util.stream.Stream.of(value.reason(), value.policyId(),
                        value.policyVersion(), value.extractorId(),
                        value.extractorVersion(), value.revision())
                .filter(java.util.Objects::nonNull).mapToInt(String::length).sum();
        total += value.allocationReasons().stream().mapToInt(String::length).sum();
        for (RepositoryEvidenceSymbols.JavaDeclaration declaration
                : value.declarations()) {
            total += java.util.stream.Stream.of(declaration.name(),
                            declaration.owningType(), declaration.returnType())
                    .filter(java.util.Objects::nonNull)
                    .mapToInt(String::length).sum();
            total += declaration.modifiers().stream().mapToInt(String::length).sum();
            total += declaration.annotations().stream().mapToInt(String::length).sum();
            total += declaration.parameters().stream().mapToInt(parameter ->
                    parameter.type().length() + parameter.name().length()).sum();
            total += 32;
        }
        return total;
    }

    public record EvidenceProvenance(
            String sourceType,
            String repositoryLocation,
            String originatingFile,
            String identifier
    ) {
    }
}
