package com.hopeful117.devlogai.repositorycontext;

import java.util.Objects;
import java.util.UUID;

/**
 * Canonical revision-aware document identity.
 * Format: document:{sourceId}:{normalizedPath}@{revision}
 * Deterministic, source-aware, path-aware, revision-aware.
 */
public record DocumentReference(
        UUID sourceId,
        String normalizedPath,
        String revision
) {
    public DocumentReference {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(normalizedPath, "normalizedPath");
        Objects.requireNonNull(revision, "revision");
        if (normalizedPath.isBlank()) {
            throw new IllegalArgumentException("normalizedPath must not be blank");
        }
        if (revision.isBlank()) {
            throw new IllegalArgumentException("revision must not be blank");
        }
    }

    public String toEvidenceReference() {
        return "document:" + sourceId + ":" + normalizedPath + "@" + revision;
    }

    public static DocumentReference parse(String evidenceReference) {
        if (evidenceReference == null || !evidenceReference.startsWith("document:")) {
            return null;
        }
        String remainder = evidenceReference.substring("document:".length());
        int atIndex = remainder.lastIndexOf('@');
        if (atIndex < 0) {
            return null;
        }
        String sourceAndPath = remainder.substring(0, atIndex);
        String revision = remainder.substring(atIndex + 1);
        int firstColon = sourceAndPath.indexOf(':');
        if (firstColon < 0) {
            return null;
        }
        String sourceIdStr = sourceAndPath.substring(0, firstColon);
        String path = sourceAndPath.substring(firstColon + 1);
        try {
            UUID sourceId = UUID.fromString(sourceIdStr);
            return new DocumentReference(sourceId, path, revision);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
