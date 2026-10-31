package com.hopeful117.devlogai.authorization;

import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.List;
import java.util.UUID;

@Service
public class McpDelegationGrantService {
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    private static final Duration MAX_TTL = Duration.ofHours(24);
    private static final List<ProjectRole> DELEGATION_ROLES = List.of(
            ProjectRole.PROJECT_OWNER);
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            "MCP_ENGINEERING_CONTEXT_READ",
            "MCP_STORY_AGENT_EXECUTE",
            "MCP_STORY_AGENT_READ_RESULT");

    private final McpDelegationGrantRepository grantRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final Clock clock;
    private final String configuredActorId;
    private final String configuredAudience;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public McpDelegationGrantService(
            McpDelegationGrantRepository grantRepository,
            ProjectRepository projectRepository,
            ProjectMembershipRepository membershipRepository,
            Clock clock,
            @Value("${devlog.security.mcp.actor-client-id:}") String configuredActorId,
            @Value("${devlog.security.mcp.delegation-audience:devlog-mcp}") String configuredAudience) {
        this.grantRepository = grantRepository;
        this.projectRepository = projectRepository;
        this.membershipRepository = membershipRepository;
        this.clock = clock;
        this.configuredActorId = configuredActorId == null ? "" : configuredActorId.trim();
        this.configuredAudience = configuredAudience == null ? "" : configuredAudience.trim();
    }

    @Transactional
    public IssuedDelegation issue(AuthenticatedPrincipal humanPrincipal,
                                  String projectSlug,
                                  List<String> scopes,
                                  Long requestedTtlSeconds) {
        requireHuman(humanPrincipal);
        if (configuredActorId == null || configuredActorId.isBlank()) {
            throw new AccessDeniedException("MCP technical actor is not configured");
        }
        Project project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new AccessDeniedException("Project delegation is not authorized"));
        if (!membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(
                humanPrincipal.principalId(), project.getId(), DELEGATION_ROLES)) {
            throw new AccessDeniedException("Project delegation is not authorized");
        }
        List<String> normalizedScopes = normalizeScopes(scopes);
        Instant issuedAt = clock.instant();
        Duration ttl = requestedTtlSeconds == null
                ? DEFAULT_TTL
                : Duration.ofSeconds(requestedTtlSeconds);
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException("Delegation TTL must be between 1 second and 24 hours");
        }
        String token = randomToken();
        McpDelegationGrant grant = McpDelegationGrant.builder()
                .tokenHash(hash(token))
                .technicalActorId(configuredActorId.trim())
                .humanPrincipalId(humanPrincipal.principalId())
                .audience(configuredAudience.trim())
                .projectId(project.getId())
                .scopes(String.join(" ", normalizedScopes))
                .status(McpDelegationStatus.ACTIVE)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .build();
        grantRepository.save(grant);
        return new IssuedDelegation(grant.getId(), token, grant.getExpiresAt(), normalizedScopes);
    }

    @Transactional(readOnly = true)
    public DelegatedIdentity validate(String token, String technicalActorId, String audience) {
        if (token == null || token.isBlank() || technicalActorId == null || technicalActorId.isBlank()) {
            throw new AccessDeniedException("MCP delegation is required");
        }
        McpDelegationGrant grant = grantRepository.findByTokenHash(hash(token))
                .orElseThrow(() -> new AccessDeniedException("MCP delegation is invalid"));
        Instant now = clock.instant();
        if (grant.getStatus() != McpDelegationStatus.ACTIVE || !now.isBefore(grant.getExpiresAt())
                || !grant.getTechnicalActorId().equals(technicalActorId)
                || !grant.getAudience().equals(audience)) {
            throw new AccessDeniedException("MCP delegation is invalid or expired");
        }
        return new DelegatedIdentity(grant.getId(), grant.getHumanPrincipalId(), grant.getProjectId(),
                grant.getTechnicalActorId(), grant.getScopes());
    }

    @Transactional
    public void revoke(AuthenticatedPrincipal humanPrincipal, UUID grantId) {
        requireHuman(humanPrincipal);
        McpDelegationGrant grant = grantRepository.findByIdAndHumanPrincipalId(
                        grantId, humanPrincipal.principalId())
                .orElseThrow(() -> new AccessDeniedException("MCP delegation is not authorized"));
        if (grant.getStatus() == McpDelegationStatus.ACTIVE) {
            grant.setStatus(McpDelegationStatus.REVOKED);
            grant.setRevokedAt(clock.instant());
        }
    }

    private void requireHuman(AuthenticatedPrincipal principal) {
        if (principal == null || principal.kind() != PrincipalKind.HUMAN) {
            throw new AccessDeniedException("A human principal is required");
        }
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("At least one delegation scope is required");
        }
        List<String> normalized = scopes.stream()
                .map(scope -> scope == null ? "" : scope.trim())
                .filter(scope -> !scope.isBlank())
                .distinct()
                .toList();
        if (normalized.isEmpty() || normalized.stream().anyMatch(scope ->
                scope.length() > 100 || !ALLOWED_SCOPES.contains(scope))) {
            throw new IllegalArgumentException("Delegation scopes are invalid");
        }
        return normalized;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record IssuedDelegation(UUID grantId, String token, Instant expiresAt, List<String> scopes) {}

    public record DelegatedIdentity(UUID grantId, String humanPrincipalId, UUID projectId,
                                    String technicalActorId, String scopes) {}
}
