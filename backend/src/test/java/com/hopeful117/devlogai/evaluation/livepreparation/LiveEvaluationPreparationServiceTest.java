package com.hopeful117.devlogai.evaluation.livepreparation;

import com.hopeful117.devlogai.authorization.CoreProjectAuthorizationService;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
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
        when(projects.findBySlug("devlog-ai")).thenReturn(java.util.Optional.of(project));
        when(authorization.isAuthorized(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(project), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        when(preparations.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        LiveEvaluationPreparationResponse response = new LiveEvaluationPreparationService(
                projects, authorization, preparations, new ObjectMapper(), Clock.systemUTC()).prepare(
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
}
