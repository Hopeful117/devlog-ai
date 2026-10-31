package com.hopeful117.devlogai.authorization;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreProjectAuthorizationServiceTest {
    @Test
    void autonomousAgentCanAccessConfiguredProjectsWithoutDelegation() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
        Project project = project("devlog-ai");
        CoreProjectAuthorizationService service = new CoreProjectAuthorizationService(
                projects, memberships, "opencode-agent", "devlog-ai,other-project");

        assertThat(service.isAuthorized(agent(), project, "MCP_ENGINEERING_CONTEXT_READ")).isTrue();
    }

    @Test
    void autonomousAgentCannotAccessUnconfiguredProject() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
        Project project = project("private-project");
        CoreProjectAuthorizationService service = new CoreProjectAuthorizationService(
                projects, memberships, "opencode-agent", "devlog-ai");

        assertThat(service.isAuthorized(agent(), project, "MCP_ENGINEERING_CONTEXT_READ")).isFalse();
    }

    @Test
    void delegatedPrincipalStillRequiresGrantScopeAndMembership() {
        ProjectRepository projects = mock(ProjectRepository.class);
        ProjectMembershipRepository memberships = mock(ProjectMembershipRepository.class);
        Project project = project("devlog-ai");
        when(memberships.existsByPrincipalIdAndProjectIdAndRoleIn(any(), any(), any())).thenReturn(true);
        CoreProjectAuthorizationService service = new CoreProjectAuthorizationService(
                projects, memberships, "opencode-agent", "devlog-ai");
        AuthenticatedPrincipal delegated = new AuthenticatedPrincipal(
                "human", PrincipalKind.HUMAN, "keycloak", "opencode-agent",
                "grant", project.getId().toString(), "MCP_ENGINEERING_CONTEXT_READ");

        assertThat(service.isAuthorized(delegated, project, "MCP_ENGINEERING_CONTEXT_READ")).isTrue();
        assertThat(service.isAuthorized(delegated, project, "MCP_STORY_AGENT_EXECUTE")).isFalse();
    }

    private static Project project(String slug) {
        return Project.builder()
                .id(UUID.randomUUID())
                .name(slug)
                .slug(slug)
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    private static AuthenticatedPrincipal agent() {
        return new AuthenticatedPrincipal("opencode-agent", PrincipalKind.AI_AGENT, "keycloak");
    }
}
