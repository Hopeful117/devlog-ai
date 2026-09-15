package com.hopeful117.devlogai.ai.reference;

import java.util.Set;
import java.util.Objects;

public record AiReferenceBinding(
        AiReference reference,
        String domainEntityType,
        String canonicalSourceIdentity,
        Set<String> groundingCapabilities
) {
    public AiReferenceBinding {
        Objects.requireNonNull(reference, "reference");
        if (domainEntityType == null || domainEntityType.isBlank()) {
            throw new IllegalArgumentException("domainEntityType must not be blank");
        }
        if (canonicalSourceIdentity == null || canonicalSourceIdentity.isBlank()) {
            throw new IllegalArgumentException("canonicalSourceIdentity must not be blank");
        }
        groundingCapabilities = groundingCapabilities == null
                ? Set.of() : Set.copyOf(groundingCapabilities);
    }
}
