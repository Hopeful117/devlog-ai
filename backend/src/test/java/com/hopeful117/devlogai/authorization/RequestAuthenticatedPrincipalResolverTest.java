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
import java.util.UUID;

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

    @Test
    void mapsTechnicalActorToConfiguredAgentPrincipalWithoutHumanDelegation() {
        Jwt jwt = Jwt.withTokenValue("redacted").header("alg", "none")
                .subject("service-account-subject").issuer("https://keycloak/realms/devlog")
                .claim("azp", "devlog-mcp")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);

        var principal = new RequestAuthenticatedPrincipalResolver(
                "https://keycloak/realms/devlog", "devlog-mcp", "opencode-agent-subject",
                "devlog-mcp",
                mock(McpDelegationGrantService.class)).resolve(request);

        assertThat(principal).get().extracting(AuthenticatedPrincipal::principalId)
                .isEqualTo("opencode-agent-subject");
        assertThat(principal).get().extracting(AuthenticatedPrincipal::kind)
                .isEqualTo(PrincipalKind.AI_AGENT);
        assertThat(principal).get().extracting(AuthenticatedPrincipal::technicalActorId)
                .isEqualTo("devlog-mcp");
    }

    @Test
    void resolvesTechnicalActorThroughCoreDelegation() {
        Jwt jwt = Jwt.withTokenValue("redacted").header("alg", "none")
                .subject("service-account-subject").issuer("https://keycloak/realms/devlog")
                .claim("azp", "devlog-mcp")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);
        when(request.getHeader("X-DevLog-Delegation")).thenReturn("delegation-token");
        var delegationService = mock(McpDelegationGrantService.class);
        UUID grantId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(delegationService.validate("delegation-token", "devlog-mcp", "devlog-mcp"))
                .thenReturn(new McpDelegationGrantService.DelegatedIdentity(
                        grantId, "human-1", projectId, "devlog-mcp", "MCP_ENGINEERING_CONTEXT_READ"));

        var principal = new RequestAuthenticatedPrincipalResolver(
                "https://keycloak/realms/devlog", "devlog-mcp", "devlog-mcp", delegationService)
                .resolve(request);

        assertThat(principal).get().extracting(AuthenticatedPrincipal::principalId)
                .isEqualTo("human-1");
        assertThat(principal).get().extracting(AuthenticatedPrincipal::kind)
                .isEqualTo(PrincipalKind.HUMAN);
        assertThat(principal).get().extracting(AuthenticatedPrincipal::technicalActorId)
                .isEqualTo("devlog-mcp");
        assertThat(principal).get().extracting(AuthenticatedPrincipal::delegatedProjectId)
                .isEqualTo(projectId.toString());
    }

    @Test
    void rejectsUnregisteredClientCredentialsInsteadOfMappingThemToHuman() {
        Jwt jwt = Jwt.withTokenValue("redacted").header("alg", "none")
                .subject("service-account-subject").issuer("https://keycloak/realms/devlog")
                .claim("azp", "unknown-service")
                .claim("gty", "client-credentials")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);

        assertThat(new RequestAuthenticatedPrincipalResolver(
                "https://keycloak/realms/devlog", "devlog-mcp", "devlog-mcp",
                mock(McpDelegationGrantService.class)).resolve(request)).isEmpty();
    }

    @Test
    void rejectsUnregisteredUnderscoreClientCredentialsInsteadOfMappingThemToHuman() {
        Jwt jwt = Jwt.withTokenValue("redacted").header("alg", "none")
                .subject("service-account-subject").issuer("https://keycloak/realms/devlog")
                .claim("azp", "unknown-service")
                .claim("gty", "client_credentials")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);

        assertThat(new RequestAuthenticatedPrincipalResolver(
                "https://keycloak/realms/devlog", "devlog-mcp", "devlog-mcp",
                mock(McpDelegationGrantService.class)).resolve(request)).isEmpty();
    }

    @Test
    void rejectsConfiguredTechnicalActorWhenAgentPrincipalIsNotProvisioned() {
        Jwt jwt = Jwt.withTokenValue("redacted").header("alg", "none")
                .subject("service-account-subject").issuer("https://keycloak/realms/devlog")
                .claim("azp", "devlog-mcp").claim("gty", "client_credentials")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        HttpServletRequest request = mock(HttpServletRequest.class);
        var authentication = new JwtAuthenticationToken(jwt, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        when(request.getUserPrincipal()).thenReturn(authentication);

        assertThat(new RequestAuthenticatedPrincipalResolver(
                "https://keycloak/realms/devlog", "devlog-mcp", "", "devlog-mcp",
                mock(McpDelegationGrantService.class)).resolve(request)).isEmpty();
    }
}
