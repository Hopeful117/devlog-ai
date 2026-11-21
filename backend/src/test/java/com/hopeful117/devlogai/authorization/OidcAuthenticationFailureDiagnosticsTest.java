package com.hopeful117.devlogai.authorization;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class OidcAuthenticationFailureDiagnosticsTest {
    @Test
    void classifiesExpiredTokenWithoutExposingExceptionDetails() {
        var exception = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_token", "Jwt expired at 2026-11-20T18:00:00Z", null));

        assertThat(OidcAuthenticationFailureDiagnostics.classify(exception).code())
                .isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void classifiesIssuerAudienceAndSignatureFailures() {
        assertThat(classify("Invalid issuer claim")).isEqualTo("ISSUER_INVALID");
        assertThat(classify("Missing configured audience")).isEqualTo("AUDIENCE_INVALID");
        assertThat(classify("Unable to resolve the JWK set")).isEqualTo("SIGNATURE_INVALID");
    }

    @Test
    void fallsBackToBoundedAuthenticationCode() {
        var exception = new BadCredentialsException("secret token value");

        assertThat(OidcAuthenticationFailureDiagnostics.classify(exception).code())
                .isEqualTo("AUTHENTICATION_FAILED");
    }

    private static String classify(String description) {
        return OidcAuthenticationFailureDiagnostics.classify(
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token", description, null)))
                .code();
    }
}
