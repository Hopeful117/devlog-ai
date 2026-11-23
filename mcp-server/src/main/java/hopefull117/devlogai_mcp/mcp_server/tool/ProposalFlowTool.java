package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.CoreCallPolicy;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP tools for the proposal review and acceptance flow.
 *
 * <p>Typical agent sequence:
 * <ol>
 *   <li>{@code list_pending_proposals} — discover what is waiting for review</li>
 *   <li>{@code get_proposal_review} — inspect proposals with full evidence</li>
 *   <li>{@code validate_proposal} — accept or reject each proposal</li>
 * </ol>
 */
@Component
public class ProposalFlowTool {

    private final DevlogResourceClient resourceClient;
    private final DevlogAdminClient adminClient;
    private final ResourceSupport support;
    private final ObjectMapper objectMapper;
    @Nullable
    private final McpAuthenticatedPrincipalProvider principalProvider;

    public ProposalFlowTool(
            DevlogResourceClient resourceClient,
            DevlogAdminClient adminClient,
            ResourceSupport support,
            ObjectMapper objectMapper) {
        this(resourceClient, adminClient, support, objectMapper, null);
    }

    @Autowired
    public ProposalFlowTool(
            DevlogResourceClient resourceClient,
            DevlogAdminClient adminClient,
            ResourceSupport support,
            ObjectMapper objectMapper,
            @Nullable McpAuthenticatedPrincipalProvider principalProvider) {
        this.resourceClient = resourceClient;
        this.adminClient = adminClient;
        this.support = support;
        this.objectMapper = objectMapper;
        this.principalProvider = principalProvider;
    }

    @McpTool(
            name = "list_pending_proposals",
            description = "Lists all proposals with status PROPOSED (pending human review) for a DevLog project. "
                    + "Each item carries its id, type, analysisId, sourceIndex, confidence, createdAt, and a summary "
                    + "of its payload. Use get_proposal_review to inspect proposals with full evidence, "
                    + "then validate_proposal to accept or reject."
    )
    public String listPendingProposals(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            McpSyncRequestContext requestContext
    ) {
        var projectId = support.requireProjectId(projectSlug, requestContext);
        String notFound = "Project '%s' not found".formatted(projectSlug);

        String allProposals;
        if (principalProvider != null) {
            var principal = principalProvider.currentPrincipal(requestContext).orElse(null);
            if (principal != null && principal.bearerToken() != null && !principal.bearerToken().isBlank()) {
                String delegation = principal.delegationToken() == null ? "" : principal.delegationToken();
                allProposals = support.getScoped(
                        () -> resourceClient.listProposalsAuthenticated(
                                projectId, principal.bearerToken(), delegation),
                        notFound);
            } else {
                allProposals = support.getScoped(
                        () -> resourceClient.listProposals(projectId), notFound);
            }
        } else {
            allProposals = support.getScoped(
                    () -> resourceClient.listProposals(projectId), notFound);
        }

        return filterProposed(allProposals);
    }

    @McpTool(
            name = "get_proposal_review",
            description = "Returns a paginated proposal review page for a given analysis, ordered with PROPOSED items first. "
                    + "Each item includes the full payload, evidence references, supporting facts and observations, "
                    + "and the existing validation decision if one has already been made. "
                    + "Use list_pending_proposals to discover analysisIds."
    )
    public String getProposalReview(
            @McpToolParam(description = "Slug identifying the DevLog project", required = true)
            String projectSlug,
            @McpToolParam(description = "UUID of the analysis to review (from list_pending_proposals)", required = true)
            String analysisId,
            @McpToolParam(description = "Page number, 0-based (default: 0)", required = false)
            @Nullable Integer page,
            @McpToolParam(description = "Page size, max 50 (default: 10)", required = false)
            @Nullable Integer size,
            McpSyncRequestContext requestContext
    ) {
        support.requireProjectId(projectSlug, requestContext);
        UUID analysisUuid = support.requireUuid(analysisId, "analysisId");
        int effectivePage = page != null ? page : 0;
        int effectiveSize = size != null ? Math.min(size, 50) : 10;
        String notFound = "Analysis '%s' not found".formatted(analysisId);

        if (principalProvider != null) {
            var principal = principalProvider.currentPrincipal(requestContext).orElse(null);
            if (principal != null && principal.bearerToken() != null && !principal.bearerToken().isBlank()) {
                String delegation = principal.delegationToken() == null ? "" : principal.delegationToken();
                return support.getScoped(
                        () -> resourceClient.getProposalReviewAuthenticated(
                                analysisUuid, effectivePage, effectiveSize,
                                principal.bearerToken(), delegation),
                        notFound);
            }
        }
        return support.getScoped(
                () -> resourceClient.getProposalReview(analysisUuid, effectivePage, effectiveSize),
                notFound);
    }

    @McpTool(
            name = "validate_proposal",
            description = "Accepts or rejects a DevLog proposal. When ACCEPTED, the proposal is promoted to trusted "
                    + "knowledge (Insight, EngineeringEvent, or other depending on type). "
                    + "Requires an authenticated MCP principal whose ID is a valid UUID. "
                    + "For INSIGHT proposals, optionally set insightSeverity (LOW, MEDIUM, HIGH, CRITICAL)."
    )
    public String validateProposal(
            @McpToolParam(description = "UUID of the proposal to validate (from list_pending_proposals)", required = true)
            String proposalId,
            @McpToolParam(description = "Validation decision: ACCEPTED or REJECTED", required = true)
            String decision,
            @McpToolParam(description = "Optional comment explaining the decision (max 2000 characters)", required = false)
            @Nullable String comment,
            @McpToolParam(description = "Insight severity for INSIGHT proposals: LOW, MEDIUM, HIGH, or CRITICAL", required = false)
            @Nullable String insightSeverity,
            McpSyncRequestContext requestContext
    ) {
        if (principalProvider == null) {
            throw ResourceSupport.unauthenticated(
                    "validate_proposal requires an authenticated MCP principal");
        }
        var principal = principalProvider.currentPrincipal(requestContext)
                .orElseThrow(() -> ResourceSupport.unauthenticated(
                        "Authentication is required to validate proposals"));
        if (principal.bearerToken() == null || principal.bearerToken().isBlank()) {
            throw ResourceSupport.unauthenticated(
                    "A bearer token is required to validate proposals");
        }

        UUID proposalUuid = support.requireUuid(proposalId, "proposalId");

        UUID validatedBy;
        try {
            validatedBy = UUID.fromString(principal.principalId());
        } catch (IllegalArgumentException e) {
            throw ResourceSupport.invalidParams(
                    "Cannot validate — the MCP principal ID '%s' is not a valid UUID. "
                            + "Use the DevLog web interface to validate this proposal."
                                    .formatted(principal.principalId()));
        }

        if (!"ACCEPTED".equals(decision) && !"REJECTED".equals(decision)) {
            throw ResourceSupport.invalidParams(
                    "decision must be ACCEPTED or REJECTED, got: " + decision);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("proposalId", proposalUuid.toString());
        body.put("decision", decision);
        body.put("validatedBy", validatedBy.toString());
        if (comment != null && !comment.isBlank()) {
            body.put("comment", comment);
        }
        if (insightSeverity != null && !insightSeverity.isBlank()) {
            body.put("insightSeverity", insightSeverity);
        }

        try {
            return adminClient.validateProposalAuthenticated(principal.bearerToken(), body);
        } catch (McpError error) {
            throw error;
        } catch (Exception exception) {
            throw CoreCallPolicy.map(exception,
                    "Proposal '%s' not found".formatted(proposalId),
                    false, "validating the proposal");
        }
    }

    private String filterProposed(String allProposalsJson) {
        JsonNode array = objectMapper.readTree(allProposalsJson);
        if (!array.isArray()) {
            return allProposalsJson;
        }
        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode node : array) {
            if ("PROPOSED".equals(node.path("status").asText(null))) {
                filtered.add(node);
            }
        }
        return objectMapper.writeValueAsString(filtered);
    }
}
