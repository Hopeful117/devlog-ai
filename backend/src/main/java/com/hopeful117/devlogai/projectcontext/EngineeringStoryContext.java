package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.repositorycontext.RepositoryContext;

import java.time.Instant;
import java.util.UUID;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;

public record EngineeringStoryContext(
        ProjectContextSnapshot projectContext,
        Instant generatedAt,
        UUID projectId,
        RepositoryContext repositoryContext,
        ProjectFreshnessSummary freshness
) {
    public EngineeringStoryContext(ProjectContextSnapshot projectContext, Instant generatedAt,
            UUID projectId, RepositoryContext repositoryContext) {
        this(projectContext, generatedAt, projectId, repositoryContext, null);
    }
}
