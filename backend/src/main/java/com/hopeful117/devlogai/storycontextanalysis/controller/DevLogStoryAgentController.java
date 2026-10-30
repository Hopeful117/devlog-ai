package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentExecution;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentRequest;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
    private final AuthenticatedPrincipalResolver principalResolver;

    @PostMapping("/api/v1/projects/{projectSlug}/story-agent")
    public ResponseEntity<DevLogStoryAgentExecution> agentRequest(
            @PathVariable String projectSlug,
            @Valid @RequestBody AgentRequest payload,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        DevLogStoryAgentRequest request = new DevLogStoryAgentRequest(
                projectSlug,
                payload.storyId(),
                payload.intent(),
                effectiveQuestion(payload.intent(), payload.question()),
                payload.files() == null ? List.of() : payload.files(),
                payload.guidance(),
                idempotencyKey
        );

        DevLogStoryAgentExecution execution = devLogStoryAgentService.execute(request, principal);
        if (execution.status() == AiTaskStatus.SUBMITTED || execution.status() == AiTaskStatus.PROCESSING) {
            return ResponseEntity.accepted().body(execution);
        }
        if (execution.status() == AiTaskStatus.COMPLETED) {
            return ResponseEntity.ok(execution);
        }
        // Failed executions are valid agent diagnostics, not transport failures.
        return ResponseEntity.ok(execution);
    }

    private String effectiveQuestion(String intent, String question) {
        return question == null || question.isBlank() ? intent : question;
    }

    public record AgentRequest(
            UUID storyId,
            @NotBlank String intent,
            String question,
            List<@NotBlank String> files,
            Map<String, Object> guidance) {
    }
}
