package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.ai.engine.client.AIEngineClient;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.task.entity.AiTask;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.repository.AiTaskRepository;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.aicredentialbinding.entity.AiCredentialBinding;
import com.hopeful117.devlogai.aicredentialbinding.service.AiEngineBindingClient;
import com.hopeful117.devlogai.aicredentialbinding.service.AiTaskExecutionAuthorizationService;
import com.hopeful117.devlogai.analysis.entity.Analysis;
import com.hopeful117.devlogai.analysis.repository.AnalysisRepository;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.contracts.engineeringcontext.EngineeringContext;
import com.hopeful117.devlogai.engineeringcontext.CanonicalEngineeringContext;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import com.hopeful117.devlogai.intent.service.IntentCatalog;
import com.hopeful117.devlogai.project.entity.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StoryContextSubmissionServiceTest {
    @Mock IntentCatalog intentCatalog;
    @Mock AiTaskService aiTaskService;
    @Mock AIEngineClient aiEngineClient;
    @Mock AiTaskRepository aiTaskRepository;
    @Mock AnalysisRepository analysisRepository;
    @Mock AiEngineBindingClient aiEngineBindingClient;
    @Mock AiTaskExecutionAuthorizationService authorizationService;

    @Test
    void authenticatedSubmissionPersistsOnlyAuthorizedNonSecretParameters() {
        ObjectMapper objectMapper = new ObjectMapper();
        StoryContextSubmissionService service = new StoryContextSubmissionService(
                intentCatalog, aiTaskService, aiEngineClient, aiTaskRepository, analysisRepository,
                objectMapper, new StoryContextDigestService(objectMapper), null, aiEngineBindingClient,
                authorizationService);
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID bindingId = UUID.randomUUID();
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(projectId);
        Map<String, Object> projection = Map.of(
                "projectionVersion", "sca/v1",
                "contractVersion", "story-context-agent-projection/v1",
                "freshness", Map.of("sourceRevision", Map.of("revision", "revision-1")),
                "requestEcho", Map.of("intent", "engineering-story-context-analysis"));
        CanonicalEngineeringContext canonical = new CanonicalEngineeringContext(
                mock(EngineeringContext.class), null, "a".repeat(64), "engineering-context-v2",
                null, Map.of("sourceRevision", Map.of("revision", "revision-1")), Map.of(),
                null, Map.of(), Map.of(), Map.of(), List.of());
        PreparedStoryContext prepared = new PreparedStoryContext(
                "devlog-ai", null, "engineering-story-context-analysis", List.of(), project, null,
                canonical, projection, "b".repeat(64), null);
        IntentDefinition intent = new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze Story context", List.of(),
                List.of(), Map.of("type", "object"), "story-context-analysis-prompt-v1");
        AiTask task = AiTask.builder().id(taskId).correlationId(UUID.randomUUID())
                .status(AiTaskStatus.CREATED).contextSnapshot(Map.of()).build();
        when(intentCatalog.resolve("engineering-story-context-analysis", "v1")).thenReturn(intent);
        when(analysisRepository.save(any(Analysis.class))).thenAnswer(invocation -> {
            Analysis analysis = invocation.getArgument(0);
            analysis.setId(UUID.randomUUID());
            return analysis;
        });
        when(aiTaskService.createForStoryContextAnalysisEntity(any(), eq(AiTaskType.STORY_CONTEXT_ANALYSIS),
                any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(task);
        when(aiTaskRepository.save(any(AiTask.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authorizationService.authorizeAndIssue(any(), eq(task))).thenReturn(
                new AiTaskExecutionAuthorizationService.AuthorizedExecution(
                        AiCredentialBinding.builder().id(bindingId).build(), "anthropic", "claude-test",
                        Map.of("maxOutputTokens", 4096)));

        service.submit(prepared, null, "submission", "idempotency",
                new AuthenticatedPrincipal("human-1", PrincipalKind.HUMAN, "test"), "anthropic");

        assertEquals(Map.of("maxOutputTokens", 4096), task.getContextSnapshot().get("allowedParameters"));
        assertFalse(task.getContextSnapshot().containsKey("credentialHandle"));
        assertFalse(task.getContextSnapshot().containsKey("accessToken"));
        ArgumentCaptor<PromptRequest> prompt = ArgumentCaptor.forClass(PromptRequest.class);
        verify(aiEngineClient).submit(prompt.capture());
        assertEquals(bindingId, prompt.getValue().credentialBindingId());
        assertEquals("anthropic", prompt.getValue().provider());
        assertEquals("claude-test", prompt.getValue().modelIdentifier());
        assertTrue(prompt.getValue().allowedParameters().containsKey("maxOutputTokens"));
    }
}
