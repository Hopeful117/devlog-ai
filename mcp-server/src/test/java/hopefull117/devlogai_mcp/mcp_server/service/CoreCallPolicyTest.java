package hopefull117.devlogai_mcp.mcp_server.service;

import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;

class CoreCallPolicyTest {

    @Test
    void mapsAuthorizationFailureToCoreCategory() {
        McpError error = map(HttpStatus.FORBIDDEN);

        assertThat(error.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "CORE_AUTHORIZATION",
                        "code", "CORE_AUTHORIZATION_FAILED", "layer", "CORE",
                        "retryable", false, "action", "CHECK_PROJECT_ACCESS"));
    }

    @Test
    void hidesForbiddenResourceExistenceWhenRequested() {
        McpError error = CoreCallPolicy.map(
                response(HttpStatus.FORBIDDEN), "resource not found", true, "reading the resource");

        assertThat(error.getJsonRpcError().message()).isEqualTo("resource not found");
        assertThat(error.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "FUNCTIONAL",
                        "code", "CORE_RESOURCE_NOT_FOUND", "layer", "CORE",
                        "retryable", false, "action", "CHECK_RESOURCE_IDENTIFIER"));
    }

    @Test
    void mapsRateLimitWithoutRetryMetadata() {
        McpError error = map(HttpStatus.TOO_MANY_REQUESTS);

        assertThat(error.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "RATE_LIMITED",
                        "code", "RATE_LIMITED", "layer", "CORE",
                        "retryable", false, "action", "WAIT_OR_RETRY_WITH_POLICY"));
    }

    @Test
    void mapsServerAndTransportFailuresToTransientInfrastructure() {
        McpError serverError = CoreCallPolicy.map(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway",
                        HttpHeaders.EMPTY, new byte[0], null),
                "not found", false, "reading the resource");
        McpError transportError = CoreCallPolicy.map(
                new ResourceAccessException("secret response details"),
                "not found", false, "reading the resource");

        assertThat(serverError.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "TRANSIENT_INFRASTRUCTURE",
                        "code", "TRANSIENT_INFRASTRUCTURE", "layer", "CORE",
                        "retryable", false, "action", "RETRY_ONLY_IF_OPERATION_SAFE"));
        assertThat(transportError.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "TRANSIENT_INFRASTRUCTURE",
                        "code", "TRANSIENT_INFRASTRUCTURE", "layer", "CORE",
                        "retryable", false, "action", "RETRY_ONLY_IF_OPERATION_SAFE"));
        assertThat(transportError.getJsonRpcError().message()).doesNotContain("secret");
    }

    @Test
    void mapsBadRequestAndConflictWithoutBackendDetails() {
        McpError badRequest = map(HttpStatus.BAD_REQUEST);
        McpError conflict = map(HttpStatus.CONFLICT);

        assertThat(badRequest.getJsonRpcError().message())
                .isEqualTo("DevLog backend rejected the request");
        assertThat(badRequest.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "FUNCTIONAL",
                        "code", "FUNCTIONAL_ERROR", "layer", "CORE",
                        "retryable", false, "action", "CHECK_OPERATION_PARAMETERS"));
        assertThat(conflict.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "CORE_CONFLICT",
                        "code", "CORE_CONFLICT", "layer", "CORE",
                        "retryable", false, "action", "RECONCILE_OPERATION_STATUS"));
    }

    private static McpError map(HttpStatus status) {
        return CoreCallPolicy.map(response(status), "not found", false, "reading the resource");
    }

    private static HttpClientErrorException response(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(),
                HttpHeaders.EMPTY, new byte[0], null);
    }
}
