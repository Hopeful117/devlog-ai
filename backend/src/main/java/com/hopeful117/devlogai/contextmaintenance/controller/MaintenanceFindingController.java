package com.hopeful117.devlogai.contextmaintenance.controller;

import com.hopeful117.devlogai.contextmaintenance.dto.request.MaintenanceFindingActionRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceEvaluationService;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceFindingService;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceRemediationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final MaintenanceRemediationService remediationService;

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

    @PostMapping("/{findingId}/acknowledgements")
    public ResponseEntity<MaintenanceFindingResponse> acknowledge(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId,
            @Valid @RequestBody MaintenanceFindingActionRequest request
    ) {
        return ResponseEntity.ok(service.acknowledge(projectId, findingId, request));
    }

    @PostMapping("/{findingId}/dismissals")
    public ResponseEntity<MaintenanceFindingResponse> dismiss(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId,
            @Valid @RequestBody MaintenanceFindingActionRequest request
    ) {
        return ResponseEntity.ok(service.dismiss(projectId, findingId, request));
    }

    @PostMapping("/{findingId}/resolutions")
    public ResponseEntity<MaintenanceFindingResponse> resolve(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId,
            @Valid @RequestBody MaintenanceFindingActionRequest request
    ) {
        return ResponseEntity.ok(service.resolve(projectId, findingId, request));
    }

    @PostMapping("/{findingId}/actions/refresh-projection")
    public ResponseEntity<MaintenanceFindingResponse> refreshProjection(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId,
            @Valid @RequestBody MaintenanceFindingActionRequest request
    ) {
        return ResponseEntity.ok(remediationService.refreshProjection(
                projectId, findingId, request.actedBy(), request.comment()));
    }

    @PostMapping("/{findingId}/actions/archive-context-input")
    public ResponseEntity<MaintenanceFindingResponse> archiveStaleHumanContext(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId,
            @Valid @RequestBody MaintenanceFindingActionRequest request
    ) {
        return ResponseEntity.ok(remediationService.archiveStaleHumanContext(
                projectId, findingId, request.actedBy(), request.comment()));
    }

    @PostMapping("/{findingId}/actions/refresh-missing-projection")
    public ResponseEntity<MaintenanceFindingResponse> refreshMissingProjection(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId,
            @Valid @RequestBody MaintenanceFindingActionRequest request
    ) {
        return ResponseEntity.ok(remediationService.refreshMissingProjection(
                projectId, findingId, request.actedBy(), request.comment()));
    }

    @PostMapping("/{findingId}/actions/refresh-understanding")
    public ResponseEntity<MaintenanceFindingResponse> refreshProjectUnderstanding(
            @PathVariable UUID projectId,
            @PathVariable UUID findingId,
            @Valid @RequestBody MaintenanceFindingActionRequest request
    ) {
        return ResponseEntity.ok(remediationService.refreshProjectUnderstanding(
                projectId, findingId, request.actedBy(), request.comment()));
    }
}
