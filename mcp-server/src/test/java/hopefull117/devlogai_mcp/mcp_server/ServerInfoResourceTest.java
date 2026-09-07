package hopefull117.devlogai_mcp.mcp_server;

import hopefull117.devlogai_mcp.mcp_server.resource.ServerInfoResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ServerInfoResourceTest {

    @Autowired
    private ServerInfoResource serverInfoResource;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldReturnServerInfo() {
        JsonNode result = objectMapper.readTree(serverInfoResource.getServerInfo());

        assertThat(result.path("name").asText()).isEqualTo("devlog-mcp");
        assertThat(result.path("version").asText()).isEqualTo("0.1.0");
        assertThat(result.path("status").asText()).isEqualTo("ready");
        assertThat(names(result.path("tools"))).containsExactly(
                "analyze_story_context",
                "get_engineering_context",
                "search_project_history");
        assertThat(values(result.path("tools"), "description"))
                .allSatisfy(description -> assertThat(description).isNotBlank());
        assertThat(result.path("prompts").isArray()).isTrue();
        assertThat(result.path("prompts")).isEmpty();
        assertThat(names(result.path("resources"))).containsExactly(
                "project-commit-context",
                "project-context",
                "project-decision",
                "project-engineering-event",
                "project-freshness",
                "project-insight",
                "project-story",
                "projects",
                "server-info");
        assertThat(values(result.path("resources"), "uri"))
                .contains("devlog://server/info")
                .contains("devlog://projects/{projectSlug}/stories/{storyId}");
        assertThat(values(result.path("resources"), "description"))
                .allSatisfy(description -> assertThat(description).isNotBlank());
    }

    private static List<String> names(JsonNode capabilities) {
        return values(capabilities, "name").stream().sorted().toList();
    }

    private static List<String> values(JsonNode capabilities, String field) {
        List<String> values = new ArrayList<>();
        capabilities.forEach(capability -> values.add(capability.path(field).asText()));
        return values;
    }
}
