package com.hopeful117.devlogai.ai.interactiontrace.controller;

import com.hopeful117.devlogai.ai.interactiontrace.dto.AiInteractionTraceResponse;
import com.hopeful117.devlogai.ai.interactiontrace.service.AiInteractionTraceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai-interaction-traces")
@RequiredArgsConstructor
public class AiInteractionTraceController {
    private final AiInteractionTraceQueryService queryService;

    @GetMapping("/task/{aiTaskId}")
    public ResponseEntity<List<AiInteractionTraceResponse>> byTask(@PathVariable UUID aiTaskId) {
        return ResponseEntity.ok(queryService.byTask(aiTaskId));
    }

    @GetMapping("/analysis/{analysisId}")
    public ResponseEntity<List<AiInteractionTraceResponse>> byAnalysis(@PathVariable UUID analysisId) {
        return ResponseEntity.ok(queryService.byAnalysis(analysisId));
    }
}
