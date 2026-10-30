package hopefull117.devlogai_mcp.mcp_server.service;

import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;

import java.util.Optional;

/** MCP authentication boundary; the default provider is deliberately fail-closed. */
public interface McpAuthenticatedPrincipalProvider {
    Optional<McpPrincipal> currentPrincipal();

    default Optional<McpPrincipal> currentPrincipal(McpSyncRequestContext requestContext) {
        return currentPrincipal();
    }

    enum PrincipalKind { HUMAN, AI_AGENT, SYSTEM }

    record McpPrincipal(String principalId, PrincipalKind kind, String authenticationSource,
                        String bearerToken) {
        public McpPrincipal(String principalId, PrincipalKind kind, String authenticationSource) {
            this(principalId, kind, authenticationSource, null);
        }
    }
}
