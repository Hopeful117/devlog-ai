package com.hopeful117.devlogai.deliverable.controller;

import com.hopeful117.devlogai.deliverable.dto.CreateDeliverableRequest;
import com.hopeful117.devlogai.deliverable.dto.DeliverableResponse;
import com.hopeful117.devlogai.deliverable.service.DeliverableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliverables")
@RequiredArgsConstructor
public class DeliverableController {
    private final DeliverableService service;

    @PostMapping
    public ResponseEntity<DeliverableResponse> generate(@Valid @RequestBody CreateDeliverableRequest request) {
        DeliverableResponse response = service.generate(request);
        return ResponseEntity.created(URI.create("/api/v1/deliverables/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DeliverableResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<DeliverableResponse>> byProject(@PathVariable UUID projectId) {
        return ResponseEntity.ok(service.getByProject(projectId));
    }

    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<List<DeliverableResponse>> byAnalysis(@PathVariable UUID analysisId) {
        return ResponseEntity.ok(service.getByAnalysis(analysisId));
    }
}
