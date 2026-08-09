package com.hopeful117.devlogai.projectcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EngineeringStoryContextController {

    private final EngineeringStoryContextService engineeringStoryContextService;

    @GetMapping("/api/projects/{projectId}/engineering-story-context")
    public ResponseEntity<Object> getEngineeringStoryContext(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String detail) {
        return buildContext(projectId, description, detail);
    }

    @PostMapping(
            value = "/api/projects/{projectId}/engineering-story-context",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> createEngineeringStoryContext(
            @PathVariable UUID projectId,
            @RequestBody EngineeringStoryContextRequest request,
            @RequestParam(required = false) String detail) {
        return buildContext(projectId, request.description(), detail);
    }

    private ResponseEntity<Object> buildContext(
            UUID projectId, String description, String detail) {
        EngineeringStoryContextDetail mode = EngineeringStoryContextDetail.parse(detail);
        Object response = mode == EngineeringStoryContextDetail.FULL
                ? engineeringStoryContextService.buildWithRepositoryContext(
                        projectId, description)
                : engineeringStoryContextService.buildAgentWithRepositoryContext(
                        projectId, description);
        return ResponseEntity.ok(response);
    }
}
