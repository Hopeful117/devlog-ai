package com.hopeful117.devlogai.evidence.resolution;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalEvidenceReferenceParserTest {
    private static final UUID SOURCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final CanonicalEvidenceReferenceParser parser = new CanonicalEvidenceReferenceParser();

    @Test
    void parsesCanonicalGitReference() {
        var reference = "git:" + SOURCE_ID + ":a1b2c3d4";

        var parsed = parser.parse(reference);

        assertThat(parsed.canonicalReference()).isEqualTo(reference);
        assertThat(parsed.family()).isEqualTo(EvidenceResolutionFamily.GIT_COMMIT);
        assertThat(parsed.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(parsed.revision()).isEqualTo("a1b2c3d4");
        assertThat(parsed.pathOrIdentity()).isEqualTo("a1b2c3d4");
    }

    @Test
    void parsesCanonicalPinnedDocumentReference() {
        var reference = "document:" + SOURCE_ID + ":docs/adr.md@deadbeef";

        var parsed = parser.parse(reference);

        assertThat(parsed.family()).isEqualTo(EvidenceResolutionFamily.DOCUMENT);
        assertThat(parsed.sourceId()).isEqualTo(SOURCE_ID);
        assertThat(parsed.revision()).isEqualTo("deadbeef");
        assertThat(parsed.pathOrIdentity()).isEqualTo("docs/adr.md");
    }

    @Test
    void parsesCanonicalFactReference() {
        var factId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        var parsed = parser.parse("fact:" + factId);

        assertThat(parsed.family()).isEqualTo(EvidenceResolutionFamily.FACT);
        assertThat(parsed.sourceId()).isNull();
        assertThat(parsed.revision()).isNull();
        assertThat(parsed.pathOrIdentity()).isEqualTo(factId.toString());
    }

    @Test
    void parsesAllSliceSixPersistedFamilies() {
        UUID id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        assertThat(parser.parse("decision:" + id).family())
                .isEqualTo(EvidenceResolutionFamily.DECISION);
        assertThat(parser.parse("event:" + id).family())
                .isEqualTo(EvidenceResolutionFamily.ENGINEERING_EVENT);
        assertThat(parser.parse("story:" + id).family())
                .isEqualTo(EvidenceResolutionFamily.STORY);
        assertThat(parser.parse("analysis:" + id).family())
                .isEqualTo(EvidenceResolutionFamily.ANALYSIS);
        assertThat(parser.parse("artifact:" + id).family())
                .isEqualTo(EvidenceResolutionFamily.ARTIFACT);
        assertThat(parser.parse("challenge:" + id).family())
                .isEqualTo(EvidenceResolutionFamily.CHALLENGE);
    }

    @Test
    void rejectsUnknownUnsupportedAndMalformedReferencesWithStableCodes() {
        assertCode("mystery:value", EvidenceResolutionFailureCode.UNKNOWN_REFERENCE);
        assertCode("file:" + SOURCE_ID + ":README.md@deadbeef",
                EvidenceResolutionFailureCode.UNSUPPORTED_REFERENCE_TYPE);
        assertCode("document:" + SOURCE_ID + ":docs/adr.md",
                EvidenceResolutionFailureCode.UNKNOWN_REFERENCE);
        assertCode("git:not-a-uuid:a1b2c3d4", EvidenceResolutionFailureCode.UNKNOWN_REFERENCE);
        assertCode("fact:not-a-uuid", EvidenceResolutionFailureCode.UNKNOWN_REFERENCE);
    }

    private void assertCode(String reference, EvidenceResolutionFailureCode code) {
        assertThatThrownBy(() -> parser.parse(reference))
                .isInstanceOf(EvidenceResolutionException.class)
                .extracting("code")
                .isEqualTo(code);
    }
}
