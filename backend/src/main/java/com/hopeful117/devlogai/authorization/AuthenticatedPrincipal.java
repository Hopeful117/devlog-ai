package com.hopeful117.devlogai.authorization;

import java.util.Objects;

/** Provider-neutral identity passed to Core capabilities after authentication. */
public record AuthenticatedPrincipal(
        String principalId,
        PrincipalKind kind,
        String authenticationSource,
        String technicalActorId,
        String delegationGrantId,
        String delegatedProjectId,
        String delegatedScopes
) {
    public AuthenticatedPrincipal(String principalId, PrincipalKind kind, String authenticationSource) {
        this(principalId, kind, authenticationSource, null, null, null, null);
    }

    public AuthenticatedPrincipal {
        if (principalId == null || principalId.isBlank()) {
            throw new IllegalArgumentException("principalId must not be blank");
        }
        Objects.requireNonNull(kind, "kind must not be null");
        if (authenticationSource == null || authenticationSource.isBlank()) {
            throw new IllegalArgumentException("authenticationSource must not be blank");
        }
    }

    public boolean isDelegated() {
        return technicalActorId != null && delegationGrantId != null;
    }

    public boolean hasDelegatedScope(String scope) {
        return !isDelegated() || (delegatedScopes != null
                && java.util.Set.of(delegatedScopes.split(" ")).contains(scope));
    }
}
