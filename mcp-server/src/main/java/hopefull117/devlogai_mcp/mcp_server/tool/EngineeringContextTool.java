package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EngineeringContextTool {
    private final DevlogProjectContextClient devlogProjectContextClient;
    private final ObjectMapper objectMapper;
    @McpTool(
            name = "get_engineering_context",
            description = "Returns relevant engineering context for a project and engineering intent"
    )
    public String getEngineeringContext(
            @McpArg(
                    description = "Slug identifying the DevLog project",
                    required = true
            )
            String projectSlug,

            @McpArg(
                    description = "Engineering task or investigation for which relevant context is requested",
                    required = true
            )
            String intent,

            @McpArg(
                    description = "Optional file paths to narrow evidence scope",
                    required = false
            )
            List<String> files,

            @McpArg(
                    description = "Optional Engineering Story UUID to scope context to story commit window",
                    required = false
            )
            UUID storyId
    ) {
        EngineeringContext engineeringContext =
                devlogProjectContextClient.getEngineeringContext(
                        projectSlug,
                        intent,
                        files,
                        storyId
                );

        return objectMapper.writeValueAsString(engineeringContext);
    }
}