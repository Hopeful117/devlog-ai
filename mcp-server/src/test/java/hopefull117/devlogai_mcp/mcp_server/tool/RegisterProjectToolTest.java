package hopefull117.devlogai_mcp.mcp_server.tool;

import hopefull117.devlogai_mcp.mcp_server.client.DevlogAdminClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterProjectToolTest {

    private static final String PROJECT_RESPONSE = """
            {"id":"11111111-1111-1111-1111-111111111111","name":"Claude Context Optimizer",
             "slug":"claude-context-optimizer","description":null,"status":"ACTIVE",
             "createdAt":"2026-11-17T10:00:00Z","updatedAt":"2026-11-17T10:00:00Z"}""";

    private static final String SOURCE_RESPONSE = """
            {"id":"22222222-2222-2222-2222-222222222222",
             "projectId":"11111111-1111-1111-1111-111111111111",
             "type":"GIT_REPOSITORY","name":"claude-context-optimizer",
             "repositoryUrl":"https://github.com/Hopeful117/claude-context-optimizer",
             "defaultBranch":"main","provider":"GITHUB","active":true,
             "lastSynchronizedAt":null,"createdAt":"2026-11-17T10:00:00Z","updatedAt":"2026-11-17T10:00:00Z"}""";

    private final DevlogAdminClient adminClient = mock(DevlogAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RegisterProjectTool tool = new RegisterProjectTool(adminClient, objectMapper);

    @Test
    void shouldCreateProjectOnlyWhenNoRepositoryUrlProvided() throws Exception {
        when(adminClient.createProject(any())).thenReturn(PROJECT_RESPONSE);

        String result = tool.registerProject("Claude Context Optimizer", null, null, null);

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("project").path("slug").asText()).isEqualTo("claude-context-optimizer");
        assertThat(json.has("source")).isFalse();
        verify(adminClient).createProject(Map.of("name", "Claude Context Optimizer"));
        verify(adminClient, never()).createSource(any());
    }

    @Test
    void shouldCreateProjectAndSourceWhenRepositoryUrlProvided() throws Exception {
        when(adminClient.createProject(any())).thenReturn(PROJECT_RESPONSE);
        when(adminClient.createSource(any())).thenReturn(SOURCE_RESPONSE);

        String result = tool.registerProject(
                "Claude Context Optimizer",
                null,
                "https://github.com/Hopeful117/claude-context-optimizer",
                null
        );

        JsonNode json = objectMapper.readTree(result);
        assertThat(json.path("project").path("slug").asText()).isEqualTo("claude-context-optimizer");
        assertThat(json.path("source").path("provider").asText()).isEqualTo("GITHUB");
        assertThat(json.path("source").path("defaultBranch").asText()).isEqualTo("main");
        assertThat(json.path("source").path("repositoryUrl").asText())
                .isEqualTo("https://github.com/Hopeful117/claude-context-optimizer");
    }

    @Test
    void shouldDetectGitHubProvider() throws Exception {
        when(adminClient.createProject(any())).thenReturn(PROJECT_RESPONSE);
        when(adminClient.createSource(any())).thenReturn(SOURCE_RESPONSE);

        tool.registerProject("My Project", null, "https://github.com/owner/repo", null);

        verify(adminClient).createSource(org.mockito.ArgumentMatchers.argThat(req ->
                "GITHUB".equals(req.get("provider"))));
    }

    @Test
    void shouldDetectGitLabProvider() throws Exception {
        when(adminClient.createProject(any())).thenReturn(PROJECT_RESPONSE);
        String gitlabSource = SOURCE_RESPONSE.replace("GITHUB", "GITLAB")
                .replace("github.com", "gitlab.com");
        when(adminClient.createSource(any())).thenReturn(gitlabSource);

        tool.registerProject("My Project", null, "https://gitlab.com/owner/repo", null);

        verify(adminClient).createSource(org.mockito.ArgumentMatchers.argThat(req ->
                "GITLAB".equals(req.get("provider"))));
    }

    @Test
    void shouldUseExplicitDefaultBranch() throws Exception {
        when(adminClient.createProject(any())).thenReturn(PROJECT_RESPONSE);
        when(adminClient.createSource(any())).thenReturn(SOURCE_RESPONSE);

        tool.registerProject(
                "My Project", null, "https://github.com/owner/repo", "develop");

        verify(adminClient).createSource(org.mockito.ArgumentMatchers.argThat(req ->
                "develop".equals(req.get("defaultBranch"))));
    }

    @Test
    void shouldDeriveSourceNameFromRepositoryUrl() throws Exception {
        when(adminClient.createProject(any())).thenReturn(PROJECT_RESPONSE);
        when(adminClient.createSource(any())).thenReturn(SOURCE_RESPONSE);

        tool.registerProject(
                "My Project", null, "https://github.com/owner/my-awesome-repo.git", null);

        verify(adminClient).createSource(org.mockito.ArgumentMatchers.argThat(req ->
                "my-awesome-repo".equals(req.get("name"))));
    }

    @Test
    void shouldIncludeDescriptionWhenProvided() throws Exception {
        when(adminClient.createProject(any())).thenReturn(PROJECT_RESPONSE);

        tool.registerProject("My Project", "A great project", null, null);

        verify(adminClient).createProject(org.mockito.ArgumentMatchers.argThat(req ->
                "A great project".equals(req.get("description"))));
    }

    @Test
    void preservesExistingMcpErrorFromMutationClient() {
        McpError expected = ResourceSupport.invalidParams("already mapped");
        when(adminClient.createProjectAuthenticated(anyString(), any()))
                .thenThrow(expected);
        RegisterProjectTool authenticatedTool = new RegisterProjectTool(
                adminClient, objectMapper, () -> java.util.Optional.of(
                        new McpAuthenticatedPrincipalProvider.McpPrincipal(
                                "principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                                "test", "Bearer test", "")));

        assertThatThrownBy(() -> authenticatedTool.registerProject(
                "My Project", null, null, null, null))
                .isSameAs(expected);
    }
}
