package hopefull117.devlogai_mcp.mcp_server.service;

import io.modelcontextprotocol.spec.McpError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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
    void rateLimitRetryAfterHeaderDoesNotChangeCurrentTerminalClassification() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "5");
        McpError withHeader = CoreCallPolicy.map(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests", headers, new byte[0], null),
                "not found", false, "reading the resource");

        java.util.Map<?, ?> data = (java.util.Map<?, ?>) withHeader.getJsonRpcError().data();
        assertThat(data.get("code")).isEqualTo("RATE_LIMITED");
        assertThat(data.get("retryable")).isEqualTo(false);
        assertThat(data.get("retryAfterSeconds")).isEqualTo(5L);
    }

    @Test
    void rateLimitRetryAfterIsBoundedAndInvalidValuesAreOmitted() {
        HttpHeaders excessiveHeaders = new HttpHeaders();
        excessiveHeaders.set("Retry-After", "120");
        McpError excessive = CoreCallPolicy.map(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests", excessiveHeaders, new byte[0], null),
                "not found", false, "reading the resource");

        HttpHeaders invalidHeaders = new HttpHeaders();
        invalidHeaders.set("Retry-After", "not-a-delay");
        McpError invalid = CoreCallPolicy.map(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests", invalidHeaders, new byte[0], null),
                "not found", false, "reading the resource");

        assertThat(((java.util.Map<?, ?>) excessive.getJsonRpcError().data()).get("retryAfterSeconds"))
                .isEqualTo(60L);
        assertThat(((java.util.Map<?, ?>) invalid.getJsonRpcError().data()).get("retryAfterSeconds"))
                .isNull();
    }

    @Test
    void parsesHttpDateRetryAfterDeterministicallyAndRejectsExpiredDates() {
        Instant now = Instant.parse("2026-11-20T17:00:00Z");
        String future = ZonedDateTime.ofInstant(now.plusSeconds(30), ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        String expired = ZonedDateTime.ofInstant(now.minusSeconds(1), ZoneOffset.UTC)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);

        assertThat(CoreCallPolicy.parseRetryAfter(future, now)).isEqualTo(30L);
        assertThat(CoreCallPolicy.parseRetryAfter(expired, now)).isNull();
        assertThat(CoreCallPolicy.parseRetryAfter("-1", now)).isNull();
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

    @Test
    void resilienceMatrixMapsCoreStatusesToTerminalDiagnostics() {
        var expected = java.util.Map.of(
                HttpStatus.UNAUTHORIZED, "CORE_AUTHENTICATION_FAILED",
                HttpStatus.FORBIDDEN, "CORE_AUTHORIZATION_FAILED",
                HttpStatus.BAD_REQUEST, "FUNCTIONAL_ERROR",
                HttpStatus.NOT_FOUND, "CORE_RESOURCE_NOT_FOUND",
                HttpStatus.CONFLICT, "CORE_CONFLICT",
                HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                HttpStatus.BAD_GATEWAY, "TRANSIENT_INFRASTRUCTURE");

        expected.forEach((status, code) -> {
            McpError error = map(status);
            java.util.Map<?, ?> data = (java.util.Map<?, ?>) error.getJsonRpcError().data();
            assertThat(data.get("code")).isEqualTo(code);
            assertThat(data.get("retryable")).isEqualTo(false);
        });
    }

    @Test
    void localInvalidParamsCarryFunctionalDiagnostics() {
        McpError error = CoreCallPolicy.invalidParams("Invalid resource identifier");

        assertThat(error.getJsonRpcError().data())
                .isEqualTo(java.util.Map.of(
                        "source", "CORE", "category", "FUNCTIONAL",
                        "code", "FUNCTIONAL_ERROR", "layer", "CORE",
                        "retryable", false, "action", "CHECK_OPERATION_PARAMETERS"));
    }

    private static McpError map(HttpStatus status) {
        return CoreCallPolicy.map(response(status), "not found", false, "reading the resource");
    }

    private static HttpClientErrorException response(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(),
                HttpHeaders.EMPTY, new byte[0], null);
    }
}
