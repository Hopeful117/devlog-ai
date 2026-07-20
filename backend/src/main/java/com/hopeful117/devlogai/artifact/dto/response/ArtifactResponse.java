package com.hopeful117.devlogai.artifact.dto.response;

import com.hopeful117.devlogai.artifact.entity.ArtifactType;

import java.time.Instant;
import java.util.UUID;

public record ArtifactResponse(
        UUID id,

        UUID projectId,

        String name,

        ArtifactType type,

        String path,

        String description,

        Instant createdAt,

        Instant updatedAt
) {
}
