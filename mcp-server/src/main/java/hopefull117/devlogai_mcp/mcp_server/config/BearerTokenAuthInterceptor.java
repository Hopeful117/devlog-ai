package hopefull117.devlogai_mcp.mcp_server.config;

import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import org.springframework.http.HttpRequest;

/**
 * Injects the configured bearer token on every outbound backend request
 * when no Authorization header is already present. This makes auth opt-out
 * rather than opt-in: call sites that explicitly pass Authorization via
 * @RequestHeader are unaffected; all others get the configured token
 * automatically.
 */
class BearerTokenAuthInterceptor implements ClientHttpRequestInterceptor {

    private final McpAuthenticatedPrincipalProvider principalProvider;

    BearerTokenAuthInterceptor(McpAuthenticatedPrincipalProvider principalProvider) {
        this.principalProvider = principalProvider;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        if (request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION) == null) {
            principalProvider.currentPrincipal().ifPresent(principal -> {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, principal.bearerToken());
                if (!principal.delegationToken().isBlank()) {
                    request.getHeaders().set("X-DevLog-Delegation", principal.delegationToken());
                }
            });
        }
        return execution.execute(request, body);
    }
}
