package hopefull117.devlogai_mcp.mcp_server.service;

import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class FailClosedMcpAuthenticatedPrincipalProvider implements McpAuthenticatedPrincipalProvider {
    @Override
    public Optional<McpPrincipal> currentPrincipal() {
        return Optional.empty();
    }
}
