package com.hopeful117.devlogai.collection.collector;

import com.hopeful117.devlogai.source.entity.SourceType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CollectionContext(
        UUID analysisId,
        UUID sourceId,
        UUID projectId,
        Path workspacePath,
        String resolvedRevision,
        SourceType sourceType,
        Instant collectionTimestamp
) {
    public CollectionContext {
        Objects.requireNonNull(analysisId, "analysisId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(collectionTimestamp, "collectionTimestamp");
        if (resolvedRevision == null || resolvedRevision.isBlank()) {
            throw new IllegalArgumentException("resolvedRevision must not be blank");
        }
        workspacePath = workspacePath.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspacePath)) {
            throw new IllegalArgumentException("workspacePath must be an accessible directory");
        }
        resolvedRevision = resolvedRevision.trim();
    }
}
