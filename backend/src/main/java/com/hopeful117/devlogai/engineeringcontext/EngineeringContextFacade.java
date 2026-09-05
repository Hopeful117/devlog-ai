package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;

import java.util.List;
import java.util.UUID;

public interface EngineeringContextFacade {
    EngineeringContext getEngineeringContext(
            String projectSlug,
            String intent,
            List<String> files,
            UUID storyId
    );
}