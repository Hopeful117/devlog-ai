package com.hopeful117.devlogai.authorization;

import java.util.Objects;

/** Provider-neutral identity passed to Core capabilities after authentication. */
public record AuthenticatedPrincipal(
        String principalId,
        PrincipalKind kind,
        String authenticationSource
) {
    public AuthenticatedPrincipal {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
        Objects.requireNonNull(kind, "kind must not be null");
        if (authenticationSource == null || authenticationSource.isBlank()) {
            throw new IllegalArgumentException("authenticationSource must not be blank");
        }
    }
}
