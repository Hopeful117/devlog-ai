package com.hopeful117.devlogai.repositorycontext;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DocumentReferenceTest {

    private static final UUID SOURCE_ID = UUID.randomUUID();

    @Test
    void createsCanonicalIdentity() {
        DocumentReference ref = new DocumentReference(SOURCE_ID, "docs/decisions/ADR-063.md", "abc123");

        assertEquals("document:" + SOURCE_ID + ":docs/decisions/ADR-063.md@abc123", ref.toEvidenceReference());
        assertEquals("docs/decisions/ADR-063.md", ref.normalizedPath());
        assertEquals("abc123", ref.revision());
    }

    @Test
    void convertsToEvidenceReference() {
        DocumentReference ref = new DocumentReference(SOURCE_ID, "docs/stories/0118/story.md", "def456");

        String evidenceRef = ref.toEvidenceReference();

        assertTrue(evidenceRef.startsWith("document:"));
        assertTrue(evidenceRef.contains("docs/stories/0118/story.md"));
        assertTrue(evidenceRef.endsWith("@def456"));
    }

    @Test
    void parsesCanonicalId() {
        String canonicalId = "document:" + SOURCE_ID + ":docs/decisions/ADR-063.md@abc123";

        DocumentReference ref = DocumentReference.parse(canonicalId);

        assertNotNull(ref);
        assertEquals("docs/decisions/ADR-063.md", ref.normalizedPath());
        assertEquals("abc123", ref.revision());
    }

    @Test
    void parseReturnsNullForInvalidFormat() {
        assertNull(DocumentReference.parse(null));
        assertNull(DocumentReference.parse(""));
        assertNull(DocumentReference.parse("invalid-format"));
    }

    @Test
    void equalsAndHashCodeByAllFields() {
        DocumentReference ref1 = new DocumentReference(SOURCE_ID, "path/file.md", "rev1");
        DocumentReference ref2 = new DocumentReference(SOURCE_ID, "path/file.md", "rev1");

        assertEquals(ref1, ref2);
        assertEquals(ref1.hashCode(), ref2.hashCode());
    }

    @Test
    void notEqualWithDifferentRevision() {
        DocumentReference ref1 = new DocumentReference(SOURCE_ID, "path/file.md", "rev1");
        DocumentReference ref2 = new DocumentReference(SOURCE_ID, "path/file.md", "rev2");

        assertNotEquals(ref1, ref2);
    }

    @Test
    void rejectsNullSourceId() {
        assertThrows(NullPointerException.class,
                () -> new DocumentReference(null, "path/file.md", "rev1"));
    }

    @Test
    void rejectsBlankPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentReference(SOURCE_ID, "", "rev1"));
    }

    @Test
    void rejectsBlankRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> new DocumentReference(SOURCE_ID, "path/file.md", ""));
    }
}
