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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineeringStoryManagementToolTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID STORY_ID = UUID.randomUUID();
    private static final String TOKEN = "Bearer test-token";

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final DevlogAdminClient adminClient = mock(DevlogAdminClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResourceSupport support = new ResourceSupport(
            projectContextClient, resourceClient, objectMapper);

    private EngineeringStoryManagementTool tool;

    @BeforeEach
    void setUp() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "DevLog AI", "ACTIVE", List.of()));
        tool = new EngineeringStoryManagementTool(
                adminClient, resourceClient, support, principalProvider(), objectMapper);
    }

    @Test
    void registerStoryForwardsToAdminClient() {
        String storyJson = "{\"id\":\"" + STORY_ID + "\",\"storyNumber\":230,\"title\":\"My story\"}";
        when(adminClient.registerStoryAuthenticated(eq(TOKEN), eq(PROJECT_ID), any()))
                .thenReturn(storyJson);

        String result = tool.registerEngineeringStory(
                SLUG, "My story", 230, "docs/stories/0230-my-story/story.md", null);

        assertThat(result).contains("My story");
        verify(adminClient).registerStoryAuthenticated(eq(TOKEN), eq(PROJECT_ID), any());
    }

    @Test
    void listStoriesForwardsToAdminClient() {
        String listJson = "[{\"id\":\"" + STORY_ID + "\",\"storyNumber\":230}]";
        when(adminClient.listProjectStoriesAuthenticated(TOKEN, PROJECT_ID)).thenReturn(listJson);

        String result = tool.listProjectStories(SLUG, null);

        assertThat(result).contains("storyNumber");
        verify(adminClient).listProjectStoriesAuthenticated(TOKEN, PROJECT_ID);
    }

    @Test
    void startImplementationResolvesStoryIdThenCallsBackend() {
        String storyJson = "{\"id\":\"" + STORY_ID + "\",\"storyNumber\":230,\"status\":\"IN_PROGRESS\"}";
        when(resourceClient.resolveStoryByNumberAuthenticated(PROJECT_ID, 230, TOKEN, ""))
                .thenReturn(storyJson);
        when(adminClient.startStoryImplementationAuthenticated(eq(TOKEN), eq(PROJECT_ID),
                eq(STORY_ID), any())).thenReturn(storyJson);

        String result = tool.startStoryImplementation(SLUG, 230,
                "a".repeat(40), null);

        assertThat(result).contains("IN_PROGRESS");
        verify(adminClient).startStoryImplementationAuthenticated(
                eq(TOKEN), eq(PROJECT_ID), eq(STORY_ID),
                eq(Map.of("baseCommit", "a".repeat(40))));
    }

    @Test
    void completeImplementationResolvesStoryIdThenCallsBackend() {
        String storyJson = "{\"id\":\"" + STORY_ID + "\",\"storyNumber\":230,\"status\":\"COMPLETED\"}";
        when(resourceClient.resolveStoryByNumberAuthenticated(PROJECT_ID, 230, TOKEN, ""))
                .thenReturn(storyJson);
        when(adminClient.completeStoryImplementationAuthenticated(eq(TOKEN), eq(PROJECT_ID),
                eq(STORY_ID), any())).thenReturn(storyJson);

        String result = tool.completeStoryImplementation(SLUG, 230,
                "b".repeat(40), null);

        assertThat(result).contains("COMPLETED");
        verify(adminClient).completeStoryImplementationAuthenticated(
                eq(TOKEN), eq(PROJECT_ID), eq(STORY_ID),
                eq(Map.of("targetCommit", "b".repeat(40))));
    }

    @Test
    void startImplementationRejectsInvalidCommitSha() {
        assertThatThrownBy(() -> tool.startStoryImplementation(SLUG, 230, "not-a-sha", null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Invalid commit SHA");
    }

    @Test
    void completeImplementationRejectsInvalidCommitSha() {
        assertThatThrownBy(() -> tool.completeStoryImplementation(SLUG, 230, "short", null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Invalid commit SHA");
    }

    @Test
    void requiresAuthenticationForRegister() {
        EngineeringStoryManagementTool unauthTool = new EngineeringStoryManagementTool(
                adminClient, resourceClient, support, unauthenticatedProvider(), objectMapper);
        assertThatThrownBy(() -> unauthTool.registerEngineeringStory(
                SLUG, "title", 230, "docs/stories/0230/story.md", null))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("Authenticated bearer token is required");
    }

    private static McpAuthenticatedPrincipalProvider principalProvider() {
        return () -> java.util.Optional.of(new McpAuthenticatedPrincipalProvider.McpPrincipal(
                "test-principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                "test", TOKEN, ""));
    }

    private static McpAuthenticatedPrincipalProvider unauthenticatedProvider() {
        return () -> java.util.Optional.empty();
    }
}
