package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.mapper.AiTaskMapper;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.ProjectMembershipRepository;
import com.hopeful117.devlogai.authorization.ProjectRole;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorizedStoryContextSnapshotReader {
    private static final Duration SNAPSHOT_TTL = Duration.ofDays(30);
    private static final List<ProjectRole> READ_ROLES = List.of(
            ProjectRole.PROJECT_READER, ProjectRole.PROJECT_OWNER);

    private final AiTaskRepository aiTaskRepository;
    private final ProjectMembershipRepository membershipRepository;
    private final AiTaskMapper aiTaskMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AiTaskResponse readStoryContextSnapshot(
        AuthenticatedPrincipal principal, UUID snapshotId) {
        if (principal == null) {
            throw new com.hopeful117.devlogai.authorization.UnauthenticatedPrincipalException();
        }

        // This projection intentionally excludes all JSON snapshot columns.
        var identity = aiTaskRepository.findStoryContextSnapshotIdentity(snapshotId)
                .orElseThrow(() -> notFound(snapshotId));
        Instant expiryOrigin = identity.createdAt();
        if (identity.parentSnapshotId() != null) {
            expiryOrigin = aiTaskRepository.findStoryContextSnapshotIdentity(identity.parentSnapshotId())
                    .map(SnapshotIdentity::createdAt)
                    .orElse(null);
        }
        if (identity.taskType() != AiTaskType.STORY_CONTEXT_ANALYSIS
                || expiryOrigin == null
                || !membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(
                        principal.principalId(), identity.projectId(), READ_ROLES)
                || !clock.instant().isBefore(expiryOrigin.plus(SNAPSHOT_TTL))) {
            throw notFound(snapshotId);
        }

        // Detailed materialization is deliberately after authentication, scope,
        // membership, and TTL checks.
        return aiTaskRepository.findById(snapshotId)
                .map(aiTaskMapper::toResponse)
                .orElseThrow(() -> notFound(snapshotId));
    }

    private EntityNotFoundException notFound(UUID snapshotId) {
        return new EntityNotFoundException("Story Context snapshot", snapshotId);
    }

}
