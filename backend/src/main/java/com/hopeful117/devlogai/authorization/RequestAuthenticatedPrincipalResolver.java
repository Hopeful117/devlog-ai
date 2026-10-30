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

    public RequestAuthenticatedPrincipalResolver() {
        this.issuer = "";
    }

    @Autowired
    public RequestAuthenticatedPrincipalResolver(
            @Value("${devlog.security.oidc.issuer-uri:}") String issuer) {
        this.issuer = issuer == null ? "" : issuer.trim();
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
                String principalId = jwtAuthenticationToken.getName();
                if (principalId == null || principalId.isBlank()) {
                    principalId = jwtAuthenticationToken.getToken().getSubject();
                }
                if (principalId == null || principalId.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new AuthenticatedPrincipal(principalId, PrincipalKind.HUMAN, issuer));
            }
            if (authentication.getPrincipal() instanceof Jwt jwt) {
                if (!issuer.isBlank() && !issuer.equals(jwt.getIssuer())
                        || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(new AuthenticatedPrincipal(jwt.getSubject(), PrincipalKind.HUMAN, issuer));
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

    /** Authentication integration boundary for an HTTP security module. */
    public interface CoreRequestPrincipal extends Principal {
        AuthenticatedPrincipal authenticatedPrincipal();
    }
}
