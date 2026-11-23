package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProposalFlowToolTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final UUID ANALYSIS_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PROPOSAL_ID =
            UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID PRINCIPAL_UUID =
            UUID.fromString("c0000000-0000-0000-0000-000000000003");
    private static final String BEARER = "Bearer test-token";

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final DevlogAdminClient adminClient = mock(DevlogAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ProposalFlowTool tool = new ProposalFlowTool(
            resourceClient, adminClient,
            new ResourceSupport(projectContextClient, resourceClient, objectMapper),
            objectMapper);

    @BeforeEach
    void resolveProject() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE", List.of()));
        when(projectContextClient.getProjectContext("unknown")).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));
    }

    // ── list_pending_proposals ──────────────────────────────────────────────

    @Test
    void listPendingProposals_filtersByProposedStatus() {
        String mixed = """
                [
                  {"id":"%s","status":"PROPOSED","type":"INSIGHT","analysisId":"%s"},
                  {"id":"aaaaaaaa-0000-0000-0000-000000000000","status":"ACCEPTED","type":"INSIGHT","analysisId":"%s"},
                  {"id":"bbbbbbbb-0000-0000-0000-000000000000","status":"REJECTED","type":"INSIGHT","analysisId":"%s"}
                ]""".formatted(PROPOSAL_ID, ANALYSIS_ID, ANALYSIS_ID, ANALYSIS_ID);
        when(resourceClient.listProposals(PROJECT_ID)).thenReturn(mixed);

        String result = tool.listPendingProposals(SLUG, null);

        assertThat(result).contains(PROPOSAL_ID.toString());
        assertThat(result).doesNotContain("ACCEPTED");
        assertThat(result).doesNotContain("REJECTED");
    }

    @Test
    void listPendingProposals_returnsEmptyArrayWhenNonePending() {
        String noProposed = """
                [{"id":"aaaaaaaa-0000-0000-0000-000000000000","status":"ACCEPTED","type":"INSIGHT"}]""";
        when(resourceClient.listProposals(PROJECT_ID)).thenReturn(noProposed);

        String result = tool.listPendingProposals(SLUG, null);

        assertThat(result).isEqualTo("[]");
    }

    @Test
    void listPendingProposals_rejectsUnknownProject() {
        assertThatThrownBy(() -> tool.listPendingProposals("unknown", null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Project 'unknown' not found");
    }

    @Test
    void listPendingProposals_usesAuthenticatedEndpointWhenPrincipalPresent() {
        var principal = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                PRINCIPAL_UUID.toString(),
                McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", BEARER, "");
        McpAuthenticatedPrincipalProvider principalProvider = buildRequestScopedProvider(principal);
        ResourceSupport authSupport = new ResourceSupport(
                projectContextClient, resourceClient, objectMapper, principalProvider);
        ProposalFlowTool authTool = new ProposalFlowTool(
                resourceClient, adminClient, authSupport, objectMapper, principalProvider);
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);

        when(projectContextClient.getProjectContextAuthenticated(SLUG, BEARER, ""))
                .thenReturn(new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE", List.of()));
        when(resourceClient.listProposalsAuthenticated(PROJECT_ID, BEARER, ""))
                .thenReturn("[{\"id\":\"" + PROPOSAL_ID + "\",\"status\":\"PROPOSED\",\"type\":\"INSIGHT\"}]");

        String result = authTool.listPendingProposals(SLUG, ctx);

        assertThat(result).contains(PROPOSAL_ID.toString());
        verify(resourceClient).listProposalsAuthenticated(PROJECT_ID, BEARER, "");
    }

    // ── get_proposal_review ─────────────────────────────────────────────────

    @Test
    void getProposalReview_delegatesWithDefaultPagination() {
        String reviewJson = "{\"counts\":{\"total\":1,\"pending\":1},\"items\":[]}";
        when(resourceClient.getProposalReview(ANALYSIS_ID, 0, 10)).thenReturn(reviewJson);

        String result = tool.getProposalReview(SLUG, ANALYSIS_ID.toString(), null, null, null);

        assertThat(result).contains("\"pending\":1");
        verify(resourceClient).getProposalReview(ANALYSIS_ID, 0, 10);
    }

    @Test
    void getProposalReview_clampsSizeTo50() {
        when(resourceClient.getProposalReview(ANALYSIS_ID, 0, 50)).thenReturn("{\"items\":[]}");

        tool.getProposalReview(SLUG, ANALYSIS_ID.toString(), 0, 200, null);

        verify(resourceClient).getProposalReview(ANALYSIS_ID, 0, 50);
    }

    @Test
    void getProposalReview_rejectsInvalidAnalysisId() {
        assertThatThrownBy(() ->
                tool.getProposalReview(SLUG, "not-a-uuid", null, null, null))
                .isInstanceOf(McpError.class);
    }

    @Test
    void getProposalReview_usesAuthenticatedEndpointWhenPrincipalPresent() {
        var principal = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                PRINCIPAL_UUID.toString(),
                McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", BEARER, "");
        McpAuthenticatedPrincipalProvider principalProvider = buildRequestScopedProvider(principal);
        ResourceSupport authSupport = new ResourceSupport(
                projectContextClient, resourceClient, objectMapper, principalProvider);
        ProposalFlowTool authTool = new ProposalFlowTool(
                resourceClient, adminClient, authSupport, objectMapper, principalProvider);
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);

        when(projectContextClient.getProjectContextAuthenticated(SLUG, BEARER, ""))
                .thenReturn(new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE", List.of()));
        when(resourceClient.getProposalReviewAuthenticated(ANALYSIS_ID, 0, 10, BEARER, ""))
                .thenReturn("{\"items\":[]}");

        authTool.getProposalReview(SLUG, ANALYSIS_ID.toString(), null, null, ctx);

        verify(resourceClient).getProposalReviewAuthenticated(ANALYSIS_ID, 0, 10, BEARER, "");
    }

    // ── validate_proposal ───────────────────────────────────────────────────

    @Test
    void validateProposal_requiresAuthenticatedPrincipal() {
        assertThatThrownBy(() ->
                tool.validateProposal(PROPOSAL_ID.toString(), "ACCEPTED", null, null, null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("authenticated");
    }

    @Test
    void validateProposal_requiresPresentPrincipal() {
        McpAuthenticatedPrincipalProvider principalProvider = buildRequestScopedProvider(null);
        ProposalFlowTool authTool = new ProposalFlowTool(
                resourceClient, adminClient,
                new ResourceSupport(projectContextClient, resourceClient, objectMapper),
                objectMapper, principalProvider);
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);

        assertThatThrownBy(() ->
                authTool.validateProposal(PROPOSAL_ID.toString(), "ACCEPTED", null, null, ctx))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    void validateProposal_nonUuidPrincipalId_surfacesError() {
        var principal = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "not-a-uuid",
                McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", BEARER, null);
        ProposalFlowTool authTool = new ProposalFlowTool(
                resourceClient, adminClient,
                new ResourceSupport(projectContextClient, resourceClient, objectMapper),
                objectMapper, buildRequestScopedProvider(principal));
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);

        assertThatThrownBy(() ->
                authTool.validateProposal(PROPOSAL_ID.toString(), "ACCEPTED", null, null, ctx))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not a valid UUID");
    }

    @Test
    void validateProposal_rejectsInvalidDecision() {
        var principal = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                PRINCIPAL_UUID.toString(),
                McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", BEARER, null);
        ProposalFlowTool authTool = new ProposalFlowTool(
                resourceClient, adminClient,
                new ResourceSupport(projectContextClient, resourceClient, objectMapper),
                objectMapper, buildRequestScopedProvider(principal));
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);

        assertThatThrownBy(() ->
                authTool.validateProposal(PROPOSAL_ID.toString(), "MAYBE", null, null, ctx))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("ACCEPTED or REJECTED");
    }

    @Test
    void validateProposal_accepted_buildsCorrectRequest() {
        var principal = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                PRINCIPAL_UUID.toString(),
                McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", BEARER, null);
        ProposalFlowTool authTool = new ProposalFlowTool(
                resourceClient, adminClient,
                new ResourceSupport(projectContextClient, resourceClient, objectMapper),
                objectMapper, buildRequestScopedProvider(principal));
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);

        String validationResponse = "{\"id\":\"v1\",\"decision\":\"ACCEPTED\"}";
        when(adminClient.validateProposalAuthenticated(eq(BEARER), any())).thenReturn(validationResponse);

        String result = authTool.validateProposal(
                PROPOSAL_ID.toString(), "ACCEPTED", "looks good", "HIGH", ctx);

        assertThat(result).contains("ACCEPTED");
        verify(adminClient).validateProposalAuthenticated(eq(BEARER), any(Map.class));
    }

    @Test
    void validateProposal_rejected_noInsightSeverityRequired() {
        var principal = new McpAuthenticatedPrincipalProvider.McpPrincipal(
                PRINCIPAL_UUID.toString(),
                McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", BEARER, null);
        ProposalFlowTool authTool = new ProposalFlowTool(
                resourceClient, adminClient,
                new ResourceSupport(projectContextClient, resourceClient, objectMapper),
                objectMapper, buildRequestScopedProvider(principal));
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);

        when(adminClient.validateProposalAuthenticated(eq(BEARER), any())).thenReturn("{\"decision\":\"REJECTED\"}");

        String result = authTool.validateProposal(
                PROPOSAL_ID.toString(), "REJECTED", null, null, ctx);

        assertThat(result).contains("REJECTED");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static McpAuthenticatedPrincipalProvider buildRequestScopedProvider(
            McpAuthenticatedPrincipalProvider.McpPrincipal principal) {
        return new McpAuthenticatedPrincipalProvider() {
            @Override
            public Optional<McpPrincipal> currentPrincipal() {
                return Optional.empty();
            }

            @Override
            public Optional<McpPrincipal> currentPrincipal(
                    org.springframework.ai.mcp.annotation.context.McpSyncRequestContext requestContext) {
                return Optional.ofNullable(principal);
            }
        };
    }
}
