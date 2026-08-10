package com.hopeful117.devlogai.story.controller;

import com.hopeful117.devlogai.story.dto.request.CompleteStoryRequest;
import com.hopeful117.devlogai.story.dto.request.CreateEngineeringStoryRequest;
import com.hopeful117.devlogai.story.dto.request.StartStoryRequest;
import com.hopeful117.devlogai.story.dto.response.EngineeringStoryResponse;
import com.hopeful117.devlogai.story.service.EngineeringStoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/stories")
@RequiredArgsConstructor
public class EngineeringStoryController {

    private final EngineeringStoryService storyService;

    @PostMapping
    public ResponseEntity<EngineeringStoryResponse> register(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateEngineeringStoryRequest request) {

        request.setProjectId(projectId);
        EngineeringStoryResponse response = storyService.register(request);

        URI location = URI.create(
                "/api/v1/projects/" + projectId + "/stories/" + response.id());

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @PostMapping("/{storyId}/start")
    public ResponseEntity<EngineeringStoryResponse> startImplementation(
            @PathVariable UUID projectId,
            @PathVariable UUID storyId,
            @Valid @RequestBody StartStoryRequest request) {

        return ResponseEntity.ok(
                storyService.startImplementation(storyId, projectId, request));
    }

    @PostMapping("/{storyId}/complete")
    public ResponseEntity<EngineeringStoryResponse> complete(
            @PathVariable UUID projectId,
            @PathVariable UUID storyId,
            @Valid @RequestBody CompleteStoryRequest request) {

        return ResponseEntity.ok(
                storyService.complete(storyId, projectId, request));
    }

    @GetMapping("/{storyId}")
    public ResponseEntity<EngineeringStoryResponse> getById(
            @PathVariable UUID projectId,
            @PathVariable UUID storyId) {

        return ResponseEntity.ok(
                storyService.getById(storyId, projectId));
    }

    @GetMapping
    public ResponseEntity<List<EngineeringStoryResponse>> getByProject(
            @PathVariable UUID projectId) {

        return ResponseEntity.ok(
                storyService.getByProject(projectId));
    }
}