package com.hopeful117.devlogai.ai.reference;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable, persistence-safe execution snapshot of a reference registry. */
public record AiReferenceMappingSnapshot(
        String contractVersion,
        String mappingDigest,
        List<BindingSnapshot> bindings
) {
    public static final String CONTRACT_VERSION = "AI_REFERENCE_MAPPING_V1";

    public AiReferenceMappingSnapshot {
        bindings = List.copyOf(bindings);
    }

    public static AiReferenceMappingSnapshot from(AiReferenceRegistry registry) {
        return new AiReferenceMappingSnapshot(CONTRACT_VERSION, registry.mappingDigest(),
                registry.bindings().stream().map(BindingSnapshot::from).toList());
    }

    public Map<String, Object> asMap() {
        return Map.of(
                "contractVersion", contractVersion,
                "mappingDigest", mappingDigest,
                "bindings", bindings.stream().map(BindingSnapshot::asMap).toList());
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
