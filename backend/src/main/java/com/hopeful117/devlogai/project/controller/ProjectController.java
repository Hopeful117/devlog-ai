package com.hopeful117.devlogai.project.controller;

import com.hopeful117.devlogai.project.dto.request.CreateProjectRequest;
import com.hopeful117.devlogai.project.dto.request.UpdateProjectRequest;
import com.hopeful117.devlogai.project.dto.response.ProjectResponse;
import com.hopeful117.devlogai.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projectService;

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request) {

        ProjectResponse response = projectService.create(request);

        URI location = URI.create("/api/v1/projects/" + response.getSlug());

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping
    public ResponseEntity <List<ProjectResponse>> getAll() {
        return ResponseEntity.ok( projectService.getAll());
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ProjectResponse> getBySlug(
            @PathVariable String slug) {

        return ResponseEntity.ok(
                projectService.getBySlug(slug)
        );
    }

    @PutMapping("/{slug}")
    public ResponseEntity<ProjectResponse> update(
            @PathVariable String slug,
            @Valid @RequestBody UpdateProjectRequest request) {

        return ResponseEntity.ok(
                projectService.update(slug, request)
        );
    }

    @PatchMapping("/{slug}/archive")
    public ResponseEntity<Void> archive(
            @PathVariable String slug) {

        projectService.archive(slug);

        return ResponseEntity.noContent().build();
    }
}
