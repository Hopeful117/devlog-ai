package com.hopeful117.devlogai.engineeringevent.execution;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/engineering-event-executions")
public class EngineeringEventExecutionController {
    private final EngineeringEventExecutionService service;
    @PostMapping
    ResponseEntity<EngineeringEventExecutionResponse> execute(
            @PathVariable UUID projectId, @Valid @RequestBody EngineeringEventExecutionRequest request) {
        return ResponseEntity.ok(service.execute(projectId, request));
    }
}
