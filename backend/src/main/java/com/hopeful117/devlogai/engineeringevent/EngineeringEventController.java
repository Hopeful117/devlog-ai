package com.hopeful117.devlogai.engineeringevent;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class EngineeringEventController {
    private final EngineeringEventQueryService service;
    @GetMapping("/api/v1/projects/{projectId}/engineering-events")
    ResponseEntity<EngineeringEventPageResponse> byProject(@PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(service.byProject(projectId, page, size));
    }
    @GetMapping("/api/v1/engineering-events/{id}")
    ResponseEntity<EngineeringEventResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }
}
