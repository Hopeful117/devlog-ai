package com.hopeful117.devlogai.projectstate.service;

import com.hopeful117.devlogai.projectstate.dto.response.ProjectStateResponse;

import java.util.UUID;

public interface ProjectStateProjectionService {

    ProjectStateResponse getProjectState(UUID projectId);
}
