package com.hopeful117.devlogai.authorization;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class McpDelegationGrantServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Test
    void issuesAndValidatesOpaqueDelegationForProjectOwner() {
        var grants = mock(McpDelegationGrantRepository.class);
        var projects = mock(ProjectRepository.class);
        var memberships = mock(ProjectMembershipRepository.class);
        var project = Project.builder().id(PROJECT_ID).slug("devlog-ai").build();
        when(projects.findBySlug("devlog-ai")).thenReturn(Optional.of(project));
        when(memberships.existsByPrincipalIdAndProjectIdAndRoleIn(
                "human-1", PROJECT_ID, List.of(ProjectRole.PROJECT_OWNER))).thenReturn(true);
        when(grants.save(any(McpDelegationGrant.class))).thenAnswer(invocation -> {
            McpDelegationGrant grant = invocation.getArgument(0);
            grant.setId(UUID.randomUUID());
            return grant;
        });
        var service = service(grants, projects, memberships);

        var issued = service.issue(
                new AuthenticatedPrincipal("human-1", PrincipalKind.HUMAN, "issuer"),
                "devlog-ai", List.of("MCP_STORY_AGENT_EXECUTE"), 600L);

        assertThat(issued.token()).isNotBlank();
        assertThat(issued.token()).doesNotContain("human-1");
        verify(grants).save(argThat(grant ->
                grant.getHumanPrincipalId().equals("human-1")
                        && grant.getTechnicalActorId().equals("mcp-client")
                        && grant.getProjectId().equals(PROJECT_ID)));
        McpDelegationGrant stored = McpDelegationGrant.builder()
                .id(issued.grantId())
                .tokenHash(hash(issued.token()))
                .technicalActorId("mcp-client")
                .humanPrincipalId("human-1")
                .audience("devlog-mcp")
                .projectId(PROJECT_ID)
                .scopes("MCP_STORY_AGENT_EXECUTE")
                .status(McpDelegationStatus.ACTIVE)
                .issuedAt(NOW)
                .expiresAt(issued.expiresAt())
                .build();
        when(grants.findByTokenHash(hash(issued.token()))).thenReturn(Optional.of(stored));

        var identity = service.validate(issued.token(), "mcp-client", "devlog-mcp");

        assertThat(identity.humanPrincipalId()).isEqualTo("human-1");
        assertThat(identity.projectId()).isEqualTo(PROJECT_ID);
        assertThat(identity.scopes()).isEqualTo("MCP_STORY_AGENT_EXECUTE");
    }

    @Test
    void rejectsNonOwnerAndExpiredDelegation() {
        var grants = mock(McpDelegationGrantRepository.class);
        var projects = mock(ProjectRepository.class);
        var memberships = mock(ProjectMembershipRepository.class);
        var project = Project.builder().id(PROJECT_ID).slug("devlog-ai").build();
        when(projects.findBySlug("devlog-ai")).thenReturn(Optional.of(project));
        when(memberships.existsByPrincipalIdAndProjectIdAndRoleIn(
                "human-1", PROJECT_ID, List.of(ProjectRole.PROJECT_OWNER))).thenReturn(false);
        var service = service(grants, projects, memberships);

        assertThatThrownBy(() -> service.issue(
                new AuthenticatedPrincipal("human-1", PrincipalKind.HUMAN, "issuer"),
                "devlog-ai", List.of("MCP_STORY_AGENT_EXECUTE"), null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        McpDelegationGrant expired = McpDelegationGrant.builder()
                .id(UUID.randomUUID()).tokenHash(hash("expired"))
                .technicalActorId("mcp-client").humanPrincipalId("human-1")
                .audience("devlog-mcp").projectId(PROJECT_ID)
                .scopes("MCP_STORY_AGENT_EXECUTE").status(McpDelegationStatus.ACTIVE)
                .issuedAt(NOW.minusSeconds(20)).expiresAt(NOW.minusSeconds(1)).build();
        when(grants.findByTokenHash(hash("expired"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.validate("expired", "mcp-client", "devlog-mcp"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void rejectsActorAudienceAndScopeMismatchesAndRevokedGrant() {
        var grants = mock(McpDelegationGrantRepository.class);
        var projects = mock(ProjectRepository.class);
        var memberships = mock(ProjectMembershipRepository.class);
        var service = service(grants, projects, memberships);
        UUID grantId = UUID.randomUUID();
        McpDelegationGrant grant = McpDelegationGrant.builder()
                .id(grantId).tokenHash(hash("active"))
                .technicalActorId("mcp-client").humanPrincipalId("human-1")
                .audience("devlog-mcp").projectId(PROJECT_ID)
                .scopes("MCP_ENGINEERING_CONTEXT_READ").status(McpDelegationStatus.ACTIVE)
                .issuedAt(NOW.minusSeconds(10)).expiresAt(NOW.plusSeconds(100)).build();
        when(grants.findByTokenHash(hash("active"))).thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> service.validate("active", "other-client", "devlog-mcp"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.validate("active", "mcp-client", "other-audience"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        when(grants.findByIdAndHumanPrincipalId(grantId, "human-1"))
                .thenReturn(Optional.of(grant));
        service.revoke(new AuthenticatedPrincipal("human-1", PrincipalKind.HUMAN, "issuer"), grantId);
        assertThat(grant.getStatus()).isEqualTo(McpDelegationStatus.REVOKED);
        assertThatThrownBy(() -> service.validate("active", "mcp-client", "devlog-mcp"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void rejectsUnsupportedDelegationScopeAndNonHumanIssuer() {
        var grants = mock(McpDelegationGrantRepository.class);
        var projects = mock(ProjectRepository.class);
        var memberships = mock(ProjectMembershipRepository.class);
        var project = Project.builder().id(PROJECT_ID).slug("devlog-ai").build();
        when(projects.findBySlug("devlog-ai")).thenReturn(Optional.of(project));
        when(memberships.existsByPrincipalIdAndProjectIdAndRoleIn(any(), any(), any())).thenReturn(true);
        var service = service(grants, projects, memberships);

        assertThatThrownBy(() -> service.issue(
                new AuthenticatedPrincipal("agent", PrincipalKind.AI_AGENT, "issuer"),
                "devlog-ai", List.of("MCP_ENGINEERING_CONTEXT_WRITE"), null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        assertThatThrownBy(() -> service.issue(
                new AuthenticatedPrincipal("human-1", PrincipalKind.HUMAN, "issuer"),
                "devlog-ai", List.of("MCP_ENGINEERING_CONTEXT_WRITE"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private McpDelegationGrantService service(
            McpDelegationGrantRepository grants,
            ProjectRepository projects,
            ProjectMembershipRepository memberships) {
        return new McpDelegationGrantService(
                grants, projects, memberships,
                Clock.fixed(NOW, ZoneOffset.UTC), "mcp-client", "devlog-mcp");
    }

    private static String hash(String token) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
