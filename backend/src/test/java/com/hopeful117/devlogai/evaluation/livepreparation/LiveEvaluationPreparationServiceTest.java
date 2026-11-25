package com.hopeful117.devlogai.evaluation.livepreparation;

import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.collection.workspace.WorkspaceManager;
import com.hopeful117.devlogai.collection.workspace.SynchronizedWorkspace;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.source.entity.Source;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.repository.SourceRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LiveEvaluationPreparationServiceTest {
    @Test
    void reportsNotAuthorizedWithoutPersistingOrInspectingRequest() {
        LiveEvaluationPreparationService service = new LiveEvaluationPreparationService(
                mock(ProjectRepository.class),
                mock(CoreProjectAuthorizationService.class),
                mock(LiveEvaluationPreparationRepository.class),
                mock(SourceRepository.class),
                mock(WorkspaceManager.class),
                new ObjectMapper(),
                Clock.systemUTC());

        LiveEvaluationPreparationResponse response = service.prepare(
                "devlog-ai", "key", null,
                new LiveEvaluationPreparationRequest(
                        "q1", "1", "Q1", "manifest", "1", "digest", "ACCEPTED", "oracle",
                        "source", "runtime", Map.of(), Map.of(), Map.of(), "provider", "model",
                        Map.of(), "q1/run", java.util.List.of("observation")));

        assertEquals(LiveEvaluationPreparationStatus.NOT_AUTHORIZED, response.status());
        assertEquals(0, response.plan().get("providerCalls"));
        assertEquals(0, response.plan().get("networkCalls"));
    }

    @Test
    void persistsOnlyTheBoundedProjectionAndBlocksUnverifiedRuntime() {
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        ProjectRepository projects = mock(ProjectRepository.class);
        CoreProjectAuthorizationService authorization = mock(CoreProjectAuthorizationService.class);
        LiveEvaluationPreparationRepository preparations = mock(LiveEvaluationPreparationRepository.class);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        when(projects.findBySlug("devlog-ai")).thenReturn(java.util.Optional.of(project));
        when(authorization.isAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(project), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        when(preparations.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        LiveEvaluationPreparationResponse response = new LiveEvaluationPreparationService(
                projects, authorization, preparations, sources, workspaces, new ObjectMapper(), Clock.systemUTC()).prepare(
                "devlog-ai", "key", new AuthenticatedPrincipal("principal", PrincipalKind.HUMAN, "test"),
                new LiveEvaluationPreparationRequest(
                        "devlog-ai-abc", "1.0.0", "Q1", "devlog-ai-abc-first-test", "1.1.0",
                        "0".repeat(64), "ACCEPTED", "0".repeat(64), "0".repeat(40), "1".repeat(40),
                        Map.of("runtimeRevision", "1".repeat(40), "reproducible", true, "coreVerified", true, "prompt", "secret"),
                        Map.of("A", Map.of("runtime", "attacker")), Map.of("maxProviderCalls", 1),
                        "attacker-provider", "attacker-model", Map.of("token", "secret"), "q1/run", List.of("observation")));

        assertEquals(LiveEvaluationPreparationStatus.BLOCKED, response.status());
        assertFalse(response.plan().containsKey("prompt"));
        assertFalse(response.plan().containsKey("token"));
        assertEquals("openai_chatgpt", response.plan().get("provider"));
        assertNull(response.plan().get("credentials"));
    }

    @Test
    void acceptsRuntimeWhenCoreResolvesTheClaimedRevisionFromTheActiveSource() {
        Project project = Project.builder().id(UUID.randomUUID()).slug("devlog-ai").build();
        ProjectRepository projects = mock(ProjectRepository.class);
        CoreProjectAuthorizationService authorization = mock(CoreProjectAuthorizationService.class);
        LiveEvaluationPreparationRepository preparations = mock(LiveEvaluationPreparationRepository.class);
        SourceRepository sources = mock(SourceRepository.class);
        WorkspaceManager workspaces = mock(WorkspaceManager.class);
        Source source = Source.builder().id(UUID.randomUUID()).project(project)
                .type(SourceType.GIT_REPOSITORY).active(true).build();
        String runtimeRevision = "1".repeat(40);

        when(projects.findBySlug("devlog-ai")).thenReturn(java.util.Optional.of(project));
        when(authorization.isAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(project), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        when(sources.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId()))
                .thenReturn(List.of(source));
        when(workspaces.synchronize(source, runtimeRevision))
                .thenReturn(new SynchronizedWorkspace(source.getId(), Path.of("/tmp/source"), runtimeRevision));
        when(preparations.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        LiveEvaluationPreparationResponse response = new LiveEvaluationPreparationService(
                projects, authorization, preparations, sources, workspaces, new ObjectMapper(), Clock.systemUTC()).prepare(
                "devlog-ai", "key", new AuthenticatedPrincipal("principal", PrincipalKind.HUMAN, "test"),
                new LiveEvaluationPreparationRequest(
                        "devlog-ai-abc", "1.0.0", "Q1", "devlog-ai-abc-first-test", "1.1.0",
                        "eaca1774647e4d9e15f0f6b77a98aa733a787895f0cde1f282933de3405dc598",
                        "ACCEPTED", "2127d4ce224b1f39f5d8f113bb0b24b3dab7e9443fcb101600a774341c8d6d4e",
                        "0673d1d32433cdaff054d5f1defd1bedd01c0039", runtimeRevision,
                        Map.of("runtimeRevision", runtimeRevision, "coreVerified", false),
                        Map.of("A", Map.of("runtime", "java-core-story-context-one-shot", "entrypoint",
                                        "backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java",
                                        "promptVersion", "story-context-agent-protocol/v1", "answerContract", "story-context-analysis-result-v1"),
                                "B", Map.of("runtime", "comparative-v4-direct-investigator", "entrypoint",
                                        "ai-engine/evaluations/comparative_baseline_v4/runtime.py", "configuration",
                                        "docs/evaluation/devlog-ai-q1-b-direct-config-v1.json", "promptVersion",
                                        "comparative-v4-common-answer-1.0.0", "answerContract", "comparative-v4-common-answer-1.0.0"),
                                "C", Map.of("runtime", "python-core-bounded-investigation", "entrypoint",
                                        "ai-engine/app/services/story_context_analysis_generation_service.py", "promptVersion",
                                        "story-context-agent-protocol/v2", "answerContract", "story-context-analysis-result-v1")),
                        Map.of("maxModelTurns", 8, "maxProviderCalls", 8, "maxToolOrActionOperations", 8,
                                "maxReadBytesPerOperation", 64000, "maxReadTokensPerOperation", 8000,
                                "maxWallClockSeconds", 120, "maxOutputTokensPerResponse", 32768),
                        "openai_chatgpt", "gpt-5.6-luna",
                        Map.of("status", "APPROVED", "version", "q1-chatgpt-oauth-pricing-v1",
                                "provider", "openai_chatgpt", "authentication", "CHATGPT_OAUTH",
                                "currency", "NOT_APPLICABLE", "billingModel", "CHATGPT_OAUTH_SUBSCRIPTION_NOT_METERED",
                                "costCeilingPolicy", "OPERATIONAL_LIMITS_ONLY",
                                "digest", "0b48079816061d0660476bf2bb5d08572ff5f3378be7d5f3ba77d4013c229fbd"),
                        "q1/run", List.of("Q1-A-DEVLOG-CURRENT-r1", "Q1-B-DIRECT-INVESTIGATOR-r1", "Q1-C-DEVLOG-ENGINEERING-ANALYST-r1")));

         assertEquals(LiveEvaluationPreparationStatus.READY, response.status());
         assertEquals(true, ((Map<?, ?>) response.plan().get("runtimeAttestation")).get("coreVerified"));
         assertEquals("devlog-ai-abc-first-test", response.plan().get("campaignId"));
         assertEquals(List.of("A_DEVLOG_CURRENT", "B_DIRECT_INVESTIGATOR", "C_DEVLOG_ENGINEERING_ANALYST"),
                 response.plan().get("authorizedConditions"));
         assertEquals(response.plan().get("operationalLimits"), response.plan().get("budgets"));
         assertEquals(response.plan().get("operationalLimits"), response.plan().get("limits"));
         assertEquals("APPROVED", response.plan().get("pricingStatus"));
         assertEquals("q1-chatgpt-oauth-pricing-v1", response.plan().get("pricingVersion"));
         assertEquals("0b48079816061d0660476bf2bb5d08572ff5f3378be7d5f3ba77d4013c229fbd",
                 response.plan().get("pricingDigest"));
         assertEquals(Map.of("currency", "NOT_APPLICABLE", "maxAmount", "NOT_MEASURED"),
                 response.plan().get("costCeiling"));
     }
}
