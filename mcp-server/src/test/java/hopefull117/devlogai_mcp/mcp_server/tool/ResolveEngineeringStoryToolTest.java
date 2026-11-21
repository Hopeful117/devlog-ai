package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.projectcontext.ProjectContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogResourceClient;
import hopefull117.devlogai_mcp.mcp_server.resource.ResourceSupport;
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

class ResolveEngineeringStoryToolTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID STORY_ID = UUID.randomUUID();

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient = mock(DevlogResourceClient.class);
    private final ResolveEngineeringStoryTool tool = new ResolveEngineeringStoryTool(
            resourceClient,
            new ResourceSupport(projectContextClient, resourceClient, new ObjectMapper()));

    @Test
    void shouldResolveStoryNumberThroughCore() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "digest", "ACTIVE", List.of()));
        String response = """
                {"id":"%s","projectId":"%s","storyNumber":223,
                 "title":"Pilot","storyPath":"docs/stories/0223/story.md"}
                """.formatted(STORY_ID, PROJECT_ID);
        when(resourceClient.resolveStoryByNumber(PROJECT_ID, 223)).thenReturn(response);

        assertThat(tool.resolveEngineeringStory(SLUG, 223, null))
                .contains(STORY_ID.toString())
                .contains("\"storyNumber\":223");

        verify(resourceClient).resolveStoryByNumber(PROJECT_ID, 223);
    }

    @Test
    void shouldRejectNonPositiveStoryNumber() {
        assertThatThrownBy(() -> tool.resolveEngineeringStory(SLUG, 0, null))
                .hasMessageContaining("storyNumber must be a positive integer");
    }

    @Test
    void shouldMapUnknownStoryToMcpNotFound() {
        when(projectContextClient.getProjectContext(SLUG)).thenReturn(
                new ProjectContext(PROJECT_ID, SLUG, SLUG, "digest", "ACTIVE", List.of()));
        when(resourceClient.resolveStoryByNumber(PROJECT_ID, 223)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> tool.resolveEngineeringStory(SLUG, 223, null))
                .hasMessageContaining("Story 223 not found in project 'devlog-ai'");
    }
}
