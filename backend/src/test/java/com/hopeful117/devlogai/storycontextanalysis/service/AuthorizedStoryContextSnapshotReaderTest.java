package com.hopeful117.devlogai.storycontextanalysis.service;

import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.mapper.AiTaskMapper;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.authorization.ProjectMembershipRepository;
import com.hopeful117.devlogai.authorization.ProjectRole;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizedStoryContextSnapshotReaderTest {
    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final AuthenticatedPrincipal PRINCIPAL = new AuthenticatedPrincipal(
            "human-1", PrincipalKind.HUMAN, "test");

    @Mock AiTaskRepository taskRepository;
    @Mock ProjectMembershipRepository membershipRepository;
    @Mock AiTaskMapper mapper;
    private AuthorizedStoryContextSnapshotReader reader;

    @BeforeEach
    void setUp() {
        reader = new AuthorizedStoryContextSnapshotReader(taskRepository, membershipRepository,
                mapper, Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void authorizedReaderMaterializesOnlyAfterMembershipAndTtlChecks() {
        var identity = identity(Instant.parse("2026-09-02T00:00:00Z"));
        var response = response();
        when(taskRepository.findStoryContextSnapshotIdentity(TASK_ID)).thenReturn(Optional.of(identity));
        when(membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(
                eq("human-1"), eq(PROJECT_ID), any()))
                .thenReturn(true);
        AiTask task = new AiTask();
        when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));
        when(mapper.toResponse(any())).thenReturn(response);

        reader.readStoryContextSnapshot(PRINCIPAL, TASK_ID);
        verify(taskRepository).findById(TASK_ID);
        verify(mapper).toResponse(task);
    }

    @Test
    void otherProjectIsNotDisclosedAndDetailedProjectionIsNotLoaded() {
        when(taskRepository.findStoryContextSnapshotIdentity(TASK_ID))
                .thenReturn(Optional.of(identity(Instant.parse("2026-09-01T00:00:00Z"))));
        when(membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(
                eq("human-1"), eq(PROJECT_ID), any())).thenReturn(false);

        assertThrows(EntityNotFoundException.class,
                () -> reader.readStoryContextSnapshot(PRINCIPAL, TASK_ID));
        verify(taskRepository, never()).findById(TASK_ID);
        verifyNoInteractions(mapper);
    }

    @Test
    void expiredSnapshotIsRejectedWithoutMutationOrProjection() {
        when(taskRepository.findStoryContextSnapshotIdentity(TASK_ID))
                .thenReturn(Optional.of(identity(Instant.parse("2026-08-31T23:59:59Z"))));
        when(membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(
                eq("human-1"), eq(PROJECT_ID), any())).thenReturn(true);

        assertThrows(EntityNotFoundException.class,
                () -> reader.readStoryContextSnapshot(PRINCIPAL, TASK_ID));
        verify(taskRepository, never()).findById(TASK_ID);
        verifyNoInteractions(mapper);
    }

    @Test
    void unknownSnapshotIsRejectedBeforeMembershipOrProjection() {
        when(taskRepository.findStoryContextSnapshotIdentity(TASK_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> reader.readStoryContextSnapshot(PRINCIPAL, TASK_ID));
        verifyNoInteractions(membershipRepository, mapper);
    }

    private SnapshotIdentity identity(Instant createdAt) {
        return new SnapshotIdentity(TASK_ID, PROJECT_ID, AiTaskType.STORY_CONTEXT_ANALYSIS, createdAt);
    }

    private AiTaskResponse response() {
        return new AiTaskResponse(TASK_ID, null, null, AiTaskType.STORY_CONTEXT_ANALYSIS,
                AiTaskStatus.COMPLETED, Map.of(), null, 0, null, null,
                Instant.now(), null, null, null);
    }
}
