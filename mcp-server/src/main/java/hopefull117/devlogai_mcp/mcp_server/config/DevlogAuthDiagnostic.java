package hopefull117.devlogai_mcp.mcp_server.config;

import org.springframework.web.client.RestClientResponseException;
import org.springframework.http.HttpHeaders;

import java.util.Set;

/** Reads only the allowlisted authentication diagnostic emitted by DevLog Core. */
public final class DevlogAuthDiagnostic {
    public static final String HEADER_NAME = "X-DevLog-Auth-Diagnostic";

    private static final Set<String> ALLOWED = Set.of(
            "TOKEN_EXPIRED",
            "ISSUER_INVALID",
            "AUDIENCE_INVALID",
            "SIGNATURE_INVALID",
            "TOKEN_INVALID",
            "AUTHENTICATION_FAILED",
            "PRINCIPAL_RESOLUTION_FAILED");

    private DevlogAuthDiagnostic() {
    }

    public static String from(RestClientResponseException exception) {
        HttpHeaders headers = exception.getResponseHeaders();
        String value = headers == null ? null : headers.getFirst(HEADER_NAME);
        return value != null && ALLOWED.contains(value) ? value : null;
    }
}
