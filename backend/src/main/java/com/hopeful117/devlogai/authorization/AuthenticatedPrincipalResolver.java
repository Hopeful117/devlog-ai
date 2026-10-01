package com.hopeful117.devlogai.authorization;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface AuthenticatedPrincipalResolver {
    Optional<AuthenticatedPrincipal> resolve(HttpServletRequest request);
}
