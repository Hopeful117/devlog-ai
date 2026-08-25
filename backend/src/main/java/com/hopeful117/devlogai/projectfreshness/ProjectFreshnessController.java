package com.hopeful117.devlogai.projectfreshness;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/freshness-checks")
public class ProjectFreshnessController {
    private final ProjectFreshnessService service;

    @PostMapping
    public ProjectFreshnessResponse check(@PathVariable UUID projectId,
            @Valid @RequestBody ProjectFreshnessCheckRequest request) {
        return service.check(projectId, request.sourceId());
    }

    @GetMapping("/latest")
    public ResponseEntity<ProjectFreshnessResponse> latest(@PathVariable UUID projectId,
            @RequestParam UUID sourceId) {
        return service.latest(projectId, sourceId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/summary")
    public ProjectFreshnessSummary summary(@PathVariable UUID projectId) {
        return service.summary(projectId);
    }
}
