package com.hopeful117.devlogai.projectcontext.controller;

import java.util.UUID;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.project.service.ProjectService;
import com.hopeful117.devlogai.projectcontext.ProjectContextProvider;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.projectcontext.mapper.ProjectContextContractMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectContextController {

    private final ProjectContextProvider projectContextProvider;
    private final ProjectContextContractMapper mapper;
    private final ProjectService projectService;

    @GetMapping("/{projectSlug}/context")
    public ResponseEntity<ProjectContext> getProjectContext(
            @PathVariable String projectSlug
    ) {
        UUID projectId = projectService.getBySlug(projectSlug).getId();
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        ProjectContext context = mapper.toContract(snapshot);

        return ResponseEntity.ok(context);
    }
}