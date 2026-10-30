package com.hopeful117.devlogai.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestAuthenticatedPrincipalResolverTest {
    @Test
    void mapsVerifiedJwtSubjectToHumanPrincipal() {
        Jwt jwt = Jwt.withTokenValue("redacted")
                .header("alg", "none")
                .subject("keycloak-subject")
                .issuer("https://keycloak/realms/devlog")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(claims -> claims.putAll(Map.of()))
                .build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_test")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);

        var principal = new RequestAuthenticatedPrincipalResolver("https://keycloak/realms/devlog")
                .resolve(request);

        assertThat(principal).get().extracting(AuthenticatedPrincipal::principalId)
                .isEqualTo("keycloak-subject");
        assertThat(principal).get().extracting(AuthenticatedPrincipal::kind)
                .isEqualTo(PrincipalKind.HUMAN);
    }

    @Test
    void mapsAnAlreadyAuthenticatedJwtPrincipal() {
        Jwt jwt = Jwt.withTokenValue("redacted").header("alg", "none")
                .subject("keycloak-subject").issuer("https://other")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_test")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);

        assertThat(new RequestAuthenticatedPrincipalResolver("https://keycloak/realms/devlog")
                .resolve(request)).get().extracting(AuthenticatedPrincipal::principalId)
                .isEqualTo("keycloak-subject");
    }

    @Test
    void mapsSpringResourceServerJwtPrincipal() {
        Jwt jwt = Jwt.withTokenValue("redacted")
                .header("alg", "none")
                .subject("keycloak-subject")
                .issuer("https://keycloak/realms/devlog")
                .issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_test")));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);

        var principal = new RequestAuthenticatedPrincipalResolver("https://keycloak/realms/devlog")
                .resolve(request);

        assertThat(principal).get().extracting(AuthenticatedPrincipal::principalId)
                .isEqualTo("keycloak-subject");
    }
}
