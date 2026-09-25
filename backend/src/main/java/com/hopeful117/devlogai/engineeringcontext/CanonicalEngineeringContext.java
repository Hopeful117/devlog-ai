package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.EvidenceRef;
import com.hopeful117.devlogai.contracts.engineeringcontext.ContextRequestEcho;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextDiagnostics;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable Core-owned composition result; it contains no retrieval behavior. */
public record CanonicalEngineeringContext(
        EngineeringContext projection,
        RepositoryContext repositoryContext,
        String contextDigest,
        String contextVersion,
        ContextRequestEcho requestEcho,
        Map<String, Object> freshness,
        Map<String, Object> accounting,
        RepositoryContextDiagnostics diagnostics,
        Map<String, List<String>> relationsByReference,
        Map<String, RepositoryEvidence.EvidenceProvenance> provenanceByReference,
        Map<String, String> trustByReference,
        List<EvidenceRef> authorizedReferences) {
    public CanonicalEngineeringContext {
        if (projection == null) {
            throw new IllegalArgumentException("canonical context is required");
        }
        freshness = freshness == null ? Map.of() : Map.copyOf(freshness);
        accounting = accounting == null ? Map.of() : Map.copyOf(accounting);
        diagnostics = diagnostics == null ? RepositoryContextDiagnostics.empty() : diagnostics;
        relationsByReference = relationsByReference == null ? Map.of() : Map.copyOf(relationsByReference);
        provenanceByReference = provenanceByReference == null ? Map.of() : Map.copyOf(provenanceByReference);
        trustByReference = trustByReference == null ? Map.of() : Map.copyOf(trustByReference);
        authorizedReferences = authorizedReferences == null ? List.of() : List.copyOf(authorizedReferences);
        validateAuthorizedReferences(repositoryContext, authorizedReferences);
        if (repositoryContext != null) {
            contextDigest = CanonicalContextDigest.calculate(digestInputs(repositoryContext,
                    contextVersion, requestEcho, freshness, accounting, diagnostics,
                    relationsByReference, provenanceByReference, trustByReference, authorizedReferences));
        } else if (contextDigest == null || contextDigest.isBlank()) {
            throw new IllegalArgumentException("canonical context and digest are required");
        }
    }

    private static void validateAuthorizedReferences(RepositoryContext repositoryContext,
                                                      List<EvidenceRef> references) {
        if (references.stream().anyMatch(Objects::isNull)
                || references.stream().anyMatch(ref -> ref.reference() == null || ref.reference().isBlank())) {
            throw new IllegalArgumentException("authorizedReferences must contain non-blank typed references");
        }
        if (repositoryContext == null) return;
        var evidence = repositoryContext.evidence().stream().map(e -> e.reference())
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (references.stream().map(EvidenceRef::reference).distinct().anyMatch(ref -> !evidence.contains(ref))) {
            throw new IllegalArgumentException("authorizedReferences must be a subset of RepositoryContext.evidence");
        }
    }

    private static Map<String, Object> digestInputs(RepositoryContext repositoryContext,
                                                      String contextVersion, ContextRequestEcho requestEcho,
                                                      Map<String, Object> freshness, Map<String, Object> accounting,
                                                      RepositoryContextDiagnostics diagnostics,
                                                      Map<String, List<String>> relations,
                                                      Map<String, RepositoryEvidence.EvidenceProvenance> provenance,
                                                      Map<String, String> trust, List<EvidenceRef> authorized) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schema", "canonical-engineering-context-digest-v1");
        values.put("contextVersion", contextVersion);
        values.put("requestEcho", requestEcho);
        values.put("repositoryContext", repositoryDigestInputs(repositoryContext));
        values.put("freshness", freshness);
        values.put("accounting", accounting);
        values.put("diagnostics", diagnostics);
        values.put("relations", relations);
        values.put("provenance", provenance);
        values.put("trustByReference", trust);
        values.put("authorizedReferences", authorized);
        return values;
    }

    /** Core-owned repository data; deliberately excludes RepositoryContext.contextDigest to avoid recursion. */
    private static Map<String, Object> repositoryDigestInputs(RepositoryContext value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contextVersion", value.contextVersion());
        result.put("profile", value.profile());
        result.put("activeProfileKeys", value.activeProfileKeys());
        result.put("contextPlanVersion", value.contextPlanVersion());
        result.put("contextIntelligenceExplanations", value.contextIntelligenceExplanations());
        result.put("evidence", value.evidence());
        result.put("selectedByLayer", value.selectedByLayer());
        result.put("diagnostics", value.diagnostics());
        result.put("budget", value.budget());
        result.put("usedTokens", value.usedTokens());
        result.put("candidateCount", value.candidateCount());
        result.put("discardedCount", value.discardedCount());
        result.put("truncated", value.truncated());
        result.put("selectionDecisions", value.selectionDecisions());
        result.put("warnings", value.warnings());
        return result;
    }

    /** Compatibility accessor; raw references are confined to legacy envelopes. */
    public List<String> authorizedEvidenceReferences() {
        return authorizedReferences.stream().map(EvidenceRef::reference).toList();
    }

    /** Compatibility constructor retained for the additive rich-authority migration. */
    public CanonicalEngineeringContext(EngineeringContext projection, RepositoryContext repositoryContext,
            String contextDigest, String contextVersion, ContextRequestEcho requestEcho,
            Map<String, Object> freshness, Map<String, Object> accounting,
            Map<String, String> trustByReference, List<EvidenceRef> authorizedReferences) {
        this(projection, repositoryContext, contextDigest, contextVersion, requestEcho,
                freshness, accounting,
                repositoryContext == null ? RepositoryContextDiagnostics.empty() : repositoryContext.diagnostics(),
                repositoryContext == null ? Map.of() : relations(repositoryContext),
                repositoryContext == null ? Map.of() : provenance(repositoryContext),
                trustByReference, authorizedReferences);
    }

    private static Map<String, List<String>> relations(RepositoryContext repositoryContext) {
        return repositoryContext.evidence().stream().filter(e -> e.reference() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(RepositoryEvidence::reference,
                        e -> e.relatedReferences() == null ? List.of() : List.copyOf(e.relatedReferences()),
                        (left, right) -> left));
    }

    private static Map<String, RepositoryEvidence.EvidenceProvenance> provenance(RepositoryContext repositoryContext) {
        return repositoryContext.evidence().stream()
                .filter(e -> e.reference() != null && e.provenance() != null)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(RepositoryEvidence::reference,
                        RepositoryEvidence::provenance, (left, right) -> left));
    }

    /** Compatibility constructor for callers compiled against the first migration slice. */
    public CanonicalEngineeringContext(EngineeringContext projection, String contextDigest,
            String contextVersion, UUID storyId, List<String> references) {
        this(projection, null, contextDigest, contextVersion,
                projection == null ? null : projection.requestEcho(), Map.of(), Map.of(),
                RepositoryContextDiagnostics.empty(), Map.of(), Map.of(), Map.of(),
                references == null ? List.of() : references.stream()
                        .filter(java.util.Objects::nonNull)
                        .map(ref -> new EvidenceRef(ref, ref))
                        .toList());
    }
}
