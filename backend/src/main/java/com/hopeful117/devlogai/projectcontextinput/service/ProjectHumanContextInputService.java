package com.hopeful117.devlogai.projectcontextinput.service;

import com.hopeful117.devlogai.projectcontextinput.dto.request.CreateProjectHumanContextInputRequest;
import com.hopeful117.devlogai.projectcontextinput.dto.response.ProjectHumanContextInputResponse;

import java.util.List;
import java.util.UUID;

public interface ProjectHumanContextInputService {

    ProjectHumanContextInputResponse create(UUID projectId, CreateProjectHumanContextInputRequest request);

    List<ProjectHumanContextInputResponse> getByProject(UUID projectId);

    ProjectHumanContextInputResponse archive(UUID projectId, UUID inputId);
}
