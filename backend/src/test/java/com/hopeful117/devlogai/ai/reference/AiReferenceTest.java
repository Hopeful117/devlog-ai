package com.hopeful117.devlogai.ai.reference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiReferenceTest {

    private static final AiReferenceType TYPE = AiReferenceType.FACT;
    private static final AiReferenceScope SCOPE = AiReferenceScope.ANALYSIS_CONTEXT;

    @Test
    void acceptsValidReference() {
        AiReference reference = new AiReference(TYPE, "F001", SCOPE);

        assertEquals(TYPE, reference.type());
        assertEquals("F001", reference.ref());
        assertEquals(SCOPE, reference.scope());
    }

    @Test
    void rejectsNullType() {
        assertThrows(NullPointerException.class,
                () -> new AiReference(null, "F001", SCOPE));
    }

    @Test
    void rejectsNullReferenceToken() {
        assertThrows(NullPointerException.class,
                () -> new AiReference(TYPE, null, SCOPE));
    }

    @Test
    void rejectsNullScope() {
        assertThrows(NullPointerException.class,
                () -> new AiReference(TYPE, "F001", null));
    }

    @Test
    void rejectsBlankReferenceToken() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiReference(TYPE, "  ", SCOPE));
    }

    @Test
    void hasStructuralValueEquality() {
        assertEquals(
                new AiReference(TYPE, "F001", SCOPE),
                new AiReference(TYPE, "F001", SCOPE));
    }

    @Test
    void typeIsPartOfReferenceIdentity() {
        assertNotEquals(
                new AiReference(AiReferenceType.FACT, "001", SCOPE),
                new AiReference(AiReferenceType.OBSERVATION, "001", SCOPE));
    }

    @Test
    void scopeIsPartOfReferenceIdentity() {
        assertNotEquals(
                new AiReference(TYPE, "001", AiReferenceScope.ANALYSIS_CONTEXT),
                new AiReference(TYPE, "001", AiReferenceScope.PROJECT));
    }

    @Test
    void tokenIsPartOfReferenceIdentity() {
        assertNotEquals(
                new AiReference(TYPE, "001", SCOPE),
                new AiReference(TYPE, "002", SCOPE));
    }
}
