package hopefull117.devlogai_mcp.mcp_server.resource;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

@Component
public class ServerInfoResource {
    @McpResource(
            uri = "devlog://server/info",
            name="server-info",
            description = "Provides information about the Devlog MCP server",
            mimeType = "application/json"
    )

    public String getServerInfo() {
        return """
            {
              "name": "devlog-mcp",
              "version": "0.1.0",
              "status": "ready"
            }
            """;
    }

}
