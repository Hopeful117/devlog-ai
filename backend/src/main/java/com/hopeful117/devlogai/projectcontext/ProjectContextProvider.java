package com.hopeful117.devlogai.projectcontext;

import java.util.UUID;

public interface ProjectContextProvider {

    ProjectContextSnapshot build(UUID projectId);
}
