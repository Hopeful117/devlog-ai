package com.hopeful117.devlogai.source.dto.response;

import com.hopeful117.devlogai.source.entity.GitProvider;
import com.hopeful117.devlogai.source.entity.SourceType;

import java.time.Instant;
import java.util.UUID;

public record SourceResponse(
        UUID id,
        UUID projectId,
        SourceType type,
        String name,
        String repositoryUrl,
        String defaultBranch,
        GitProvider provider,
        boolean active,
        Instant lastSynchronizedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
