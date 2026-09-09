package com.hopeful117.devlogai.repositorycontext;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable repository snapshot scope for a single SCA execution.
 * Resolved once, propagated to all repository-context consumers.
 * Prevents independent source/revision resolution by participating collectors.
 */
public record RepositoryRevisionScope(
        UUID projectId,
        UUID sourceId,
        String resolvedRevision,
        Path workspacePath,
        String revisionSource
) {
    public RepositoryRevisionScope {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(resolvedRevision, "resolvedRevision");
        Objects.requireNonNull(revisionSource, "revisionSource");
        if (resolvedRevision.isBlank()) {
            throw new IllegalArgumentException("resolvedRevision must not be blank");
        }
        if (revisionSource.isBlank()) {
            throw new IllegalArgumentException("revisionSource must not be blank");
        }
        if (workspacePath != null) {
            workspacePath = workspacePath.toAbsolutePath().normalize();
        }
    }

    public static final String SOURCE_STORY_TARGET = "STORY_TARGET_COMMIT";
    public static final String SOURCE_ANALYSIS_TARGET = "ANALYSIS_TARGET_REVISION";
    public static final String SOURCE_CURRENT_REVISION = "SOURCE_CURRENT_REVISION";
    public static final String SOURCE_HEAD = "HEAD";
}
