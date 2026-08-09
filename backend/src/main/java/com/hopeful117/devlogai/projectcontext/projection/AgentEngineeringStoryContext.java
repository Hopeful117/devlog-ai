package com.hopeful117.devlogai.projectcontext.projection;

import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;

import java.time.Instant;
import java.util.UUID;
import com.hopeful117.devlogai.projectfreshness.ProjectFreshnessSummary;

public record AgentEngineeringStoryContext(
        ProjectContextSnapshot projectContext,
        Instant generatedAt,
        UUID projectId,
        AgentRepositoryContext repositoryContext,
        ProjectFreshnessSummary freshness
) {
    public AgentEngineeringStoryContext(ProjectContextSnapshot projectContext, Instant generatedAt,
            UUID projectId, AgentRepositoryContext repositoryContext) {
        this(projectContext, generatedAt, projectId, repositoryContext, null);
    }
}
