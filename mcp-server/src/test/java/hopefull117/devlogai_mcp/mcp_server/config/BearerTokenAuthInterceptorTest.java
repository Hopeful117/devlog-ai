package hopefull117.devlogai_mcp.mcp_server.config;

import hopefull117.devlogai_mcp.mcp_server.service.McpAuthenticatedPrincipalProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class BearerTokenAuthInterceptorTest {

    @Test
    void addsDelegationEvenWhenAuthorizationWasExplicitlyProvided() throws Exception {
        McpAuthenticatedPrincipalProvider provider = () -> Optional.of(
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                        "test", "Bearer current", "delegation-token"));
        BearerTokenAuthInterceptor interceptor = new BearerTokenAuthInterceptor(provider);
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer explicit");
        ClientHttpRequestExecution execution = (executedRequest, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer explicit");
        assertThat(request.getHeaders().getFirst("X-DevLog-Delegation"))
                .isEqualTo("delegation-token");
    }

    @Test
    void doesNotEmitBlankIdentityHeaders() throws Exception {
        McpAuthenticatedPrincipalProvider provider = () -> Optional.of(
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                        "test", null, ""));
        BearerTokenAuthInterceptor interceptor = new BearerTokenAuthInterceptor(provider);
        MockClientHttpRequest request = new MockClientHttpRequest();
        ClientHttpRequestExecution execution = (executedRequest, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        assertThat(request.getHeaders().getFirst("X-DevLog-Delegation")).isNull();
    }

    @Test
    void preservesExplicitDelegationHeader() throws Exception {
        McpAuthenticatedPrincipalProvider provider = () -> Optional.of(
                new McpAuthenticatedPrincipalProvider.McpPrincipal(
                        "principal", McpAuthenticatedPrincipalProvider.PrincipalKind.HUMAN,
                        "test", "Bearer current", "delegation-from-principal"));
        BearerTokenAuthInterceptor interceptor = new BearerTokenAuthInterceptor(provider);
        MockClientHttpRequest request = new MockClientHttpRequest();
        request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer explicit");
        request.getHeaders().set("X-DevLog-Delegation", "delegation-explicit");
        ClientHttpRequestExecution execution = (executedRequest, body) ->
                new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK);

        interceptor.intercept(request, new byte[0], execution);

        assertThat(request.getHeaders().getFirst("X-DevLog-Delegation"))
                .isEqualTo("delegation-explicit");
    }
}
