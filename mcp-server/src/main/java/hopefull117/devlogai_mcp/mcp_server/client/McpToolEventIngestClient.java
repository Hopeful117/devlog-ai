package hopefull117.devlogai_mcp.mcp_server.client;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1")
public interface McpToolEventIngestClient {

    @PostExchange("/mcp/tool-events")
    void ingest(
            @RequestBody McpToolEventIngestPayload request,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation);
}
