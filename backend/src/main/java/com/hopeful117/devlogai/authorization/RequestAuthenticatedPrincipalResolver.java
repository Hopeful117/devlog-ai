package com.hopeful117.devlogai.authorization;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * Adapts an already-authenticated request principal. It deliberately does not
 * interpret headers, project slugs, or arbitrary caller input as identity.
 */
@Component
public class RequestAuthenticatedPrincipalResolver implements AuthenticatedPrincipalResolver {
    @Override
    public Optional<AuthenticatedPrincipal> resolve(HttpServletRequest request) {
        Principal principal = request.getUserPrincipal();
        if (!(principal instanceof CoreRequestPrincipal corePrincipal)) {
            return Optional.empty();
        }
        return Optional.of(corePrincipal.authenticatedPrincipal());
    }

    /** Authentication integration boundary for an HTTP security module. */
    public interface CoreRequestPrincipal extends Principal {
        AuthenticatedPrincipal authenticatedPrincipal();
    }
}
