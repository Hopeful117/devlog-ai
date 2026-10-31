package com.hopeful117.devlogai.authorization;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Core-owned project authorization for human, delegated, and autonomous callers. */
@Service
public class CoreProjectAuthorizationService {
    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final String agentPrincipalId;
    private final Set<String> agentProjectSlugs;

    public CoreProjectAuthorizationService(
            ProjectRepository projectRepository,
            ProjectMembershipRepository membershipRepository,
            @Value("${devlog.security.mcp.agent-principal-id:}") String agentPrincipalId,
            @Value("${devlog.security.mcp.agent-projects:}") String agentProjects) {
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.agentPrincipalId = agentPrincipalId == null ? "" : agentPrincipalId.trim();
        this.agentProjectSlugs = parseProjectSlugs(agentProjects);
    }

    public boolean isAuthorized(AuthenticatedPrincipal principal, Project project, String requiredScope) {
        if (principal == null) {
            return true;
        }
        if (principal.isDelegated()) {
            return project.getId().toString().equals(principal.delegatedProjectId())
                    && principal.hasDelegatedScope(requiredScope)
                    && hasMembership(principal, project.getId());
        }
        if (principal.kind() == PrincipalKind.AI_AGENT
                && principal.principalId().equals(agentPrincipalId)) {
            return agentProjectSlugs.contains(project.getSlug());
        }
        return hasMembership(principal, project.getId());
    }

    public boolean isAuthorized(AuthenticatedPrincipal principal, UUID projectId, String requiredScope) {
        return projectRepository.findById(projectId)
                .map(project -> isAuthorized(principal, project, requiredScope))
                .orElse(false);
    }

    private boolean hasMembership(AuthenticatedPrincipal principal, UUID projectId) {
        return membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(
                principal.principalId(), projectId,
                java.util.List.of(ProjectRole.PROJECT_READER, ProjectRole.PROJECT_OWNER));
    }

    private Set<String> parseProjectSlugs(String configuredProjects) {
        if (configuredProjects == null || configuredProjects.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredProjects.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
