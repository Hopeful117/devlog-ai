package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.authorization.ProjectMembershipRepository;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.shared.exception.ConflictException;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.storycontextanalysis.service.SnapshotIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryAgentFollowUpServiceTest {
    private static final UUID PARENT_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final String CONTEXT_DIGEST = "a".repeat(64);
    private static final String PROJECTION_DIGEST = "b".repeat(64);
    private static final AuthenticatedPrincipal PRINCIPAL =
            new AuthenticatedPrincipal("human-1", PrincipalKind.HUMAN, "test");

    @Mock AiTaskRepository taskRepository;
    @Mock ProjectMembershipRepository membershipRepository;
    @Mock AiTaskService aiTaskService;
    @Mock AIEngineClient aiEngineClient;
    @Mock IntentCatalog intentCatalog;
    private StoryAgentFollowUpService service;
    private AiTask parent;

    @BeforeEach
    void setUp() {
        service = new StoryAgentFollowUpService(taskRepository, membershipRepository, aiTaskService,
                aiEngineClient, intentCatalog, new ObjectMapper(), new StoryContextDigestService(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
        parent = parent();
        lenient().when(taskRepository.findStoryContextSnapshotIdentity(PARENT_ID))
                .thenReturn(Optional.of(new SnapshotIdentity(PARENT_ID, PROJECT_ID,
                        AiTaskType.STORY_CONTEXT_ANALYSIS, Instant.parse("2026-09-15T00:00:00Z"))));
        lenient().when(membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(eq("human-1"), eq(PROJECT_ID), any()))
                .thenReturn(true);
        lenient().when(taskRepository.findById(PARENT_ID)).thenReturn(Optional.of(parent));
        lenient().when(taskRepository.findByParentSnapshotId(PARENT_ID)).thenReturn(Optional.empty());
        lenient().when(taskRepository.save(any(AiTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(intentCatalog.resolve("engineering-story-context-analysis", "v1")).thenReturn(intent());
    }

    @Test
    void authorizesBeforeMaterializingParentAndReusesProjectionAndAllowList() {
        var result = service.submit(PRINCIPAL, PARENT_ID, "Why is this boundary needed?",
                Map.of("audience", "maintainers"), null);

        assertEquals(PARENT_ID, result.parentSnapshotId());
        assertEquals(result.followUpId(), result.snapshotId());
        ArgumentCaptor<PromptRequest> prompt = ArgumentCaptor.forClass(PromptRequest.class);
        verify(aiEngineClient).submit(prompt.capture());
        assertEquals(parent.getSelectedKnowledgeSnapshot(), prompt.getValue().selectedKnowledge());
        assertEquals(parent.getContextDigest(), prompt.getValue().contextDigest());
        assertEquals(parent.getProjectionDigest(), prompt.getValue().projectionDigest());
        assertEquals("maintainers", prompt.getValue().userGuidance().audience());
        verify(taskRepository).findStoryContextSnapshotIdentity(PARENT_ID);
        verify(membershipRepository).existsByPrincipalIdAndProjectIdAndRoleIn(eq("human-1"), eq(PROJECT_ID), any());
        verify(taskRepository).findById(PARENT_ID);
        verify(aiTaskService).submit(any(), any());
    }

    @Test
    void unauthorizedParentIsRejectedBeforeDetailedLoadOrProvider() {
        when(membershipRepository.existsByPrincipalIdAndProjectIdAndRoleIn(eq("human-1"), eq(PROJECT_ID), any()))
                .thenReturn(false);

        assertThrows(EntityNotFoundException.class, () -> service.submit(
                PRINCIPAL, PARENT_ID, "Explain this", null, null));

        verify(taskRepository, never()).findById(PARENT_ID);
        verifyNoInteractions(aiEngineClient);
    }

    @Test
    void identicalRetryIsIdempotentAndDivergentPayloadConflicts() {
        var first = service.submit(PRINCIPAL, PARENT_ID, "Explain this", null, null);
        AiTask existing = AiTask.builder().id(first.followUpId())
                .parentSnapshotId(PARENT_ID)
                .followUpRequestDigest(digest("Explain this", null))
                .status(AiTaskStatus.SUBMITTED)
                .build();
        when(taskRepository.findByParentSnapshotId(PARENT_ID)).thenReturn(Optional.of(existing));

        var retry = service.submit(PRINCIPAL, PARENT_ID, "Explain this", null, null);
        assertEquals(first.followUpId(), retry.followUpId());
        verify(aiEngineClient, times(1)).submit(any(PromptRequest.class));

        assertThrows(ConflictException.class, () -> service.submit(
                PRINCIPAL, PARENT_ID, "A different question", null, null));
        verify(aiEngineClient, times(1)).submit(any(PromptRequest.class));
    }

    @Test
    void followUpCannotBeUsedAsParentAndQuestionIsBounded() {
        parent.setParentSnapshotId(UUID.randomUUID());
        assertThrows(EntityNotFoundException.class, () -> service.submit(
                PRINCIPAL, PARENT_ID, "Explain this", null, null));
        parent.setParentSnapshotId(null);

        assertThrows(IllegalArgumentException.class, () -> service.submit(
                PRINCIPAL, PARENT_ID, "x".repeat(StoryAgentFollowUpService.MAX_QUESTION_LENGTH + 1), null, null));
        verifyNoInteractions(aiEngineClient);
    }

    @Test
    void followUpAuthorizationUsesParentSnapshotTtl() {
        UUID followUpId = UUID.randomUUID();
        AiTask followUp = AiTask.builder()
                .id(followUpId)
                .parentSnapshotId(PARENT_ID)
                .taskType(AiTaskType.STORY_CONTEXT_ANALYSIS)
                .createdAt(Instant.parse("2026-10-01T00:00:00Z"))
                .build();
        when(taskRepository.findStoryContextSnapshotIdentity(followUpId))
                .thenReturn(Optional.of(new SnapshotIdentity(followUpId, PROJECT_ID,
                        AiTaskType.STORY_CONTEXT_ANALYSIS, followUp.getCreatedAt(), PARENT_ID)));
        when(taskRepository.findStoryContextSnapshotIdentity(PARENT_ID))
                .thenReturn(Optional.of(new SnapshotIdentity(PARENT_ID, PROJECT_ID,
                        AiTaskType.STORY_CONTEXT_ANALYSIS, Instant.parse("2026-09-01T00:00:00Z"))));
        assertThrows(EntityNotFoundException.class, () -> service.submit(
                PRINCIPAL, followUpId, "Explain this", null, null));
        verifyNoInteractions(aiEngineClient);
    }

    private AiTask parent() {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("projectSlug", "devlog-ai");
        scope.put("storyId", null);
        scope.put("intent", "engineering-story-context-analysis");
        scope.put("files", List.of());
        return AiTask.builder()
                .id(PARENT_ID)
                .analysis(Analysis.builder().id(UUID.randomUUID()).build())
                .taskType(AiTaskType.STORY_CONTEXT_ANALYSIS)
                .intentId("engineering-story-context-analysis")
                .intentVersion("v1")
                .status(AiTaskStatus.COMPLETED)
                .createdAt(Instant.parse("2026-09-15T00:00:00Z"))
                .contextDigest(CONTEXT_DIGEST)
                .projectionDigest(PROJECTION_DIGEST)
                .selectedKnowledgeSnapshot(Map.of("contractVersion", "story-context-agent-projection/v1",
                        "contextDigest", CONTEXT_DIGEST, "projectionDigest", PROJECTION_DIGEST))
                .contextSnapshot(Map.of("scope", scope,
                        "freshness", Map.of("state", "FRESH"), "groundingDigest", "c".repeat(64),
                        "groundingContract", Map.of("allowedGroundingReferences", List.of()),
                        "projectionVersion", "sca/v1"))
                .build();
    }

    private IntentDefinition intent() {
        return new IntentDefinition("engineering-story-context-analysis", "v1", "Analyze Story context",
                List.of(), List.of(), Map.of("type", "object"), "story-context-analysis-prompt-v1");
    }

    private String digest(String question, Map<String, Object> guidance) {
        return new StoryContextDigestService(new ObjectMapper()).sha256(
                new StoryContextDigestService(new ObjectMapper()).canonicalJson(Map.of(
                        "question", question, "guidance", guidance == null ? Map.of() : guidance)));
    }
}
