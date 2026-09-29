package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentExecution;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentRequest;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class DevLogStoryAgentController {
    private final DevLogStoryAgentService devLogStoryAgentService;

    @PostMapping("/api/v1/projects/{projectSlug}/story-agent")
    public ResponseEntity<DevLogStoryAgentExecution> agentRequest(
            @PathVariable String projectSlug,
            @Valid @RequestBody AgentRequest payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        DevLogStoryAgentRequest request = new DevLogStoryAgentRequest(
                projectSlug,
                payload.storyId(),
                payload.intent(),
                payload.files(),
                payload.guidance(),
                idempotencyKey
        );

        DevLogStoryAgentExecution execution = devLogStoryAgentService.execute(request);
        if (execution.status() == AiTaskStatus.SUBMITTED || execution.status() == AiTaskStatus.PROCESSING) {
            return ResponseEntity.accepted().body(execution);
        }
        if (execution.status() == AiTaskStatus.COMPLETED) {
            return ResponseEntity.ok(execution);
        }
        return ResponseEntity.status(500).body(execution);
    }

    public record AgentRequest(
            UUID storyId,
            @NotBlank String intent,
            @NotNull List<@NotBlank String> files,
            Map<String, Object> guidance) {
    }
}
