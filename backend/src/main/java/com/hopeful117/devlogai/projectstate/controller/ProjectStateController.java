package com.hopeful117.devlogai.projectstate.controller;

import com.hopeful117.devlogai.projectstate.dto.response.ProjectStateResponse;
import com.hopeful117.devlogai.projectstate.service.ProjectStateProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectStateController {

    private final ProjectStateProjectionService projectionService;

    @GetMapping("/{projectId}/state")
    public ProjectStateResponse getProjectState(
            @PathVariable UUID projectId
    ) {
        return projectionService.getProjectState(projectId);
    }
}
