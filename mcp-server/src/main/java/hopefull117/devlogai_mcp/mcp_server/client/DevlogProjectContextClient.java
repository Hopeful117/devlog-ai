package hopefull117.devlogai_mcp.mcp_server.client;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import com.hopeful117.devlogai.contracts.engineeringcontext.StoryContextAnalysisResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@HttpExchange("/api/v1")
public interface DevlogProjectContextClient {
    @GetExchange("/projects/{projectSlug}/context")
    ProjectContext getProjectContext(@PathVariable String projectSlug);

    @GetExchange("/projects/{projectSlug}/context")
    ProjectContext getProjectContextAuthenticated(
            @PathVariable String projectSlug,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation
    );

    @GetExchange("/projects/{projectSlug}/engineering-context")
    EngineeringContext getEngineeringContext(
            @PathVariable String projectSlug,
            @RequestParam("intent") String intent,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "storyId", required = false) UUID storyId
    );

    @GetExchange("/projects/{projectSlug}/engineering-context")
    EngineeringContext getEngineeringContextAuthenticated(
            @PathVariable String projectSlug,
            @RequestParam("intent") String intent,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam(value = "storyId", required = false) UUID storyId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation
    );

    @PostExchange("/projects/{projectSlug}/stories/{storyId}/analyze-context")
    AnalyzeContextResponse analyzeStoryContext(
            @PathVariable String projectSlug,
            @PathVariable UUID storyId,
            @RequestBody(required = false) AnalyzeContextRequest request
    );

    @GetExchange("/ai/tasks/{aiTaskId}/story-context-analysis")
    StoryContextAnalysisResponse getStoryContextAnalysis(
            @PathVariable UUID aiTaskId
    );

    @GetExchange("/ai/tasks/{aiTaskId}/story-context-analysis")
    StoryContextAnalysisResponse getStoryContextAnalysisAuthenticated(
            @PathVariable UUID aiTaskId,
            @RequestHeader("Authorization") String authorization
    );

    @GetExchange("/ai-tasks/{aiTaskId}")
    AiTaskStatusResponse getAiTaskStatus(
            @PathVariable UUID aiTaskId
    );

    @GetExchange("/ai-tasks/{aiTaskId}")
    AiTaskStatusResponse getAiTaskStatusAuthenticated(
            @PathVariable UUID aiTaskId,
            @RequestHeader("Authorization") String authorization
    );

    @GetExchange("/story-context-agent/projects/{projectSlug}/context")
    Map<String, Object> getStoryContextAgentProjection(
            @PathVariable String projectSlug,
            @RequestParam(value = "storyId", required = false) UUID storyId,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam("intent") String intent
    );

    @GetExchange("/story-context-agent/projects/{projectSlug}/context")
    Map<String, Object> getStoryContextAgentProjectionAuthenticated(
            @PathVariable String projectSlug,
            @RequestParam(value = "storyId", required = false) UUID storyId,
            @RequestParam(value = "files", required = false) List<String> files,
            @RequestParam("intent") String intent,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation
    );

    @PostExchange("/story-context-agent/projects/{projectSlug}/tasks")
    Map<String, Object> submitStoryContextAgentTask(
            @PathVariable String projectSlug,
            @RequestParam(value = "storyId", required = false) UUID storyId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("X-DevLog-Delegation") String delegation,
            @RequestBody SubmitRequest request
    );

    @PostExchange("/story-context-agent/projects/{projectSlug}/task-bindings")
    Map<String, Object> reserveStoryContextAgentTaskBinding(
            @PathVariable String projectSlug,
            @RequestParam(value = "storyId", required = false) UUID storyId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("X-DevLog-Delegation") String delegation,
            @RequestBody SubmitRequest request
    );

    @GetExchange("/story-context-agent/tasks/{aiTaskId}/snapshot")
    Map<String, Object> getStoryContextAgentSnapshot(
            @PathVariable UUID aiTaskId,
            @RequestHeader("X-DevLog-Principal-Id") String principalId,
            @RequestHeader("X-DevLog-Principal-Kind") String principalKind,
            @RequestHeader("X-DevLog-Authentication-Source") String authenticationSource,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation);

    @GetExchange("/story-context-agent/tasks/{aiTaskId}/result")
    StoryContextAnalysisResponse getStoryContextAgentResultAuthenticated(
            @PathVariable UUID aiTaskId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation);

    @PostExchange("/story-context-agent/snapshots/{snapshotId}/follow-up")
    Map<String, Object> submitStoryContextAgentFollowUp(
            @PathVariable UUID snapshotId,
            @RequestHeader("X-DevLog-Principal-Id") String principalId,
            @RequestHeader("X-DevLog-Principal-Kind") String principalKind,
            @RequestHeader("X-DevLog-Authentication-Source") String authenticationSource,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation,
            @RequestBody FollowUpRequest request);

    @PostExchange("/projects/{projectSlug}/story-agent")
    Map<String, Object> executeStoryAgent(
            @PathVariable String projectSlug,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("Authorization") String authorization,
            @RequestBody StoryAgentRequest request
    );

    @PostExchange("/live-evaluation-preparations/projects/{projectSlug}")
    Map<String, Object> prepareLiveEvaluation(
            @PathVariable String projectSlug,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody LiveEvaluationPreparationRequest request
    );

    @GetExchange("/live-evaluation-preparations/{planId}")
    Map<String, Object> getLiveEvaluationPreparation(
            @PathVariable UUID planId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation
    );

    @PostExchange("/live-evaluation-authorizations/requests")
    Map<String, Object> requestLiveEvaluationAuthorization(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody LiveEvaluationAuthorizationRequest request
    );

    @PostExchange("/live-evaluation-authorizations/{authorizationId}/approve")
    Map<String, Object> approveLiveEvaluationAuthorization(
            @PathVariable UUID authorizationId,
            @RequestParam String planDigest,
            @RequestHeader(value = "X-DevLog-MCP-Human-Confirmation", required = false)
            String mcpHumanConfirmation,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation
    );

    @GetExchange("/live-evaluation-authorizations/{authorizationId}")
    Map<String, Object> getLiveEvaluationAuthorization(
            @PathVariable UUID authorizationId,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation
    );

    @PostExchange("/live-evaluation-authorizations/{authorizationId}/consume")
    Map<String, Object> consumeLiveEvaluationAuthorization(
            @PathVariable UUID authorizationId,
            @RequestParam String planDigest,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation
    );

    record AnalyzeContextRequest(List<String> files, Map<String, Object> guidance) {}

    /** Story 0242: {@code revision} optionally pins the execution to an exact source commit. */
    record SubmitRequest(String intent, List<String> files, Map<String, Object> guidance, String revision) {
        public SubmitRequest(String intent, List<String> files, Map<String, Object> guidance) {
            this(intent, files, guidance, null);
        }
    }

    record FollowUpRequest(String question, Map<String, Object> guidance) {}

    record StoryAgentRequest(UUID storyId, String intent, String question,
                             List<String> files, Map<String, Object> guidance) {
        public StoryAgentRequest(UUID storyId, String intent, List<String> files,
                                 Map<String, Object> guidance) {
            this(storyId, intent, null, files, guidance);
        }
    }

    record LiveEvaluationPreparationRequest(
            String benchmarkId,
            String benchmarkVersion,
            String caseId,
            String manifestId,
            String manifestVersion,
            String manifestDigest,
            String oracleStatus,
            String oracleDigest,
            String sourceRevision,
            String runtimeRevision,
            Map<String, Object> runtimeAttestation,
            Map<String, Object> runtimeIdentities,
            Map<String, Object> operationalLimits,
            String provider,
            String model,
            Map<String, Object> pricingContract,
            String artifactNamespace,
            List<String> observationIds
    ) {}

    record LiveEvaluationAuthorizationRequest(UUID planId, String planDigest) {}

    record AnalyzeContextResponse(UUID aiTaskId) {}

    record AiTaskStatusResponse(
            UUID id,
            String status,
            String failureCode,
            String failureMessage,
            String contextDigest,
            String projectionDigest,
            Map<String, Object> contextSnapshot
    ) {
        public AiTaskStatusResponse(UUID id, String status, String failureCode, String failureMessage) {
            this(id, status, failureCode, failureMessage, null, null, Map.of());
        }

        public AiTaskStatusResponse {
            contextSnapshot = contextSnapshot == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(contextSnapshot));
        }
    }
}
