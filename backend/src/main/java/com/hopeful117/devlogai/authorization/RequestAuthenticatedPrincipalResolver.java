package com.hopeful117.devlogai.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * Adapts an already-authenticated request principal. It deliberately does not
 * interpret headers, project slugs, or arbitrary caller input as identity.
 */
@Component
public class RequestAuthenticatedPrincipalResolver implements AuthenticatedPrincipalResolver {
    private final String issuer;
    private final String mcpActorClientId;
    private final String mcpAgentPrincipalId;
    private final String delegationAudience;
    private final McpDelegationGrantService delegationService;

    public RequestAuthenticatedPrincipalResolver() {
        this("", "", "", "devlog-mcp", null);
    }

    public RequestAuthenticatedPrincipalResolver(
            String issuer) {
        this(issuer, "", "", "devlog-mcp", null);
    }

    public RequestAuthenticatedPrincipalResolver(
            String issuer, String mcpActorClientId, String delegationAudience,
            McpDelegationGrantService delegationService) {
        this(issuer, mcpActorClientId, "", delegationAudience, delegationService, true);
    }

    @Autowired
    public RequestAuthenticatedPrincipalResolver(
            @Value("${devlog.security.oidc.issuer-uri:}") String issuer,
            @Value("${devlog.security.mcp.actor-client-id:}") String mcpActorClientId,
            @Value("${devlog.security.mcp.agent-principal-id:}") String mcpAgentPrincipalId,
            @Value("${devlog.security.mcp.delegation-audience:devlog-mcp}") String delegationAudience,
            McpDelegationGrantService delegationService) {
        this(issuer, mcpActorClientId, mcpAgentPrincipalId, delegationAudience, delegationService, true);
    }

    private RequestAuthenticatedPrincipalResolver(
            String issuer, String mcpActorClientId, String mcpAgentPrincipalId,
            String delegationAudience,
            McpDelegationGrantService delegationService, boolean ignored) {
        this.issuer = issuer == null ? "" : issuer.trim();
        this.mcpActorClientId = mcpActorClientId == null ? "" : mcpActorClientId.trim();
        this.mcpAgentPrincipalId = mcpAgentPrincipalId == null ? "" : mcpAgentPrincipalId.trim();
        this.delegationAudience = delegationAudience == null ? "" : delegationAudience.trim();
        this.delegationService = delegationService;
    }

    @Override
    public Optional<AuthenticatedPrincipal> resolve(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (principal == null || principal instanceof AnonymousAuthenticationToken) {
            principal = SecurityContextHolder.getContext().getAuthentication();
        }
        if (!(principal instanceof CoreRequestPrincipal corePrincipal)) {
            if (!(principal instanceof Authentication authentication)
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }
            if (issuer.isBlank()) {
                return Optional.empty();
            }
            if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                return resolveJwt(jwtAuthenticationToken.getToken(), request, true);
            }
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                return resolveJwt(jwt, request, false);
            }
            if (authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal oauth2Principal) {
                String principalId = oauth2Principal.getName();
                if (principalId == null || principalId.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new AuthenticatedPrincipal(principalId, PrincipalKind.HUMAN, issuer));
            }
            String principalId = authentication.getName();
            if (principalId == null || principalId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedPrincipal(principalId, PrincipalKind.HUMAN, issuer));
        }
        return Optional.of(corePrincipal.authenticatedPrincipal());
    }

    private Optional<AuthenticatedPrincipal> resolveJwt(
            Jwt jwt, HttpServletRequest request, boolean alreadyValidated) {
        if (jwt == null || (!alreadyValidated && !issuer.isBlank()
                && !issuer.equals(String.valueOf(jwt.getIssuer())))
                || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return Optional.empty();
        }
        String actorId = claim(jwt, "azp");
        if (actorId == null) actorId = claim(jwt, "client_id");
        boolean clientCredentials = "client_credentials".equals(normalizedGrantType(claim(jwt, "gty")));
        if (clientCredentials && (mcpActorClientId.isBlank() || !mcpActorClientId.equals(actorId))) {
            return Optional.empty();
        }
        if (actorId != null && actorId.equals(mcpActorClientId) && !mcpActorClientId.isBlank()) {
            String delegation = request.getHeader("X-DevLog-Delegation");
            if (delegation == null || delegation.isBlank()) {
                if (mcpAgentPrincipalId.isBlank()) return Optional.empty();
                return Optional.of(new AuthenticatedPrincipal(
                        mcpAgentPrincipalId, PrincipalKind.AI_AGENT, issuer,
                        actorId, null, null, null));
            }
            if (delegationService == null || delegationAudience.isBlank()) return Optional.empty();
            try {
                McpDelegationGrantService.DelegatedIdentity identity = delegationService.validate(
                        delegation, actorId, delegationAudience);
                return Optional.of(new AuthenticatedPrincipal(
                        identity.humanPrincipalId(), PrincipalKind.HUMAN, issuer,
                        identity.technicalActorId(), identity.grantId().toString(),
                        identity.projectId().toString(), identity.scopes()));
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.of(new AuthenticatedPrincipal(jwt.getSubject(), PrincipalKind.HUMAN, issuer));
    }

    private String claim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        return value instanceof String string && !string.isBlank() ? string : null;
    }

    private String normalizedGrantType(String grantType) {
        return grantType == null ? null : grantType.trim().toLowerCase().replace('-', '_');
    }

    /** Authentication integration boundary for an HTTP security module. */
    public interface CoreRequestPrincipal extends Principal {
        AuthenticatedPrincipal authenticatedPrincipal();
    }
}
