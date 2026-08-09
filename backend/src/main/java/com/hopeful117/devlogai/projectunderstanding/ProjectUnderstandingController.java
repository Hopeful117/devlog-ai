package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingRequest;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/understanding-executions")
@RequiredArgsConstructor
public class ProjectUnderstandingController {
    private final ProjectUnderstandingService service;

    @PostMapping
    public ResponseEntity<ProjectUnderstandingResponse> execute(
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectUnderstandingRequest request
    ) {
        return ResponseEntity.ok(service.execute(projectId, request));
    }
}
