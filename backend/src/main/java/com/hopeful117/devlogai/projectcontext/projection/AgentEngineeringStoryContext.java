package com.hopeful117.devlogai.projectcontext.projection;

import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;

import java.time.Instant;
import java.util.UUID;

public record AgentEngineeringStoryContext(
        ProjectContextSnapshot projectContext,
        Instant generatedAt,
        UUID projectId,
        AgentRepositoryContext repositoryContext
) { }
