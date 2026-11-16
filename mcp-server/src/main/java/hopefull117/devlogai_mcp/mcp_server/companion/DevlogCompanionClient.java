package hopefull117.devlogai_mcp.mcp_server.companion;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.UUID;

@HttpExchange("/api/v1")
public interface DevlogCompanionClient {

    @GetExchange("/projects/{projectId}/mcp-operations")
    String listMcpOperations(
            @PathVariable("projectId") UUID projectId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size);

    @GetExchange("/projects/{projectId}/mcp-operations")
    String listMcpOperationsAuthenticated(
            @PathVariable("projectId") UUID projectId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("X-DevLog-Delegation") String delegation);
}
