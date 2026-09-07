package hopefull117.devlogai_mcp.mcp_server.resource;

import io.modelcontextprotocol.server.McpSyncServer;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
public class ServerInfoResource {

    private final ObjectMapper objectMapper;
    private final ObjectProvider<McpSyncServer> mcpServer;

    public ServerInfoResource(
            ObjectMapper objectMapper,
            ObjectProvider<McpSyncServer> mcpServer
    ) {
        this.objectMapper = objectMapper;
        this.mcpServer = mcpServer;
    }

    @McpResource(
            uri = "devlog://server/info",
            name = "server-info",
            description = "Describes the DevLog MCP server and its operational tools, prompts, and resources",
            mimeType = "application/json"
    )

    public String getServerInfo() {
        McpSyncServer server = mcpServer.getObject();
        List<CapabilityInfo> toolInfo = server.listTools().stream()
                .map(tool -> new CapabilityInfo(tool.name(), tool.description()))
                .sorted(Comparator.comparing(CapabilityInfo::name))
                .toList();

        List<CapabilityInfo> promptInfo = server.listPrompts().stream()
                .map(prompt -> new CapabilityInfo(prompt.name(), prompt.description()))
                .sorted(Comparator.comparing(CapabilityInfo::name))
                .toList();

        Stream<ResourceInfo> staticResources = server.listResources().stream()
                .map(resource -> new ResourceInfo(
                        resource.name(), resource.uri(), resource.description()));
        Stream<ResourceInfo> templateResources = server.listResourceTemplates().stream()
                .map(resource -> new ResourceInfo(
                        resource.name(), resource.uriTemplate(), resource.description()));
        List<ResourceInfo> resourceInfo = Stream.concat(staticResources, templateResources)
                .sorted(Comparator.comparing(ResourceInfo::uri).thenComparing(ResourceInfo::name))
                .toList();

        return objectMapper.writeValueAsString(new ServerInfo(
                server.getServerInfo().name(),
                server.getServerInfo().version(),
                "ready",
                toolInfo,
                promptInfo,
                resourceInfo));
    }

    record ServerInfo(
            String name,
            String version,
            String status,
            List<CapabilityInfo> tools,
            List<CapabilityInfo> prompts,
            List<ResourceInfo> resources
    ) {
    }

    record CapabilityInfo(String name, String description) {
    }

    record ResourceInfo(String name, String uri, String description) {
    }
}
