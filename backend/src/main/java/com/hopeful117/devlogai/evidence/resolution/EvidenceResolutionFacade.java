package com.hopeful117.devlogai.evidence.resolution;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Explicit Core seam; family resolvers retain ownership of authoritative models. */
@Component
public final class EvidenceResolutionFacade {
    private final CanonicalEvidenceReferenceParser parser;
    private final Map<EvidenceResolutionFamily, EvidenceFamilyResolver> resolvers;

    public EvidenceResolutionFacade(
            CanonicalEvidenceReferenceParser parser,
            List<EvidenceFamilyResolver> resolvers
    ) {
        this.parser = Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(resolvers, "resolvers");
        EnumMap<EvidenceResolutionFamily, EvidenceFamilyResolver> registered =
                new EnumMap<>(EvidenceResolutionFamily.class);
        for (EvidenceFamilyResolver resolver : resolvers) {
            Objects.requireNonNull(resolver, "resolver");
            if (registered.putIfAbsent(resolver.family(), resolver) != null) {
                throw new IllegalArgumentException(
                        "Multiple evidence resolvers registered for " + resolver.family());
            }
        }
        this.resolvers = Map.copyOf(registered);
    }

    public EvidenceResolutionResult resolve(EvidenceResolutionRequest request) {
        Objects.requireNonNull(request, "request");
        ParsedEvidenceReference parsed = parser.parse(request.reference());
        EvidenceFamilyResolver resolver = resolvers.get(parsed.family());
        if (resolver == null) {
            throw new EvidenceResolutionException(
                    EvidenceResolutionFailureCode.UNSUPPORTED_REFERENCE_TYPE,
                    parsed.canonicalReference(), parsed.sourceId(),
                    "No resolver is registered for " + parsed.family());
        }
        EvidenceResolutionResult result = resolver.resolve(parsed, request);
        validateResult(parsed, request, result);
        return result;
    }

    private void validateResult(
            ParsedEvidenceReference parsed,
            EvidenceResolutionRequest request,
            EvidenceResolutionResult result
    ) {
        Objects.requireNonNull(result, "resolver result");
        EvidenceResolutionMetadata metadata = result.metadata();
        if (!parsed.canonicalReference().equals(metadata.canonicalReference())
                || parsed.family() != metadata.family()
                || !Objects.equals(parsed.sourceId(), metadata.sourceId())
                || request.mode() != metadata.mode()) {
            throw new IllegalStateException(
                    "Evidence resolver returned metadata for a different resolution request");
        }
    }
}
