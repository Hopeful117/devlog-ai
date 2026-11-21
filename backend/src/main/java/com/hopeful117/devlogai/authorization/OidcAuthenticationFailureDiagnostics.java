package com.hopeful117.devlogai.authorization;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/** Logs and exposes a bounded, non-secret reason for rejected bearer tokens. */
@Component
public final class OidcAuthenticationFailureDiagnostics implements AuthenticationEntryPoint {
    public static final String HEADER_NAME = "X-DevLog-Auth-Diagnostic";

    private static final Logger log = LoggerFactory.getLogger(OidcAuthenticationFailureDiagnostics.class);
    private static final BearerTokenAuthenticationEntryPoint DEFAULT_ENTRY_POINT =
            new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        Diagnostic diagnostic = classify(exception);
        response.setHeader(HEADER_NAME, diagnostic.code());
        log.warn(
                "OIDC authentication rejected method={} path={} diagnostic={} oauthErrorCode={} exceptionType={}",
                request.getMethod(),
                request.getRequestURI(),
                diagnostic.code(),
                oauthErrorCode(exception),
                exception.getClass().getSimpleName());
        DEFAULT_ENTRY_POINT.commence(request, response, exception);
    }

    static Diagnostic classify(AuthenticationException exception) {
        String details = details(exception);
        if (containsAny(details, "expired", "expiration", "exp claim")) {
            return new Diagnostic("TOKEN_EXPIRED");
        }
        if (containsAny(details, "issuer", "iss claim")) {
            return new Diagnostic("ISSUER_INVALID");
        }
        if (containsAny(details, "audience", "aud claim")) {
            return new Diagnostic("AUDIENCE_INVALID");
        }
        if (containsAny(details, "signature", "jwk", "jwks")) {
            return new Diagnostic("SIGNATURE_INVALID");
        }
        if ("invalid_token".equals(oauthErrorCode(exception))) {
            return new Diagnostic("TOKEN_INVALID");
        }
        return new Diagnostic("AUTHENTICATION_FAILED");
    }

    private static String oauthErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error error = oauth2Exception.getError();
            return error == null ? null : error.getErrorCode();
        }
        return null;
    }

    private static String details(AuthenticationException exception) {
        StringBuilder details = new StringBuilder();
        append(details, exception.getMessage());
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error error = oauth2Exception.getError();
            if (error != null) {
                append(details, error.getErrorCode());
                append(details, error.getDescription());
            }
        }
        Throwable cause = exception.getCause();
        while (cause != null) {
            append(details, cause.getMessage());
            cause = cause.getCause();
        }
        return details.toString().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder target, String value) {
        if (value != null && !value.isBlank()) {
            target.append(value).append(' ');
        }
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    record Diagnostic(String code) {
    }
}
