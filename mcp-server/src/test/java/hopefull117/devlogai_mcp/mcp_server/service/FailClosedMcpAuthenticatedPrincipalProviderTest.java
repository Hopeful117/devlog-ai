package hopefull117.devlogai_mcp.mcp_server.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class FailClosedMcpAuthenticatedPrincipalProviderTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preservesBearerTokenFromValidatedJwtAuthentication() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://issuer.example")
                .subject("principal-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(jwt, jwt, java.util.List.of()));

        var provider = new FailClosedMcpAuthenticatedPrincipalProvider(
                "https://issuer.example", "", mock(ObjectProvider.class));

        assertThat(provider.currentPrincipal()).hasValueSatisfying(principal -> {
            assertThat(principal.principalId()).isEqualTo("principal-1");
            assertThat(principal.bearerToken()).isEqualTo("Bearer token");
        });
    }

    @Test
    void validatesConfiguredBearerWhenStdioHasNoSecurityContext() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("https://issuer.example")
                .subject("stdio-principal")
                .build();
        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode("token")).thenReturn(jwt);
        ObjectProvider<JwtDecoder> decoders = mock(ObjectProvider.class);
        when(decoders.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(decoder);

        var provider = new FailClosedMcpAuthenticatedPrincipalProvider(
                "https://issuer.example", "token", decoders);

        assertThat(provider.currentPrincipal()).hasValueSatisfying(principal -> {
            assertThat(principal.principalId()).isEqualTo("stdio-principal");
            assertThat(principal.bearerToken()).isEqualTo("Bearer token");
        });
    }
}
