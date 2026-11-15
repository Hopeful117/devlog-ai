package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.authorization.*;
import com.hopeful117.devlogai.project.entity.Project;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.repository.ProjectRepository;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpToolEventControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void ingestReturns202WhenAgentIsAuthorized() throws Exception {
        Project project = project("devlog-ai");
        McpToolEventService service = mock(McpToolEventService.class);
        MockMvc mvc = mockMvc(controller(service,
                r -> Optional.of(aiAgent()),
                (p, proj, scope) -> true,
                project));

        mvc.perform(post("/api/v1/mcp/tool-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validIngestBody("devlog-ai")))
                .andExpect(status().isAccepted());

        verify(service).ingest(eq(project), any());
    }

    @Test
    void ingestRejects401WhenNoPrincipal() throws Exception {
        MockMvc mvc = mockMvc(controller(mock(McpToolEventService.class),
                r -> Optional.empty(),
                (p, proj, scope) -> true,
                project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/tool-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validIngestBody("devlog-ai")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestRejects401WhenPrincipalIsNotAiAgent() throws Exception {
        MockMvc mvc = mockMvc(controller(mock(McpToolEventService.class),
                r -> Optional.of(humanPrincipal()),
                (p, proj, scope) -> true,
                project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/tool-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validIngestBody("devlog-ai")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestReturns404WhenProjectNotFound() throws Exception {
        McpToolEventService service = mock(McpToolEventService.class);
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        when(projectRepo.findBySlug(any())).thenReturn(Optional.empty());
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        MockMvc mvc = mockMvc(new McpToolEventController(
                r -> Optional.of(aiAgent()), authService, projectRepo, service));

        mvc.perform(post("/api/v1/mcp/tool-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validIngestBody("unknown-project")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    @Test
    void ingestReturns404WhenAgentNotAuthorizedForProject() throws Exception {
        McpToolEventService service = mock(McpToolEventService.class);
        MockMvc mvc = mockMvc(controller(service,
                r -> Optional.of(aiAgent()),
                (p, proj, scope) -> false,
                project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/tool-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validIngestBody("devlog-ai")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    @Test
    void ingestReturns400WhenBodyIsInvalid() throws Exception {
        MockMvc mvc = mockMvc(controller(mock(McpToolEventService.class),
                r -> Optional.of(aiAgent()),
                (p, proj, scope) -> true,
                project("devlog-ai")));

        mvc.perform(post("/api/v1/mcp/tool-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void byProjectReturnsPagedEvents() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        McpToolEventService service = mock(McpToolEventService.class);
        McpToolEventPageResponse pageResponse = new McpToolEventPageResponse(
                List.of(new McpToolEventResponse(
                        eventId, "inst-1", "op-1", "get_engineering_context",
                        "MCP_TOOL_RESPONDED", "RESPONDED", 1, 200, null,
                        Instant.parse("2026-01-01T12:00:00Z"))),
                1L, 0, 20);
        when(service.listByProject(eq(projectId), eq(0), eq(20))).thenReturn(pageResponse);
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(true);
        MockMvc mvc = mockMvc(new McpToolEventController(
                r -> Optional.of(humanPrincipal()), authService,
                mock(ProjectRepository.class), service));

        mvc.perform(get("/api/v1/projects/{id}/mcp-tool-events", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].toolName").value("get_engineering_context"))
                .andExpect(jsonPath("$.content[0].status").value("RESPONDED"));
    }

    @Test
    void byProjectReturns401WhenNoPrincipal() throws Exception {
        MockMvc mvc = mockMvc(new McpToolEventController(
                r -> Optional.empty(),
                mock(CoreProjectAuthorizationService.class),
                mock(ProjectRepository.class),
                mock(McpToolEventService.class)));

        mvc.perform(get("/api/v1/projects/{id}/mcp-tool-events", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void byProjectReturns404WhenPrincipalNotAuthorized() throws Exception {
        UUID projectId = UUID.randomUUID();
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(false);
        MockMvc mvc = mockMvc(new McpToolEventController(
                r -> Optional.of(humanPrincipal()), authService,
                mock(ProjectRepository.class), mock(McpToolEventService.class)));

        mvc.perform(get("/api/v1/projects/{id}/mcp-tool-events", projectId))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private static McpToolEventController controller(
            McpToolEventService service,
            AuthenticatedPrincipalResolver resolver,
            TriPredicate authorizer,
            Project project) {
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        lenient().when(authService.isAuthorized(any(AuthenticatedPrincipal.class), any(Project.class), any()))
                .thenAnswer(inv -> authorizer.test(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        lenient().when(authService.isAuthorized(any(AuthenticatedPrincipal.class), any(UUID.class), any()))
                .thenAnswer(inv -> authorizer.test(inv.getArgument(0), null, inv.getArgument(2)));
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        lenient().when(projectRepo.findBySlug(any())).thenReturn(Optional.of(project));
        return new McpToolEventController(resolver, authService, projectRepo, service);
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

    private static String validIngestBody(String projectSlug) {
        return """
                {
                  "projectSlug": "%s",
                  "instanceId": "inst-abc123",
                  "operationId": "op-xyz789",
                  "toolName": "get_engineering_context",
                  "eventType": "MCP_TOOL_RESPONDED",
                  "status": "RESPONDED",
                  "generation": 1,
                  "schemaVersion": "mcp-tool-observability-v1",
                  "durationMs": 150,
                  "occurredAt": "2026-01-01T12:00:00Z"
                }
                """.formatted(projectSlug);
    }
}
