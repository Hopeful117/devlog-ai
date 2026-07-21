package com.hopeful117.devlogai.ai.engine.controller;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/tasks")
@RequiredArgsConstructor
public class AiTaskResultController {

    private final AiTaskResultService aiTaskResultService;

    @PostMapping("/{correlationId}/result")
    public ResponseEntity<AiTaskResultAcknowledgement> receiveResult(
            @PathVariable UUID correlationId,
            @Valid @RequestBody AiTaskResultRequest request
    ) {
        return ResponseEntity.ok(aiTaskResultService.handle(correlationId, request));
    }
}
