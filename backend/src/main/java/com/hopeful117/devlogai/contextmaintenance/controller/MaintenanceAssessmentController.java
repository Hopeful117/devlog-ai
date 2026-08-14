package com.hopeful117.devlogai.contextmaintenance.controller;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceAssessmentRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceAssessmentResponse;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceAssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/maintenance-assessments")
public class MaintenanceAssessmentController {

    private final MaintenanceAssessmentService service;

    @GetMapping
    public ResponseEntity<List<MaintenanceAssessmentResponse>> getByProject(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(service.getByProject(projectId));
    }

    @GetMapping("/findings/{findingId}")
    public ResponseEntity<List<MaintenanceAssessmentResponse>> getByFinding(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId
    ) {
        return ResponseEntity.ok(service.getByFinding(projectId, findingId));
    }

    @PostMapping
    public ResponseEntity<MaintenanceAssessmentResponse> create(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateMaintenanceAssessmentRequest request
    ) {
        return ResponseEntity.ok(service.create(projectId, request));
    }
}
