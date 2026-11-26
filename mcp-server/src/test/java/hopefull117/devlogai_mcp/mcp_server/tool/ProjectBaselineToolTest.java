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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectBaselineToolTest {
    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID SOURCE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ANALYSIS_ID = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private final DevlogProjectContextClient projectContextClient = mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final DevlogAdminClient adminClient = mock(DevlogAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceSupport support = new ResourceSupport(projectContextClient, resourceClient, objectMapper);

    @BeforeEach
    void resolveProject() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, "DevLog AI", "DevLog", "ACTIVE", List.of()));
        when(resourceClient.listProjectSources(PROJECT_ID)).thenReturn(
                "[{\"id\":\"" + SOURCE_ID + "\",\"active\":true}]");
    }

    private ProjectBaselineTool tool(McpAuthenticatedPrincipalProvider provider) {
        return new ProjectBaselineTool(adminClient, support, provider, objectMapper);
    }

    private static McpAuthenticatedPrincipalProvider authenticated() {
        return () -> Optional.of(new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "agent", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN, "test", "Bearer test", ""));
    }

    @Test
    void refreshStartsAnUnderstandingExecutionForTheActiveSource() {
        when(adminClient.executeProjectUnderstandingAuthenticated(
                "Bearer test", PROJECT_ID, Map.of("sourceId", SOURCE_ID.toString())))
                .thenReturn("{\"analysisId\":\"" + ANALYSIS_ID + "\",\"outcome\":\"STARTED\"}");

        String result = tool(authenticated()).refreshProjectBaseline(SLUG, null, null);

        assertThat(result).contains("STARTED").contains(ANALYSIS_ID.toString());
    }

    @Test
    void refreshForwardsTheTargetRevision() {
        when(adminClient.executeProjectUnderstandingAuthenticated("Bearer test", PROJECT_ID,
                Map.of("sourceId", SOURCE_ID.toString(), "targetRevision", "abc123")))
                .thenReturn("{\"outcome\":\"REUSED\"}");

        assertThat(tool(authenticated()).refreshProjectBaseline(SLUG, " abc123 ", null)).contains("REUSED");
    }

    @Test
    void statusReturnsTheBoundedAnalysisAndFreshness() {
        when(adminClient.getAnalysisAuthenticated("Bearer test", ANALYSIS_ID)).thenReturn(
                "{\"id\":\"" + ANALYSIS_ID + "\",\"projectId\":\"" + PROJECT_ID + "\",\"type\":\"PROJECT_UNDERSTANDING\","
                        + "\"status\":\"COMPLETED\",\"userGuidance\":{\"secret\":\"x\"},"
                        + "\"selectedSourceSnapshot\":{\"resolvedRevision\":\"rev-1\"}}");
        when(adminClient.getProjectFreshnessSummaryAuthenticated("Bearer test", PROJECT_ID))
                .thenReturn("{\"status\":\"CURRENT\",\"checkedSources\":[]}");

        JsonNode result = objectMapper.readTree(
                tool(authenticated()).getProjectBaselineStatus(SLUG, ANALYSIS_ID.toString(), null));

        assertThat(result.path("analysis").path("status").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("analysis").path("sourceRevision").asText()).isEqualTo("rev-1");
        assertThat(result.path("analysis").has("userGuidance")).isFalse();
        assertThat(result.path("freshness").path("status").asText()).isEqualTo("CURRENT");
    }

    @Test
    void statusRejectsAnAnalysisOfAnotherProject() {
        when(adminClient.getAnalysisAuthenticated("Bearer test", ANALYSIS_ID)).thenReturn(
                "{\"id\":\"" + ANALYSIS_ID + "\",\"projectId\":\"" + UUID.randomUUID() + "\",\"status\":\"COMPLETED\"}");

        assertThatThrownBy(() -> tool(authenticated()).getProjectBaselineStatus(SLUG, ANALYSIS_ID.toString(), null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found for project");
        verify(adminClient, never()).getProjectFreshnessSummaryAuthenticated(any(), any());
    }

    @Test
    void statusRejectsAMalformedAnalysisId() {
        assertThatThrownBy(() -> tool(authenticated()).getProjectBaselineStatus(SLUG, "not-a-uuid", null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void unauthenticatedCallsAreRefused() {
        ProjectBaselineTool tool = tool(Optional::empty);

        assertThatThrownBy(() -> tool.refreshProjectBaseline(SLUG, null, null)).isInstanceOf(McpError.class);
        assertThatThrownBy(() -> tool.getProjectBaselineStatus(SLUG, ANALYSIS_ID.toString(), null))
                .isInstanceOf(McpError.class);
        verify(adminClient, never()).executeProjectUnderstandingAuthenticated(any(), any(), any());
    }
}
