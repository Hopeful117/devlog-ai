package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.authorization.*;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpSessionLifecycleEventControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void ingestReturns200WhenEventTypeIsValid() throws Exception {
        McpSessionLifecycleEventService service = mock(McpSessionLifecycleEventService.class);
        MockMvc mvc = mockMvc(controller(service, r -> Optional.of(aiAgent()),
                (p, proj, scope) -> true, project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/session-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("devlog-ai", "SESSION_LIFECYCLE_ENDED")))
                .andExpect(status().isOk());

        verify(service).ingest(any(), any());
    }

    @Test
    void ingestReturns400WhenEventTypeIsUnknown() throws Exception {
        McpSessionLifecycleEventService service = mock(McpSessionLifecycleEventService.class);
        MockMvc mvc = mockMvc(controller(service, r -> Optional.of(aiAgent()),
                (p, proj, scope) -> true, project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/session-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("devlog-ai", "MCP_RUNTIME_STOPPED")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void ingestReturns400WhenEventTypeIsBlank() throws Exception {
        McpSessionLifecycleEventService service = mock(McpSessionLifecycleEventService.class);
        MockMvc mvc = mockMvc(controller(service, r -> Optional.of(aiAgent()),
                (p, proj, scope) -> true, project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/session-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("devlog-ai", "")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void ingestReturns401WhenNoPrincipal() throws Exception {
        MockMvc mvc = mockMvc(controller(mock(McpSessionLifecycleEventService.class),
                r -> Optional.empty(),
                (p, proj, scope) -> true, project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/session-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("devlog-ai", "SESSION_LIFECYCLE_ENDED")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestReturns401WhenPrincipalIsNotAiAgent() throws Exception {
        MockMvc mvc = mockMvc(controller(mock(McpSessionLifecycleEventService.class),
                r -> Optional.of(humanPrincipal()),
                (p, proj, scope) -> true, project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/session-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("devlog-ai", "SESSION_LIFECYCLE_ENDED")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestReturns404WhenProjectNotFound() throws Exception {
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        when(projectRepo.findBySlug(any())).thenReturn(Optional.empty());
        MockMvc mvc = mockMvc(new McpSessionLifecycleEventController(
                r -> Optional.of(aiAgent()),
                mock(CoreProjectAuthorizationService.class),
                projectRepo,
                mock(McpSessionLifecycleEventService.class)));

        mvc.perform(post("/api/v1/mcp/session-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("unknown", "SESSION_LIFECYCLE_ENDED")))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private static McpSessionLifecycleEventController controller(
            McpSessionLifecycleEventService service,
            AuthenticatedPrincipalResolver resolver,
            TriPredicate authorizer,
            Project project) {
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        lenient().when(authService.isAuthorized(any(AuthenticatedPrincipal.class), any(Project.class), any()))
                .thenAnswer(inv -> authorizer.test(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        lenient().when(projectRepo.findBySlug(any())).thenReturn(Optional.of(project));
        return new McpSessionLifecycleEventController(resolver, authService, projectRepo, service);
    }

    @FunctionalInterface
    private interface TriPredicate {
        boolean test(AuthenticatedPrincipal p, Object project, String scope);
    }

    private static AuthenticatedPrincipal aiAgent() {
        return new AuthenticatedPrincipal("opencode-agent", PrincipalKind.AI_AGENT, "keycloak");
    }

    private static AuthenticatedPrincipal humanPrincipal() {
        return new AuthenticatedPrincipal("ludo", PrincipalKind.HUMAN, "keycloak");
    }

    private static Project project(String slug) {
        return Project.builder()
                .id(UUID.randomUUID())
                .name(slug)
                .slug(slug)
                .status(ProjectStatus.ACTIVE)
                .build();
    }

    private static String validBody(String projectSlug, String eventType) {
        return """
                {
                  "projectSlug": "%s",
                  "agentSessionId": "cc7d159d-a138-4b73-b14e-1e7279d013ab",
                  "eventType": "%s",
                  "occurredAt": "2026-01-01T12:00:00Z"
                }
                """.formatted(projectSlug, eventType);
    }
}
