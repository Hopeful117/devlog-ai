package com.hopeful117.devlogai.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface McpDelegationGrantRepository extends JpaRepository<McpDelegationGrant, UUID> {
    Optional<McpDelegationGrant> findByTokenHash(String tokenHash);

    Optional<McpDelegationGrant> findByIdAndHumanPrincipalId(UUID id, String humanPrincipalId);
}
