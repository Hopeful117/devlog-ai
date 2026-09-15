package com.hopeful117.devlogai.ai.reference;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiReferenceResolverTest {
    @Test
    void resolvesOnlyTheExactTypedReference() {
        var fact = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact-a", "F001", Set.of("SUPPORTING_FACT"));
        var resolver = new AiReferenceMappingSnapshot("AI_REFERENCE_MAPPING_V1", "digest",
                List.of(snapshot(fact))).resolver();

        assertThat(resolver.resolve(new AiReference(AiReferenceType.FACT, "F001",
                AiReferenceScope.ANALYSIS_CONTEXT))).isEqualTo(snapshot(fact));
        assertThat(resolver.resolve(new AiReference(AiReferenceType.FACT, "F001",
                AiReferenceScope.ANALYSIS_CONTEXT), "SUPPORTING_FACT")).isEqualTo(snapshot(fact));
    }

    @Test
    void rejectsNamespaceScopeAndCapabilityMismatches() {
        var fact = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact-a", "F001", Set.of("SUPPORTING_FACT"));
        var resolver = new AiReferenceMappingSnapshot("AI_REFERENCE_MAPPING_V1", "digest",
                List.of(snapshot(fact))).resolver();

        assertCode(resolver, new AiReference(AiReferenceType.OBSERVATION, "F001",
                AiReferenceScope.ANALYSIS_CONTEXT), "REFERENCE_NAMESPACE_MISMATCH");
        assertCode(resolver, new AiReference(AiReferenceType.FACT, "F001",
                AiReferenceScope.PROJECT), "REFERENCE_SCOPE_MISMATCH");
        assertCode(resolver, new AiReference(AiReferenceType.FACT, "F002",
                AiReferenceScope.ANALYSIS_CONTEXT), "UNKNOWN_AI_REFERENCE");
        assertThatThrownBy(() -> resolver.resolve(new AiReference(AiReferenceType.FACT, "F001",
                AiReferenceScope.ANALYSIS_CONTEXT), "EVIDENCE_REFERENCE"))
                .isInstanceOf(AiReferenceResolutionException.class)
                .extracting("code").isEqualTo("REFERENCE_NOT_ALLOWED_FOR_GROUNDING");
    }

    @Test
    void reconstructsTheSameReverseViewFromJsonMap() {
        var fact = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact-a", "F001", Set.of("SUPPORTING_FACT"));
        var snapshot = new AiReferenceMappingSnapshot("AI_REFERENCE_MAPPING_V1", "digest",
                List.of(snapshot(fact)));

        var reconstructed = AiReferenceResolver.fromMap(snapshot.asMap());

        assertThat(reconstructed.mappingDigest()).isEqualTo(snapshot.mappingDigest());
        assertThat(reconstructed.resolve(fact.reference())).isEqualTo(
                new AiReferenceMappingSnapshot.BindingSnapshot(
                        AiReferenceType.FACT, "F001", AiReferenceScope.ANALYSIS_CONTEXT,
                        "fact-a", Set.of("SUPPORTING_FACT")));
    }

    @Test
    void rejectsConflictingProviderTokenClaims() {
        var first = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact-a", "F001", Set.of("SUPPORTING_FACT"));
        var second = binding(AiReferenceType.FACT, AiReferenceScope.ANALYSIS_CONTEXT,
                "fact-b", "F001", Set.of("SUPPORTING_FACT"));

        assertThatThrownBy(() -> new AiReferenceRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicting AI reference token");
        assertThatThrownBy(() -> AiReferenceResolver.fromMap(Map.of(
                "contractVersion", "AI_REFERENCE_MAPPING_V1",
                "mappingDigest", "digest",
                "bindings", List.of(firstSnapshot(first), firstSnapshot(second)))))
                .isInstanceOf(AiReferenceResolutionException.class)
                .hasMessageContaining("Conflicting bindings");
    }

    private void assertCode(AiReferenceResolver resolver, AiReference reference, String code) {
        assertThatThrownBy(() -> resolver.resolve(reference))
                .isInstanceOf(AiReferenceResolutionException.class)
                .extracting("code").isEqualTo(code);
    }

    private Map<String, Object> firstSnapshot(AiReferenceBinding binding) {
        return new AiReferenceMappingSnapshot("AI_REFERENCE_MAPPING_V1", "digest",
                List.of(snapshot(binding))).asMap().entrySet().stream()
                .filter(entry -> entry.getKey().equals("bindings"))
                .findFirst().map(entry -> ((List<Map<String, Object>>) entry.getValue()).get(0))
                .orElseThrow();
    }

    private AiReferenceMappingSnapshot.BindingSnapshot snapshot(AiReferenceBinding binding) {
        return new AiReferenceMappingSnapshot.BindingSnapshot(binding.reference().type(),
                binding.reference().ref(), binding.reference().scope(),
                binding.canonicalSourceIdentity(), binding.groundingCapabilities());
    }

    private AiReferenceBinding binding(AiReferenceType type, AiReferenceScope scope,
            String identity, String reference, Set<String> capabilities) {
        return new AiReferenceBinding(new AiReference(type, reference, scope),
                type.name(), identity, capabilities);
    }
}
