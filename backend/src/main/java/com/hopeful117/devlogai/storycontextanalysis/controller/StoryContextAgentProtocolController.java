package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultRequest;
import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackFacade;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackAuthenticator;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentMetrics;
import com.hopeful117.devlogai.storycontextanalysis.service.AuthorizedStoryContextSnapshotReader;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAnalysisQueryService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import com.hopeful117.devlogai.storycontextanalysis.usecase.StoryAgentFollowUpService;
import com.hopeful117.devlogai.storycontextanalysis.usecase.StoryContextInvestigationActionService;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationActionRequest;
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
import java.util.Set;
import java.util.LinkedHashMap;

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
    private final StoryContextAnalysisQueryService analysisQueryService;
    private final StoryContextInvestigationActionService investigationActionService;

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
            StoryAgentFollowUpService followUpService,
            StoryContextAnalysisQueryService analysisQueryService,
            StoryContextInvestigationActionService investigationActionService) {
        this.analyzeStoryContextUseCase = analyzeStoryContextUseCase;
        this.callbackFacade = callbackFacade;
        this.aiTaskService = aiTaskService;
        this.authenticator = authenticator;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.snapshotReader = snapshotReader;
        this.principalResolver = principalResolver;
        this.followUpService = followUpService;
        this.analysisQueryService = analysisQueryService;
        this.investigationActionService = investigationActionService;
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
                metrics, objectMapper, snapshotReader, principalResolver, null, null, null);
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
                metrics, objectMapper, null, null, null, null, null);
    }

    @GetMapping("/projects/{projectSlug}/context")
    public ResponseEntity<Map<String, Object>> projection(
            @PathVariable String projectSlug,
            @RequestParam(required = false) UUID storyId,
            @RequestParam(required = false) List<String> files,
            @RequestParam @NotBlank String intent,
            HttpServletRequest servletRequest) {
        return metrics.time("projection", () -> {
            metrics.request("projection");
            if (principalResolver == null) {
                return ResponseEntity.ok(analyzeStoryContextUseCase.project(projectSlug, storyId, intent, files));
            }
            var principal = principalResolver.resolve(servletRequest)
                    .orElseThrow(UnauthenticatedPrincipalException::new);
            return ResponseEntity.ok(analyzeStoryContextUseCase.project(
                    projectSlug, storyId, intent, files, principal));
        });
    }

    @PostMapping("/projects/{projectSlug}/tasks")
    public ResponseEntity<SubmitResponse> submit(
            @PathVariable String projectSlug,
            @Valid @RequestBody SubmitRequest request,
            @RequestParam(required = false) UUID storyId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest servletRequest) {
        return metrics.time("submit", () -> {
            metrics.request("submit");
            var principal = principalResolver.resolve(servletRequest)
                    .orElseThrow(UnauthenticatedPrincipalException::new);
            List<String> files = request.files() == null ? List.of() : request.files();
            UUID aiTaskId = analyzeStoryContextUseCase.execute(
                    projectSlug, storyId, request.intent(), files, request.guidance(), idempotencyKey,
                    principal, "chatgpt");
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

    @PostMapping("/tasks/{aiTaskId}/investigation/actions")
    public ResponseEntity<?> investigationAction(
            @PathVariable UUID aiTaskId,
            @RequestBody byte[] rawBody,
            HttpServletRequest servletRequest) {
        return metrics.time("investigation_action", () -> {
            metrics.request("investigation_action");
            if (investigationActionService == null) {
                throw new UnauthenticatedPrincipalException();
            }
            boolean serviceAuthenticated = authenticator.isValid(servletRequest.getMethod(),
                    servletRequest.getRequestURI(), rawBody,
                    servletRequest.getHeader("X-SCA-Signature"));
            final InvestigationActionRequest request;
            try {
                request = parseInvestigationAction(rawBody);
            } catch (Exception exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "code", "INVALID_INVESTIGATION_ACTION",
                        "message", boundedMessage(exception.getMessage())));
            }
            if (serviceAuthenticated) {
                return ResponseEntity.ok(investigationActionService.executeAuthenticatedService(aiTaskId, request));
            }
            if (principalResolver == null) throw new UnauthenticatedPrincipalException();
            var principal = principalResolver.resolve(servletRequest)
                    .orElseThrow(UnauthenticatedPrincipalException::new);
            return ResponseEntity.ok(investigationActionService.execute(principal, aiTaskId, request));
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

    @GetMapping("/tasks/{aiTaskId}/result")
    public ResponseEntity<com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse> result(
            @PathVariable UUID aiTaskId, HttpServletRequest servletRequest) {
        return metrics.time("result", () -> {
            metrics.request("result");
            if (analysisQueryService == null || principalResolver == null) {
                throw new UnauthenticatedPrincipalException();
            }
            var principal = principalResolver.resolve(servletRequest)
                    .orElseThrow(UnauthenticatedPrincipalException::new);
            if (principal.isDelegated()
                    && !principal.hasDelegatedScope("MCP_STORY_AGENT_READ_RESULT")) {
                throw new com.hopeful117.devlogai.shared.exception.EntityNotFoundException(
                        "AI task", aiTaskId);
            }
            snapshotReader.authorize(principal, aiTaskId);
            return analysisQueryService.findResponseByAiTaskId(aiTaskId)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
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

    @SuppressWarnings("unchecked")
    private InvestigationActionRequest parseInvestigationAction(byte[] rawBody) throws Exception {
        Map<String, Object> body = objectMapper.readValue(new String(rawBody, StandardCharsets.UTF_8), Map.class);
         allowed(body, Set.of("protocolVersion", "executionId", "parentTaskId", "correlationId", "provider", "credentialBindingId",
                  "providerOutputTokens", "providerGenerationCount", "providerElapsedMillis", "kind", "actionId", "projectSlug", "revision", "purpose", "limits", "query", "path", "range"));
         if (!body.keySet().containsAll(Set.of("protocolVersion", "executionId", "parentTaskId", "correlationId", "provider",
                 "credentialBindingId", "providerOutputTokens", "kind", "actionId", "projectSlug", "revision", "purpose", "limits"))) {
            throw new IllegalArgumentException("required action fields missing");
        }
         String protocolVersion = requiredString(body, "protocolVersion");
         if (!"story-context-agent-protocol/v2".equals(protocolVersion)) throw new IllegalArgumentException("unsupported protocolVersion");
         UUID executionId = UUID.fromString(requiredString(body, "executionId"));
         UUID parentTaskId = UUID.fromString(requiredString(body, "parentTaskId"));
         UUID correlationId = UUID.fromString(requiredString(body, "correlationId"));
         String provider = requiredString(body, "provider");
         UUID credentialBindingId = UUID.fromString(requiredString(body, "credentialBindingId"));
          Integer providerOutputTokens = body.containsKey("providerOutputTokens") ? integer(body, "providerOutputTokens") : null;
          Integer providerGenerationCount = body.containsKey("providerGenerationCount") ? integer(body, "providerGenerationCount") : null;
          Long providerElapsedMillis = body.containsKey("providerElapsedMillis") ? longValue(body, "providerElapsedMillis") : null;
         String kind = requiredString(body, "kind");
         if (!"SEARCH_EVIDENCE".equals(kind) && !"READ_EVIDENCE_RANGE".equals(kind)) {
             throw new IllegalArgumentException("unknown investigation action kind");
         }
        String actionId = requiredString(body, "actionId");
        String projectSlug = requiredString(body, "projectSlug");
        String revision = requiredString(body, "revision");
        String purpose = requiredString(body, "purpose");
        Map<String, Object> limits = object(body.get("limits"));
        exact(limits, Set.of("resultLimit", "maximumBytes", "maximumTokens"));
        InvestigationActionRequest.Limits parsedLimits = new InvestigationActionRequest.Limits(
                integer(limits, "resultLimit"), longValue(limits, "maximumBytes"), longValue(limits, "maximumTokens"));
        String query = optionalString(body.get("query"));
        String path = optionalString(body.get("path"));
        InvestigationActionRequest.Range range = null;
        if (body.get("range") != null) {
            Map<String, Object> value = object(body.get("range"));
            exact(value, Set.of("startLine", "endLine"));
            range = new InvestigationActionRequest.Range(integer(value, "startLine"), integer(value, "endLine"));
        }
        if ("SEARCH_EVIDENCE".equals(kind) && query == null) throw new IllegalArgumentException("query is required");
        if ("READ_EVIDENCE_RANGE".equals(kind) && (path == null || range == null)) throw new IllegalArgumentException("path and range are required");
         return new InvestigationActionRequest(kind, actionId, projectSlug, revision, purpose, parsedLimits, query, path, range,
                  protocolVersion, executionId, parentTaskId, correlationId, provider, credentialBindingId,
                  providerOutputTokens, providerGenerationCount, providerElapsedMillis);
    }

    private void exact(Map<String, Object> value, Set<String> allowed) {
        if (value == null || !value.keySet().equals(allowed)) throw new IllegalArgumentException("strict action fields required");
    }

    private void allowed(Map<String, Object> value, Set<String> allowed) {
        if (value == null || !allowed.containsAll(value.keySet())) throw new IllegalArgumentException("unknown action field");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("object required");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> { if (!(key instanceof String)) throw new IllegalArgumentException("string key required"); result.put((String) key, item); });
        return result;
    }

    private String requiredString(Map<String, Object> value, String key) {
        String result = optionalString(value.get(key));
        if (result == null) throw new IllegalArgumentException(key + " is required");
        return result;
    }

    private String optionalString(Object value) {
        return value == null ? null : value instanceof String text && !text.isBlank() ? text : fail("string required");
    }

    private int integer(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof Number number) || number.longValue() != number.doubleValue()) throw new IllegalArgumentException(key + " must be an integer");
        return Math.toIntExact(number.longValue());
    }

    private long longValue(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof Number number) || number.longValue() != number.doubleValue()) throw new IllegalArgumentException(key + " must be an integer");
        return number.longValue();
    }

    private <T> T fail(String message) { throw new IllegalArgumentException(message); }

    private String boundedMessage(String message) {
        if (message == null || message.isBlank()) return "Malformed investigation action";
        return message.length() <= 200 ? message : message.substring(0, 200);
    }

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
