package com.hopeful117.devlogai.contextmaintenance.controller;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceEvaluationService;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceFindingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/projects/{projectId}/maintenance-findings")
public class MaintenanceFindingController {

    private final MaintenanceFindingService service;
    private final MaintenanceEvaluationService evaluationService;

    @GetMapping
    public ResponseEntity<List<MaintenanceFindingResponse>> getByProject(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(service.getByProject(projectId));
    }

    @PostMapping("/evaluations")
    public ResponseEntity<MaintenanceEvaluationResponse> evaluate(
            @PathVariable UUID projectId
    ) {
        return ResponseEntity.ok(evaluationService.evaluate(projectId));
    }
}
