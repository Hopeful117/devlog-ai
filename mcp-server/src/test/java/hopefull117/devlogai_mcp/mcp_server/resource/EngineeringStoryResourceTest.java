package hopefull117.devlogai_mcp.mcp_server.resource;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineeringStoryResourceTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final UUID STORY_ID =
            UUID.fromString("cccc1111-2222-3333-4444-555555555555");

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient =
            mock(DevlogResourceClient.class);
    private final EngineeringStoryResource resource = new EngineeringStoryResource(
            resourceClient,
            new ResourceSupport(projectContextClient, resourceClient,
                    new ObjectMapper()));

    @BeforeEach
    void resolveProject() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "d", "ACTIVE", List.of()));
        when(projectContextClient.getProjectContext("unknown")).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnStoryWithIdentityStatusAndCommits() {
        String story = """
                {"id":"%s","projectId":"%s","storyNumber":88,"title":"MCP resources",
                 "status":"COMPLETED","storyPath":"docs/stories/0088",
                 "baseCommit":"abc123","targetCommit":"def456"}"""
                .formatted(STORY_ID, PROJECT_ID);
        when(resourceClient.getStory(PROJECT_ID, STORY_ID)).thenReturn(story);

        String result = resource.getStory(SLUG, STORY_ID.toString());

        assertThat(result)
                .contains("\"storyNumber\":88")
                .contains("\"status\":\"COMPLETED\"")
                .contains("\"baseCommit\":\"abc123\"");
        verify(resourceClient).getStory(PROJECT_ID, STORY_ID);
    }

    @Test
    void shouldRejectStoryOwnedByAnotherProjectThroughServerSideScope() {
        when(resourceClient.getStory(PROJECT_ID, STORY_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resource.getStory(SLUG, STORY_ID.toString()))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found in project");
    }
}
