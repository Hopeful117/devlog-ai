package com.hopeful117.devlogai.mcptoolobservability;

import com.hopeful117.devlogai.authorization.*;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActivityIncidentControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void byProjectReturns200WithIncidents() throws Exception {
        UUID projectId = UUID.randomUUID();
        ActivityIncidentService service = mock(ActivityIncidentService.class);
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        ActivityIncidentPageResponse response = new ActivityIncidentPageResponse(
                List.of(new ActivityIncidentView(
                        "abc123def456ab12", "ERROR",
                        "get_engineering_context", "MCP_PROCESS_FAILED",
                        7, now.minusSeconds(300), now,
                        List.of("inst1/op1", "inst2/op2"))),
                1L, 1L, 0L, 60, now.minusSeconds(3600), 0, 20);
        when(service.listByProject(eq(projectId), eq(60), eq(0), eq(20))).thenReturn(response);
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(true);
        MockMvc mvc = mockMvc(new ActivityIncidentController(
                r -> Optional.of(humanPrincipal()), authService, service));

        mvc.perform(get("/api/v1/projects/{id}/activity-incidents", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(1))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.windowMinutes").value(60))
                .andExpect(jsonPath("$.content[0].incidentId").value("abc123def456ab12"))
                .andExpect(jsonPath("$.content[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.content[0].toolName").value("get_engineering_context"))
                .andExpect(jsonPath("$.content[0].occurrenceCount").value(7));
    }

    @Test
    void byProjectReturns200WithEmptyContent() throws Exception {
        UUID projectId = UUID.randomUUID();
        ActivityIncidentService service = mock(ActivityIncidentService.class);
        Instant now = Instant.now();
        when(service.listByProject(eq(projectId), anyInt(), anyInt(), anyInt()))
                .thenReturn(new ActivityIncidentPageResponse(
                        List.of(), 0L, 0L, 0L, 60, now, 0, 20));
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(), eq(projectId), any())).thenReturn(true);
        MockMvc mvc = mockMvc(new ActivityIncidentController(
                r -> Optional.of(humanPrincipal()), authService, service));

        mvc.perform(get("/api/v1/projects/{id}/activity-incidents", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncidents").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void byProjectReturns401WhenNoPrincipal() throws Exception {
        MockMvc mvc = mockMvc(new ActivityIncidentController(
                r -> Optional.empty(),
                mock(CoreProjectAuthorizationService.class),
                mock(ActivityIncidentService.class)));

        mvc.perform(get("/api/v1/projects/{id}/activity-incidents", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void byProjectReturns404WhenNotAuthorized() throws Exception {
        UUID projectId = UUID.randomUUID();
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(), eq(projectId), any())).thenReturn(false);
        MockMvc mvc = mockMvc(new ActivityIncidentController(
                r -> Optional.of(humanPrincipal()), authService,
                mock(ActivityIncidentService.class)));

        mvc.perform(get("/api/v1/projects/{id}/activity-incidents", projectId))
                .andExpect(status().isNotFound());
    }

    @Test
    void byProjectPassesWindowMinutesToService() throws Exception {
        UUID projectId = UUID.randomUUID();
        ActivityIncidentService service = mock(ActivityIncidentService.class);
        Instant now = Instant.now();
        when(service.listByProject(eq(projectId), eq(30), eq(0), eq(20)))
                .thenReturn(new ActivityIncidentPageResponse(
                        List.of(), 0L, 0L, 0L, 30, now, 0, 20));
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(), eq(projectId), any())).thenReturn(true);
        MockMvc mvc = mockMvc(new ActivityIncidentController(
                r -> Optional.of(humanPrincipal()), authService, service));

        mvc.perform(get("/api/v1/projects/{id}/activity-incidents?windowMinutes=30", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowMinutes").value(30));
    }

    private static AuthenticatedPrincipal humanPrincipal() {
        return new AuthenticatedPrincipal("ludo", PrincipalKind.HUMAN, "keycloak");
    }
}
