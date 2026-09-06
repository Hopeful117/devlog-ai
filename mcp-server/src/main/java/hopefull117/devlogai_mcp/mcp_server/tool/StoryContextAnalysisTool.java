package hopefull117.devlogai_mcp.mcp_server.tool;

import com.hopeful117.devlogai.contracts.storycontextanalysis.StoryContextAnalysisResult;
import hopefull117.devlogai_mcp.mcp_server.client.DevlogProjectContextClient;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StoryContextAnalysisTool {

    private final DevlogProjectContextClient devlogProjectContextClient;
    private final ObjectMapper objectMapper;

    @McpTool(
            name = "analyze_story_context",
            description = "Analyzes an Engineering Story context and produces a structured, grounded analysis for Discuss/Plan preparation"
    )
    public String analyzeStoryContext(
            @McpArg(description = "Slug identifying the DevLog project", required = true) String projectSlug,
            @McpArg(description = "Engineering Story UUID", required = true) UUID storyId,
            @McpArg(description = "Optional file paths to narrow technical evidence scope", required = false) List<String> files,
            @McpArg(description = "Optional human guidance for the analysis", required = false) Map<String, Object> guidance
    ) {
        DevlogProjectContextClient.AnalyzeContextRequest request = null;
        if (files != null || guidance != null) {
            request = new DevlogProjectContextClient.AnalyzeContextRequest(files, guidance);
        }

        StoryContextAnalysisResult result = devlogProjectContextClient.analyzeStoryContext(projectSlug, storyId, request);
        return objectMapper.writeValueAsString(result);
    }
}