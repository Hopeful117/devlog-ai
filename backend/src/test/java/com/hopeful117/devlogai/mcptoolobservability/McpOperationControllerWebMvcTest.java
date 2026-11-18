package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.authorization.*;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class McpOperationControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void byProjectReturnsPagedOperations() throws Exception {
        UUID projectId = UUID.randomUUID();
        McpOperationService service = mock(McpOperationService.class);
        McpOperationPageResponse response = new McpOperationPageResponse(
                List.of(new McpOperationView(
                        "inst-1", "op-1", "search_project_history",
                        "RESPONDED", 1,
                        Instant.parse("2026-01-01T12:00:00Z"),
                        Instant.parse("2026-01-01T12:00:01Z"),
                        1050, null, null, null, null)),
                1L, 0, 20);
        when(service.listByProject(eq(projectId), eq(0), eq(20))).thenReturn(response);
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(true);
        MockMvc mvc = mockMvc(new McpOperationController(
                r -> Optional.of(humanPrincipal()), authService, service, mock(ContextTraceService.class)));

        mvc.perform(get("/api/v1/projects/{id}/mcp-operations", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOperations").value(1))
                .andExpect(jsonPath("$.content[0].toolName").value("search_project_history"))
                .andExpect(jsonPath("$.content[0].status").value("RESPONDED"))
                .andExpect(jsonPath("$.content[0].durationMs").value(1050));
    }

    @Test
    void byProjectReturns401WhenNoPrincipal() throws Exception {
        MockMvc mvc = mockMvc(new McpOperationController(
                r -> Optional.empty(),
                mock(CoreProjectAuthorizationService.class),
                mock(McpOperationService.class),
                mock(ContextTraceService.class)));

        mvc.perform(get("/api/v1/projects/{id}/mcp-operations", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void byProjectReturns404WhenPrincipalNotAuthorized() throws Exception {
        UUID projectId = UUID.randomUUID();
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(false);
        MockMvc mvc = mockMvc(new McpOperationController(
                r -> Optional.of(humanPrincipal()), authService,
                mock(McpOperationService.class),
                mock(ContextTraceService.class)));

        mvc.perform(get("/api/v1/projects/{id}/mcp-operations", projectId))
                .andExpect(status().isNotFound());
    }

    @Test
    void contextTraceByAiTaskIdReturnsView() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ContextTraceService traceService = mock(ContextTraceService.class);
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(traceService.findProjectIdByAiTaskId(aiTaskId)).thenReturn(projectId);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(true);
        ContextTraceView view = new ContextTraceView(
                "inst-1", "op-1", "devlog_story_agent", "RESPONDED",
                Instant.parse("2026-01-01T12:00:00Z"), Instant.parse("2026-01-01T12:00:02Z"),
                2050, aiTaskId, "devlog-ai", null, "investigate story 42",
                List.of(), "ctx-digest", "proj-digest", "FRESH", null,
                List.of(), Map.of(), null, null, null, true);
        when(traceService.getContextTraceByAiTaskId(eq(projectId), eq(aiTaskId))).thenReturn(view);
        MockMvc mvc = mockMvc(new McpOperationController(
                r -> Optional.of(humanPrincipal()), authService,
                mock(McpOperationService.class), traceService));

        mvc.perform(get("/api/v1/context-traces/{id}", aiTaskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("devlog_story_agent"))
                .andExpect(jsonPath("$.status").value("RESPONDED"))
                .andExpect(jsonPath("$.durationMs").value(2050))
                .andExpect(jsonPath("$.projectSlug").value("devlog-ai"))
                .andExpect(jsonPath("$.contextAvailable").value(true));
    }

    @Test
    void contextTraceByAiTaskIdReturns401WhenNoPrincipal() throws Exception {
        MockMvc mvc = mockMvc(new McpOperationController(
                r -> Optional.empty(),
                mock(CoreProjectAuthorizationService.class),
                mock(McpOperationService.class),
                mock(ContextTraceService.class)));

        mvc.perform(get("/api/v1/context-traces/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void contextTraceByAiTaskIdReturns404WhenTaskNotFound() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        ContextTraceService traceService = mock(ContextTraceService.class);
        when(traceService.findProjectIdByAiTaskId(aiTaskId)).thenReturn(null);
        MockMvc mvc = mockMvc(new McpOperationController(
                r -> Optional.of(humanPrincipal()),
                mock(CoreProjectAuthorizationService.class),
                mock(McpOperationService.class), traceService));

        mvc.perform(get("/api/v1/context-traces/{id}", aiTaskId))
                .andExpect(status().isNotFound());
    }

    @Test
    void contextTraceByAiTaskIdReturns404WhenPrincipalNotAuthorizedForProject() throws Exception {
        UUID aiTaskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ContextTraceService traceService = mock(ContextTraceService.class);
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(traceService.findProjectIdByAiTaskId(aiTaskId)).thenReturn(projectId);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(false);
        MockMvc mvc = mockMvc(new McpOperationController(
                r -> Optional.of(humanPrincipal()), authService,
                mock(McpOperationService.class), traceService));

        mvc.perform(get("/api/v1/context-traces/{id}", aiTaskId))
                .andExpect(status().isNotFound());
    }

    private static AuthenticatedPrincipal humanPrincipal() {
        return new AuthenticatedPrincipal("ludo", PrincipalKind.HUMAN, "keycloak");
    }
}
