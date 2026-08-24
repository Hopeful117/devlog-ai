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
import static org.mockito.Mockito.when;

class EngineeringEventResourceTest {

    private static final String SLUG = "devlog-ai";
    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");
    private static final UUID EVENT_ID =
            UUID.fromString("eeee1111-2222-3333-4444-555555555555");

    private final DevlogProjectContextClient projectContextClient =
            mock(DevlogProjectContextClient.class);
    private final DevlogResourceClient resourceClient =
            mock(DevlogResourceClient.class);
    private final EngineeringEventResource resource = new EngineeringEventResource(
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
    void shouldReturnEventWithCategoryCommitsAndProvenance() {
        String event = """
                {"id":"%s","projectId":"%s","category":"FEATURE_INTRODUCTION",
                 "title":"Add markdown rendering","baseCommit":"abc123",
                 "targetCommit":"def456","proposalId":"p1","validationId":"v1",
                 "occurredAt":"2026-07-01T12:00:00Z"}"""
                .formatted(EVENT_ID, PROJECT_ID);
        when(resourceClient.getEngineeringEvent(EVENT_ID)).thenReturn(event);

        String result = resource.getEngineeringEvent(SLUG, EVENT_ID.toString());

        assertThat(result)
                .contains("\"category\":\"FEATURE_INTRODUCTION\"")
                .contains("\"baseCommit\":\"abc123\"")
                .contains("\"validationId\":\"v1\"");
    }

    @Test
    void shouldRejectEventFromAnotherProject() {
        String foreign = """
                {"id":"%s","projectId":"00000000-0000-0000-0000-000000000001",
                 "title":"Foreign event"}""".formatted(EVENT_ID);
        when(resourceClient.getEngineeringEvent(EVENT_ID)).thenReturn(foreign);

        assertThatThrownBy(() -> resource.getEngineeringEvent(SLUG, EVENT_ID.toString()))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("does not belong to project");
    }

    @Test
    void shouldRejectUnknownEvent() {
        when(resourceClient.getEngineeringEvent(EVENT_ID)).thenThrow(
                HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                        null, null, StandardCharsets.UTF_8));

        assertThatThrownBy(() -> resource.getEngineeringEvent(SLUG, EVENT_ID.toString()))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("not found");
    }
}
