package com.hopeful117.devlogai.ai.reference;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiReferenceMappingSnapshotTest {
    @Test
    void snapshotPreservesContractDigestBindingsAndCapabilities() {
        AiReferenceBinding fact = new AiReferenceBinding(
                new AiReference(AiReferenceType.FACT, "F001", AiReferenceScope.ANALYSIS_CONTEXT),
                "FACT", "fact-1", Set.of("SUPPORTING_FACT"));
        AiReferenceRegistry registry = new AiReferenceRegistry(List.of(fact));

        AiReferenceMappingSnapshot snapshot = AiReferenceMappingSnapshot.from(registry);

        assertThat(snapshot.contractVersion())
                .isEqualTo(AiReferenceMappingSnapshot.CONTRACT_VERSION);
        assertThat(snapshot.mappingDigest()).isEqualTo(registry.mappingDigest());
        assertThat(snapshot.bindings()).singleElement().satisfies(binding -> {
            assertThat(binding.type()).isEqualTo(AiReferenceType.FACT);
            assertThat(binding.ref()).isEqualTo("F001");
            assertThat(binding.scope()).isEqualTo(AiReferenceScope.ANALYSIS_CONTEXT);
            assertThat(binding.canonicalSourceIdentity()).isEqualTo("fact-1");
            assertThat(binding.groundingCapabilities()).containsExactly("SUPPORTING_FACT");
        });
        assertThat(snapshot.asMap()).containsKeys("contractVersion", "mappingDigest", "bindings");
    }
}
