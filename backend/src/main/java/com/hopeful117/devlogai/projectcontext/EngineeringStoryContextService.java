package com.hopeful117.devlogai.projectcontext;

import java.util.UUID;

public interface EngineeringStoryContextService {

    EngineeringStoryContext build(UUID projectId);
}