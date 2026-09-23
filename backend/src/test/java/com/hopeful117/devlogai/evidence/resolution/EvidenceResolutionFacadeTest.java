package com.hopeful117.devlogai.evidence.resolution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceResolutionFacadeTest {
    private static final UUID SOURCE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void dispatchesParsedFamilyWithoutChangingReferenceOrMode() {
        var resolver = new RecordingResolver(EvidenceResolutionFamily.GIT_COMMIT);
        var facade = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(), List.of(resolver));
        var request = new EvidenceResolutionRequest(
                "git:" + SOURCE_ID + ":a1b2c3d4", EvidenceResolutionMode.TASK_SNAPSHOT);

        var result = facade.resolve(request);

        assertThat(result.metadata().canonicalReference()).isEqualTo(request.reference());
        assertThat(result.metadata().family()).isEqualTo(EvidenceResolutionFamily.GIT_COMMIT);
        assertThat(result.metadata().sourceId()).isEqualTo(SOURCE_ID);
        assertThat(result.metadata().mode()).isEqualTo(EvidenceResolutionMode.TASK_SNAPSHOT);
        assertThat(resolver.request).isEqualTo(request);
    }

    @Test
    void failsWhenAParsedFamilyHasNoRegisteredResolverInsteadOfFallingBack() {
        var facade = new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(), List.of());

        assertThatThrownBy(() -> facade.resolve(new EvidenceResolutionRequest(
                "document:" + SOURCE_ID + ":docs/adr.md@deadbeef",
                EvidenceResolutionMode.CURRENT)))
                .isInstanceOf(EvidenceResolutionException.class)
                .extracting("code")
                .isEqualTo(EvidenceResolutionFailureCode.UNSUPPORTED_REFERENCE_TYPE);
    }

    @Test
    void rejectsDuplicateFamilyRegistration() {
        var first = new RecordingResolver(EvidenceResolutionFamily.DOCUMENT);
        var second = new RecordingResolver(EvidenceResolutionFamily.DOCUMENT);

        assertThatThrownBy(() -> new EvidenceResolutionFacade(
                new CanonicalEvidenceReferenceParser(), List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Multiple evidence resolvers");
    }

    private static final class RecordingResolver implements EvidenceFamilyResolver {
        private final EvidenceResolutionFamily family;
        private EvidenceResolutionRequest request;

        private RecordingResolver(EvidenceResolutionFamily family) {
            this.family = family;
        }

        @Override
        public EvidenceResolutionFamily family() {
            return family;
        }

        @Override
        public EvidenceResolutionResult resolve(
                ParsedEvidenceReference reference,
                EvidenceResolutionRequest request
        ) {
            this.request = request;
            return new EvidenceResolutionResult(
                    new EvidenceResolutionMetadata(
                            reference.canonicalReference(), reference.family(),
                            reference.sourceId(), "test", reference.revision(),
                            request.mode(), false),
                    new TestPayload());
        }
    }

    private record TestPayload() implements EvidenceResolutionPayload {
    }
}
