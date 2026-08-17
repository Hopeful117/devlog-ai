package com.hopeful117.devlogai.engineeringcontext;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;



public interface EngineeringContextFacade {
    EngineeringContext getEngineeringContext(String projectSlug,String intent);
}
