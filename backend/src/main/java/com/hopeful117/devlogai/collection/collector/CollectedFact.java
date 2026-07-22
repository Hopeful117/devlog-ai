package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.fact.entity.FactType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record CollectedFact(
        FactType type,
        String content,
        String source,
        List<String> evidenceReferences,
        String fingerprint
) {
    public CollectedFact {
        Objects.requireNonNull(type, "type");
        content = requireText(content, "content");
        source = requireText(source, "source");
        evidenceReferences = evidenceReferences.stream()
                .map(reference -> requireText(reference, "evidence reference"))
                .distinct()
                .sorted()
                .toList();
        fingerprint = requireText(fingerprint, "fingerprint");
    }

    public static CollectedFact create(
            String collectorVersion,
            FactType type,
            String content,
            List<String> evidenceReferences,
            String resolvedRevision
    ) {
        List<String> normalizedEvidence = evidenceReferences.stream()
                .map(String::trim)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        String normalizedContent = content.replace("\r\n", "\n").trim();
        String identity = String.join("\u0000",
                collectorVersion,
                type.name(),
                normalizedContent,
                String.join("\u0001", normalizedEvidence),
                resolvedRevision
        );
        return new CollectedFact(
                type,
                normalizedContent,
                collectorVersion,
                normalizedEvidence,
                sha256(identity)
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
