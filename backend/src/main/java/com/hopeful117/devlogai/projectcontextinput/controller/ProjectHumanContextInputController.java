package com.hopeful117.devlogai.projectcontextinput.controller;

import com.hopeful117.devlogai.projectcontextinput.dto.request.CreateProjectHumanContextInputRequest;
import com.hopeful117.devlogai.projectcontextinput.dto.response.ProjectHumanContextInputResponse;
import com.hopeful117.devlogai.projectcontextinput.service.ProjectHumanContextInputService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/context-inputs")
public class ProjectHumanContextInputController {

    private final ProjectHumanContextInputService service;

    @PostMapping
    public ResponseEntity<ProjectHumanContextInputResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateProjectHumanContextInputRequest request
    ) {
        ProjectHumanContextInputResponse response = service.create(projectId, request);
        return ResponseEntity.created(URI.create(
                "/api/v1/projects/" + projectId + "/context-inputs/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProjectHumanContextInputResponse>> getByProject(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(service.getByProject(projectId));
    }

    @PatchMapping("/{inputId}/archive")
    public ResponseEntity<ProjectHumanContextInputResponse> archive(
            @PathVariable UUID projectId,
            @PathVariable UUID inputId
    ) {
        return ResponseEntity.ok(service.archive(projectId, inputId));
    }
}
