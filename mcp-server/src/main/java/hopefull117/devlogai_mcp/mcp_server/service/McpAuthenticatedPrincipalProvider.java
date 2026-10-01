package hopefull117.devlogai_mcp.mcp_server.service;

import java.util.Optional;

/** MCP authentication boundary; the default provider is deliberately fail-closed. */
public interface McpAuthenticatedPrincipalProvider {
    Optional<McpPrincipal> currentPrincipal();

    enum PrincipalKind { HUMAN, AI_AGENT, SYSTEM }

    record McpPrincipal(String principalId, PrincipalKind kind, String authenticationSource) { }
}
