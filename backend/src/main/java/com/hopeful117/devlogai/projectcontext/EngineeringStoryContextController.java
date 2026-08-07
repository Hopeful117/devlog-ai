package com.hopeful117.devlogai.projectcontext;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EngineeringStoryContextController {

    private final EngineeringStoryContextService engineeringStoryContextService;

    @GetMapping("/api/projects/{projectId}/engineering-story-context")
    public ResponseEntity<EngineeringStoryContext> getEngineeringStoryContext(
            @PathVariable UUID projectId) {
        return ResponseEntity.ok(engineeringStoryContextService.build(projectId));
    }
}