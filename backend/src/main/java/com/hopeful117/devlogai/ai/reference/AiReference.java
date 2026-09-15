package com.hopeful117.devlogai.ai.reference;

import java.util.Objects;

public record AiReference(
        AiReferenceType type,
        String ref,
        AiReferenceScope scope
) {
    public AiReference {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(ref, "ref");
        Objects.requireNonNull(scope, "scope");
        if (ref.isBlank()) {
            throw new IllegalArgumentException("ref must not be blank");
        }
    }
}
