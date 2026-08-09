package com.hopeful117.devlogai.projectcontext;

import com.hopeful117.devlogai.projectcontext.projection.AgentEngineeringStoryContext;

import java.util.UUID;

public interface EngineeringStoryContextService {

    EngineeringStoryContext build(UUID projectId);

    EngineeringStoryContext buildWithRepositoryContext(
            UUID projectId, String storyDescription);

    AgentEngineeringStoryContext buildAgentWithRepositoryContext(
            UUID projectId, String storyDescription);
}
