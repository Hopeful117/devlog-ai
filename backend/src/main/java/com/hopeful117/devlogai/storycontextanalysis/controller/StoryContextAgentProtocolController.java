package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackFacade;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackAuthenticator;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentMetrics;
import com.hopeful117.devlogai.storycontextanalysis.service.AuthorizedStoryContextSnapshotReader;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import com.hopeful117.devlogai.storycontextanalysis.usecase.StoryAgentFollowUpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** REST adapter for the versioned SCA protocol. It delegates to existing Core services. */
@RestController
@RequestMapping("/api/v1/story-context-agent")
public class StoryContextAgentProtocolController {
    private final AnalyzeStoryContextUseCase analyzeStoryContextUseCase;
    private final StoryContextAgentCallbackFacade callbackFacade;
    private final AiTaskService aiTaskService;
    private final StoryContextAgentCallbackAuthenticator authenticator;
    private final StoryContextAgentMetrics metrics;
    private final ObjectMapper objectMapper;
    private final AuthorizedStoryContextSnapshotReader snapshotReader;
    private final AuthenticatedPrincipalResolver principalResolver;
    private final StoryAgentFollowUpService followUpService;

    @Autowired
    public StoryContextAgentProtocolController(
            AnalyzeStoryContextUseCase analyzeStoryContextUseCase,
            StoryContextAgentCallbackFacade callbackFacade,
            AiTaskService aiTaskService,
            StoryContextAgentCallbackAuthenticator authenticator,
            StoryContextAgentMetrics metrics,
            ObjectMapper objectMapper,
            AuthorizedStoryContextSnapshotReader snapshotReader,
            AuthenticatedPrincipalResolver principalResolver,
            StoryAgentFollowUpService followUpService) {
        this.analyzeStoryContextUseCase = analyzeStoryContextUseCase;
        this.callbackFacade = callbackFacade;
        this.aiTaskService = aiTaskService;
        this.authenticator = authenticator;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.snapshotReader = snapshotReader;
        this.principalResolver = principalResolver;
        this.followUpService = followUpService;
    }

    /** Compatibility constructor for callback-only unit tests. */
    public StoryContextAgentProtocolController(
            AnalyzeStoryContextUseCase analyzeStoryContextUseCase,
            StoryContextAgentCallbackFacade callbackFacade,
            AiTaskService aiTaskService,
            StoryContextAgentCallbackAuthenticator authenticator,
            StoryContextAgentMetrics metrics,
            ObjectMapper objectMapper,
            AuthorizedStoryContextSnapshotReader snapshotReader,
            AuthenticatedPrincipalResolver principalResolver) {
        this(analyzeStoryContextUseCase, callbackFacade, aiTaskService, authenticator,
                metrics, objectMapper, snapshotReader, principalResolver, null);
    }

    /** Compatibility constructor for callback-only unit tests. */
    public StoryContextAgentProtocolController(
            AnalyzeStoryContextUseCase analyzeStoryContextUseCase,
            StoryContextAgentCallbackFacade callbackFacade,
            AiTaskService aiTaskService,
            StoryContextAgentCallbackAuthenticator authenticator,
            StoryContextAgentMetrics metrics,
            ObjectMapper objectMapper) {
        this(analyzeStoryContextUseCase, callbackFacade, aiTaskService, authenticator,
                metrics, objectMapper, null, null, null);
    }

    @GetMapping("/projects/{projectSlug}/context")
    public ResponseEntity<Map<String, Object>> projection(
            @PathVariable String projectSlug,
            @RequestParam(required = false) UUID storyId,
            @RequestParam(required = false) List<String> files,
            @RequestParam @NotBlank String intent) {
        return metrics.time("projection", () -> {
            metrics.request("projection");
            return ResponseEntity.ok(analyzeStoryContextUseCase.project(projectSlug, storyId, intent, files));
        });
    }

    @PostMapping("/projects/{projectSlug}/tasks")
    public ResponseEntity<SubmitResponse> submit(
            @PathVariable String projectSlug,
            @Valid @RequestBody SubmitRequest request,
            @RequestParam(required = false) UUID storyId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return metrics.time("submit", () -> {
            metrics.request("submit");
            List<String> files = request.files() == null ? List.of() : request.files();
            UUID aiTaskId = analyzeStoryContextUseCase.execute(
                    projectSlug, storyId, request.intent(), files, request.guidance(), idempotencyKey);
            AiTaskResponse task = aiTaskService.getById(aiTaskId);
            return ResponseEntity.accepted().body(SubmitResponse.from(task, task.contextSnapshot()));
        });
    }

    @PostMapping("/tasks/{aiTaskId}/callback")
    public ResponseEntity<AiTaskResultAcknowledgement> callback(
            @PathVariable UUID aiTaskId,
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-SCA-Signature", required = false) String signature,
            HttpServletRequest servletRequest) {
        return metrics.time("callback", () -> {
            metrics.request("callback");
            if (!authenticator.isValid(servletRequest.getMethod(), servletRequest.getRequestURI(), rawBody, signature)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            final AiTaskResultRequest request;
            try {
                request = objectMapper.readValue(
                        new String(rawBody, StandardCharsets.UTF_8), AiTaskResultRequest.class);
            } catch (Exception exception) {
                return ResponseEntity.badRequest().build();
            }
            request.validateCallbackContract();
            // Keep domain conflicts visible to GlobalExceptionHandler so a different
            // terminal payload is reported as 409 rather than being flattened to 400.
            return ResponseEntity.ok(callbackFacade.callback(aiTaskId, request));
        });
    }

    @GetMapping("/tasks/{aiTaskId}/snapshot")
    public ResponseEntity<AiTaskResponse> snapshot(
            @PathVariable UUID aiTaskId, HttpServletRequest servletRequest) {
        return metrics.time("snapshot", () -> {
            metrics.request("snapshot");
            if (snapshotReader == null || principalResolver == null) {
                throw new UnauthenticatedPrincipalException();
            }
            var principal = principalResolver.resolve(servletRequest)
                    .orElseThrow(UnauthenticatedPrincipalException::new);
            return ResponseEntity.ok(snapshotReader.readStoryContextSnapshot(principal, aiTaskId));
        });
    }

    @PostMapping("/snapshots/{snapshotId}/follow-up")
    public ResponseEntity<StoryAgentFollowUpService.FollowUpSubmission> followUp(
            @PathVariable UUID snapshotId,
            @Valid @RequestBody FollowUpRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest) {
        var principal = principalResolver.resolve(servletRequest)
                .orElseThrow(UnauthenticatedPrincipalException::new);
        return ResponseEntity.accepted().body(followUpService.submit(
                principal, snapshotId, request.question(), request.guidance(), idempotencyKey));
    }

    public record SubmitRequest(@NotBlank String intent, List<String> files, Map<String, Object> guidance) { }

    public record FollowUpRequest(@NotBlank String question, Map<String, Object> guidance) { }

    public record SubmitResponse(String protocolVersion, String projectionVersion, UUID aiTaskId, UUID snapshotId,
                                 String contextDigest, String projectionDigest, String selectionDigest,
                                 Map<String, Object> scope, Map<String, Object> freshness,
                                 Map<String, Object> accounting, String status) {
        static SubmitResponse from(AiTaskResponse task, Map<String, Object> snapshot) {
            return new SubmitResponse(
                    (String) snapshot.get("protocolVersion"),
                    (String) snapshot.get("projectionVersion"),
                    task.id(), task.id(), task.contextDigest(), task.projectionDigest(), task.selectionDigest(),
                    map(snapshot.get("scope")), map(snapshot.get("freshness")), map(snapshot.get("accounting")),
                    task.status().name());
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> map(Object value) {
            return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }
    }
}
