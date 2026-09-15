package com.hopeful117.devlogai.ai.reference;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, persistence-safe execution snapshot of a reference registry. */
public record AiReferenceMappingSnapshot(
        String contractVersion,
        String mappingDigest,
        List<BindingSnapshot> bindings,
        List<AiReference> architectureKnowledgeReferences
) {
    public static final String CONTRACT_VERSION = "AI_REFERENCE_MAPPING_V1";

    public AiReferenceMappingSnapshot {
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalArgumentException("contractVersion must not be blank");
        }
        if (mappingDigest == null || mappingDigest.isBlank()) {
            throw new IllegalArgumentException("mappingDigest must not be blank");
        }
        bindings = List.copyOf(bindings);
        architectureKnowledgeReferences = architectureKnowledgeReferences == null
                ? List.of() : List.copyOf(architectureKnowledgeReferences);
    }

    public AiReferenceResolver resolver() {
        return AiReferenceResolver.from(this);
    }

    public static AiReferenceMappingSnapshot from(AiReferenceRegistry registry) {
        return new AiReferenceMappingSnapshot(CONTRACT_VERSION, registry.mappingDigest(),
                registry.bindings().stream().map(BindingSnapshot::from).toList(),
                registry.architectureKnowledgeReferences().stream().sorted(
                        java.util.Comparator.comparing(AiReference::type)
                                .thenComparing(AiReference::scope).thenComparing(AiReference::ref))
                        .toList());
    }

    public AiReferenceMappingSnapshot(String contractVersion, String mappingDigest,
            List<BindingSnapshot> bindings) {
        this(contractVersion, mappingDigest, bindings, List.of());
    }

    public Map<String, Object> asMap() {
        return Map.of(
                "contractVersion", contractVersion,
                "mappingDigest", mappingDigest,
                "bindings", bindings.stream().map(BindingSnapshot::asMap).toList(),
                "architectureKnowledgeReferences", architectureKnowledgeReferences.stream()
                        .map(AiReferenceMappingSnapshot::referenceAsMap).toList());
    }

    private static Map<String, Object> referenceAsMap(AiReference reference) {
        return Map.of("type", reference.type().name(), "ref", reference.ref(),
                "scope", reference.scope().name());
    }

    public record BindingSnapshot(
            AiReferenceType type,
            String ref,
            AiReferenceScope scope,
            String canonicalSourceIdentity,
            Set<String> groundingCapabilities
    ) {
        public BindingSnapshot {
            groundingCapabilities = groundingCapabilities == null
                    ? Set.of() : Set.copyOf(groundingCapabilities);
        }

        private static BindingSnapshot from(AiReferenceBinding binding) {
            return new BindingSnapshot(binding.reference().type(), binding.reference().ref(),
                    binding.reference().scope(), binding.canonicalSourceIdentity(),
                    binding.groundingCapabilities());
        }

        private Map<String, Object> asMap() {
            return Map.of(
                    "type", type.name(),
                    "ref", ref,
                    "scope", scope.name(),
                    "canonicalSourceIdentity", canonicalSourceIdentity,
                    "groundingCapabilities", groundingCapabilities.stream().sorted().toList());
        }
    }
}
