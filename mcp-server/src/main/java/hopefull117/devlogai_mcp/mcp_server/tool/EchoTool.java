package hopefull117.devlogai_mcp.mcp_server.tool;


import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class EchoTool {
    @McpTool(name = "echo_message",description = "Returns the provided message unchanged")
    public String echoMessage(@McpToolParam(description = "Message to return",required = true) String message) {
        return message;
    }

}
