package com.hopeful117.devlogai.ai.task.controller;

import com.hopeful117.devlogai.ai.task.dto.request.CreateAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.FailAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.request.SubmitAiTaskRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService aiTaskService;

    @PostMapping
    public ResponseEntity<AiTaskResponse> create(
            @Valid @RequestBody CreateAiTaskRequest request
    ) {
        AiTaskResponse response = aiTaskService.create(request);
        URI location = URI.create("/api/v1/ai-tasks/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AiTaskResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(aiTaskService.getById(id));
    }

    @GetMapping("/correlation/{correlationId}")
    public ResponseEntity<AiTaskResponse> getByCorrelationId(
            @PathVariable UUID correlationId
    ) {
        return ResponseEntity.ok(aiTaskService.getByCorrelationId(correlationId));
    }

    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<List<AiTaskResponse>> getByAnalysisId(
            @PathVariable UUID analysisId
    ) {
        return ResponseEntity.ok(aiTaskService.getByAnalysisId(analysisId));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<AiTaskResponse> submit(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitAiTaskRequest request
    ) {
        return ResponseEntity.ok(aiTaskService.submit(id, request));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<AiTaskResponse> startProcessing(@PathVariable UUID id) {
        return ResponseEntity.ok(aiTaskService.startProcessing(id));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<AiTaskResponse> complete(@PathVariable UUID id) {
        return ResponseEntity.ok(aiTaskService.complete(id));
    }

    @PostMapping("/{id}/fail")
    public ResponseEntity<AiTaskResponse> fail(
            @PathVariable UUID id,
            @Valid @RequestBody FailAiTaskRequest request
    ) {
        return ResponseEntity.ok(aiTaskService.fail(id, request));
    }
}
