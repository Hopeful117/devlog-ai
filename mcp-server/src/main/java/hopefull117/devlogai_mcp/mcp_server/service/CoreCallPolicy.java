package hopefull117.devlogai_mcp.mcp_server.service;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import hopefull117.devlogai_mcp.mcp_server.config.DevlogAuthDiagnostic;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/** Shared, non-retrying policy for failures returned by DevLog Core. */
public final class CoreCallPolicy {

    static final long MAX_RETRY_AFTER_SECONDS = 60;

    private CoreCallPolicy() {
    }

    public static McpError map(
            Exception exception,
            String notFoundMessage,
            boolean hideForbidden,
            String operation
    ) {
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatus status = HttpStatus.resolve(responseException.getStatusCode().value());
            if (status == HttpStatus.UNAUTHORIZED) {
                return coreAuthenticationFailed(
                        "Authentication is required to access DevLog backend",
                        DevlogAuthDiagnostic.from(responseException));
            }
            if (status == HttpStatus.FORBIDDEN) {
                return hideForbidden
                        ? notFound(notFoundMessage)
                        : coreAuthorizationFailed("DevLog backend refused access");
            }
            if (status == HttpStatus.NOT_FOUND) {
                return notFound(notFoundMessage);
            }
            if (status == HttpStatus.BAD_REQUEST) {
                return functionalFailure("DevLog backend rejected the request");
            }
            if (status == HttpStatus.CONFLICT) {
                return coreConflict("DevLog backend rejected the operation");
            }
            if (status == HttpStatus.TOO_MANY_REQUESTS) {
                return rateLimited("DevLog backend rate limit reached",
                        retryAfterSeconds(responseException));
            }
            if (status != null && status.is5xxServerError()) {
                return transientInfrastructure("DevLog backend is unavailable");
            }
            return coreInternal("DevLog backend rejected the request");
        }
        if (exception instanceof RestClientException) {
            return transientInfrastructure("DevLog backend is unavailable");
        }
        return coreInternal("DevLog backend failed while " + operation);
    }

    public static McpError notFound(String message) {
        return categorizedWithErrorCode(ErrorCodes.RESOURCE_NOT_FOUND, message, "FUNCTIONAL",
                "CORE_RESOURCE_NOT_FOUND", "CHECK_RESOURCE_IDENTIFIER");
    }

    public static McpError invalidParams(String message) {
        return functionalFailure(message);
    }

    private static McpError functionalFailure(String message) {
        return categorizedWithErrorCode(ErrorCodes.INVALID_PARAMS, message, "FUNCTIONAL",
                "FUNCTIONAL_ERROR", "CHECK_OPERATION_PARAMETERS");
    }

    public static McpError unauthenticated(String message) {
        return McpError.builder(ErrorCodes.INVALID_REQUEST)
                .message(message)
                .build();
    }

    public static McpError coreAuthenticationFailed(String message) {
        return coreAuthenticationFailed(message, null);
    }

    private static McpError coreAuthenticationFailed(String message, String diagnostic) {
        Map<String, Object> data = new java.util.HashMap<>(Map.of(
                "source", "CORE",
                "category", "CORE_AUTHENTICATION",
                "code", "CORE_AUTHENTICATION_FAILED",
                "layer", "CORE",
                "retryable", false,
                "action", "CHECK_CORE_AUTHORIZATION"));
        if (diagnostic != null) {
            data.put("authDiagnostic", diagnostic);
        }
        return McpError.builder(ErrorCodes.INVALID_REQUEST)
                .message(message)
                .data(data)
                .build();
    }

    public static McpError coreAuthorizationFailed(String message) {
        return categorized(message, "CORE_AUTHORIZATION", "CORE_AUTHORIZATION_FAILED",
                "CHECK_PROJECT_ACCESS");
    }

    public static McpError rateLimited(String message) {
        return rateLimited(message, null);
    }

    private static McpError rateLimited(String message, Long retryAfterSeconds) {
        return categorized(message, "RATE_LIMITED", "RATE_LIMITED",
                "WAIT_OR_RETRY_WITH_POLICY", retryAfterSeconds);
    }

    public static McpError coreConflict(String message) {
        return categorized(message, "CORE_CONFLICT", "CORE_CONFLICT",
                "RECONCILE_OPERATION_STATUS");
    }

    public static McpError transientInfrastructure(String message) {
        return categorized(message, "TRANSIENT_INFRASTRUCTURE", "TRANSIENT_INFRASTRUCTURE",
                "RETRY_ONLY_IF_OPERATION_SAFE");
    }

    private static McpError categorized(
            String message,
            String category,
            String code,
            String action
    ) {
        return categorized(message, category, code, action, null);
    }

    private static McpError categorized(
            String message,
            String category,
            String code,
            String action,
            Long retryAfterSeconds
    ) {
        Map<String, Object> data = new java.util.HashMap<>(Map.of(
                "source", "CORE",
                "category", category,
                "code", code,
                "layer", "CORE",
                "retryable", false,
                "action", action));
        if (retryAfterSeconds != null) {
            data.put("retryAfterSeconds", retryAfterSeconds);
        }
        return McpError.builder(ErrorCodes.INVALID_REQUEST)
                .message(message)
                .data(data)
                .build();
    }

    private static Long retryAfterSeconds(RestClientResponseException exception) {
        String value = exception.getResponseHeaders().getFirst("Retry-After");
        return parseRetryAfter(value, Instant.now());
    }

    static Long parseRetryAfter(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return boundedRetryAfter(seconds);
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                long seconds = Math.max(0, (long) Math.ceil(
                        Duration.between(now, retryAt).toMillis() / 1000.0));
                return boundedRetryAfter(seconds);
            } catch (DateTimeParseException ignoredDate) {
                return null;
            }
        }
    }

    private static Long boundedRetryAfter(long seconds) {
        if (seconds <= 0) {
            return null;
        }
        return Math.min(seconds, MAX_RETRY_AFTER_SECONDS);
    }

    private static McpError internal(String message) {
        return McpError.builder(ErrorCodes.INTERNAL_ERROR)
                .message(message)
                .build();
    }

    private static McpError coreInternal(String message) {
        return categorizedWithErrorCode(ErrorCodes.INTERNAL_ERROR, message, "UNKNOWN",
                "CORE_INTERNAL_ERROR", "ESCALATE_WITH_CORRELATION");
    }

    private static McpError categorizedWithErrorCode(
            int errorCode,
            String message,
            String category,
            String code,
            String action
    ) {
        return McpError.builder(errorCode)
                .message(message)
                .data(Map.of(
                        "source", "CORE",
                        "category", category,
                        "code", code,
                        "layer", "CORE",
                        "retryable", false,
                        "action", action))
                .build();
    }
}
