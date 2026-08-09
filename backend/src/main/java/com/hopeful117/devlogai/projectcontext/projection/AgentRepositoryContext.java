package com.hopeful117.devlogai.projectcontext.projection;

import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

public record AgentRepositoryContext(
        String projectionVersion,
        String projectionPolicyId,
        String projectionPolicyVersion,
        String repositoryContextVersion,
        List<String> activeProfileKeys,
        String contextPlanVersion,
        List<String> resolvedRevisions,
        List<Evidence> evidence,
        Map<String, Integer> selectedByLayer,
        Map<String, Integer> selectedByKind,
        Map<String, Integer> rejectedByReason,
        int candidateCount,
        int selectedCount,
        int discardedCount,
        int duplicateCandidateCount,
        boolean truncated,
        List<String> warnings,
        String repositoryContextDigest,
        String projectionDigest,
        Accounting accounting
) {
    public static final String VERSION = "engineering-story-agent-projection-v1";

    public AgentRepositoryContext {
        activeProfileKeys = List.copyOf(activeProfileKeys);
        resolvedRevisions = List.copyOf(resolvedRevisions);
        evidence = List.copyOf(evidence);
        selectedByLayer = immutableMap(selectedByLayer);
        selectedByKind = immutableMap(selectedByKind);
        rejectedByReason = immutableMap(rejectedByReason);
        warnings = List.copyOf(warnings);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public record Evidence(
            String layer,
            String kind,
            String reference,
            String summary,
            Instant occurredAt,
            int relevanceScore,
            List<String> reasons,
            List<String> relatedReferences,
            Provenance provenance,
            Extraction extraction,
            Content content,
            Symbols symbols
    ) {
        public Evidence {
            reasons = List.copyOf(reasons);
            relatedReferences = List.copyOf(relatedReferences);
        }

        public Evidence withRelatedReferences(List<String> value) {
            return new Evidence(layer, kind, reference, summary, occurredAt,
                    relevanceScore, reasons, value, provenance, extraction, content, symbols);
        }

        public Evidence withReasons(List<String> value) {
            return new Evidence(layer, kind, reference, summary, occurredAt,
                    relevanceScore, value, relatedReferences, provenance, extraction,
                    content, symbols);
        }

        public Evidence withoutDeclarations() {
            return symbols == null ? this : new Evidence(layer, kind, reference,
                    summary, occurredAt, relevanceScore, reasons, relatedReferences,
                    provenance, extraction, content, symbols.withoutDeclarations());
        }

        public Evidence withoutContentText() {
            return content == null ? this : new Evidence(layer, kind, reference,
                    summary, occurredAt, relevanceScore, reasons, relatedReferences,
                    provenance, extraction, content.withoutText(), symbols);
        }
    }

    public record Provenance(
            String sourceType,
            String repositoryLocation,
            String originatingFile,
            String identifier
    ) { }

    public record Extraction(
            String collectorId,
            String collectorVersion,
            String resolvedRevision
    ) { }

    public record Content(
            String status,
            String text,
            String reason,
            String policyId,
            String policyVersion,
            String revision,
            String allocationPolicyId,
            String allocationPolicyVersion,
            Integer allocationRank
    ) {
        Content withoutText() {
            return new Content(status, null, reason, policyId, policyVersion,
                    revision, allocationPolicyId, allocationPolicyVersion,
                    allocationRank);
        }
    }

    public record Symbols(
            String status,
            String reason,
            String policyId,
            String policyVersion,
            String extractorId,
            String extractorVersion,
            String revision,
            Integer allocationRank,
            boolean truncated,
            int returnedSymbolCount,
            Integer availableSymbolCount,
            List<RepositoryEvidenceSymbols.JavaDeclaration> declarations
    ) {
        public Symbols {
            declarations = List.copyOf(declarations);
        }

        Symbols withoutDeclarations() {
            return new Symbols(status, reason, policyId, policyVersion,
                    extractorId, extractorVersion, revision, allocationRank,
                    truncated, returnedSymbolCount, availableSymbolCount, List.of());
        }
    }

    public record Accounting(
            int maximumBytes,
            int maximumEstimatedTokens,
            int canonicalBytes,
            int estimatedTokens,
            int removedRelatedReferences,
            int removedReasons,
            int removedDeclarationPayloads,
            int removedContentPayloads,
            int removedEvidenceItems
    ) { }
}
