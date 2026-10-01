package com.hopeful117.devlogai.authorization;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {
    boolean existsByPrincipalIdAndProjectIdAndRoleIn(
            String principalId, UUID projectId, Iterable<ProjectRole> roles);
}
