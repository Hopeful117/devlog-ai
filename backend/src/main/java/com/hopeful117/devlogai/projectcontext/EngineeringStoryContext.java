package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.repositorycontext.RepositoryContext;

import java.time.Instant;
import java.util.UUID;

public record EngineeringStoryContext(
        ProjectContextSnapshot projectContext,
        Instant generatedAt,
        UUID projectId,
        RepositoryContext repositoryContext
) {
}