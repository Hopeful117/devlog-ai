package hopefull117.devlogai_mcp.mcp_server.service;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.ErrorCodes;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/** Shared, non-retrying policy for failures returned by DevLog Core. */
public final class CoreCallPolicy {

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
                return coreAuthenticationFailed("Authentication is required to access DevLog backend");
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
                return rateLimited("DevLog backend rate limit reached");
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
        return categorized(message, "CORE_AUTHENTICATION", "CORE_AUTHENTICATION_FAILED",
                "CHECK_CORE_AUTHORIZATION");
    }

    public static McpError coreAuthorizationFailed(String message) {
        return categorized(message, "CORE_AUTHORIZATION", "CORE_AUTHORIZATION_FAILED",
                "CHECK_PROJECT_ACCESS");
    }

    public static McpError rateLimited(String message) {
        return categorized(message, "RATE_LIMITED", "RATE_LIMITED",
                "WAIT_OR_RETRY_WITH_POLICY");
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
        return McpError.builder(ErrorCodes.INVALID_REQUEST)
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
