package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Thin MCP adapter for the Core-owned, provider-free live evaluation plan. */
@Component
@RequiredArgsConstructor
public class PrepareLiveEvaluationTool {
    private final DevlogProjectContextClient client;
    private final ObjectMapper objectMapper;
    private final McpAuthenticatedPrincipalProvider principalProvider;

    @McpTool(
            name = "prepare_bounded_live_evaluation",
            description = "Prepare a bounded live evaluation through Core without provider calls or execution")
    public String prepare(
            @McpArg(description = "DevLog project slug", required = true) String projectSlug,
            @McpArg(description = "Stable idempotency key", required = true) String idempotencyKey,
            @McpArg(description = "Benchmark identifier", required = true) String benchmarkId,
            @McpArg(description = "Benchmark version", required = true) String benchmarkVersion,
            @McpArg(description = "Case identifier", required = true) String caseId,
            @McpArg(description = "Manifest identifier", required = true) String manifestId,
            @McpArg(description = "Manifest version", required = true) String manifestVersion,
            @McpArg(description = "Manifest SHA-256 digest", required = true) String manifestDigest,
            @McpArg(description = "Oracle status", required = true) String oracleStatus,
            @McpArg(description = "Oracle SHA-256 digest", required = true) String oracleDigest,
            @McpArg(description = "Frozen source revision", required = true) String sourceRevision,
            @McpArg(description = "Executable runtime revision", required = true) String runtimeRevision,
            @McpArg(description = "Bounded local runtime attestation", required = true) Map<String, Object> runtimeAttestation,
            @McpArg(description = "A/B/C runtime identities", required = true) Map<String, Object> runtimeIdentities,
            @McpArg(description = "Operational ceilings", required = true) Map<String, Object> operationalLimits,
            @McpArg(description = "Provider identity", required = true) String provider,
            @McpArg(description = "Model identity", required = true) String model,
            @McpArg(description = "Approved non-secret pricing contract", required = true) Map<String, Object> pricingContract,
            @McpArg(description = "Logical isolated artifact namespace", required = true) String artifactNamespace,
            @McpArg(description = "Bounded observation identifiers", required = true) List<String> observationIds,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        if (principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authenticated bearer token is required");
        }
        try {
            return objectMapper.writeValueAsString(client.prepareLiveEvaluation(
                    projectSlug, principal.bearerToken(), principal.delegationToken(), idempotencyKey,
                    new DevlogProjectContextClient.LiveEvaluationPreparationRequest(
                            benchmarkId, benchmarkVersion, caseId, manifestId, manifestVersion, manifestDigest,
                            oracleStatus, oracleDigest, sourceRevision, runtimeRevision, runtimeAttestation,
                            runtimeIdentities, operationalLimits, provider, model, pricingContract,
                            artifactNamespace, observationIds)));
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception,
                    "Project '%s' not found or access denied".formatted(projectSlug), true,
                    "preparing bounded live evaluation");
        }
    }

    @McpTool(
            name = "get_bounded_live_evaluation_preparation",
            description = "Read an authorized immutable bounded live evaluation plan")
    public String get(
            @McpArg(description = "Preparation plan UUID", required = true) UUID planId,
            McpSyncRequestContext requestContext) {
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated("Authentication is required"));
        if (principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated("Authenticated bearer token is required");
        }
        try {
            return objectMapper.writeValueAsString(client.getLiveEvaluationPreparation(
                    planId, principal.bearerToken(), principal.delegationToken()));
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception, "Preparation plan not found", true,
                    "reading bounded live evaluation preparation");
        }
    }
}
