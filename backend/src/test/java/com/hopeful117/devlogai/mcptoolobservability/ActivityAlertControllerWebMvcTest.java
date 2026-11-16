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

class ActivityAlertControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void byProjectReturns200WithAlerts() throws Exception {
        UUID projectId = UUID.randomUUID();
        ActivityAlertService service = mock(ActivityAlertService.class);
        ActivityAlertPageResponse response = new ActivityAlertPageResponse(
                List.of(new ActivityAlertView(
                        "inst1/op1", "ERROR", "MCP_TOOL_FAILED",
                        "devlog_get_story_context", null, 4521,
                        Instant.parse("2026-11-16T11:42:03Z"),
                        "inst1", "op1", "session-aaa", "claude-code")),
                1L, 0, 20);
        when(service.listByProject(eq(projectId), eq(0), eq(20))).thenReturn(response);
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(AuthenticatedPrincipal.class), eq(projectId), any()))
                .thenReturn(true);
        MockMvc mvc = mockMvc(new ActivityAlertController(
                r -> Optional.of(humanPrincipal()), authService, service));

        mvc.perform(get("/api/v1/projects/{id}/activity-alerts", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAlerts").value(1))
                .andExpect(jsonPath("$.content[0].alertId").value("inst1/op1"))
                .andExpect(jsonPath("$.content[0].severity").value("ERROR"))
                .andExpect(jsonPath("$.content[0].toolName").value("devlog_get_story_context"));
    }

    @Test
    void byProjectReturns200WithEmptyContent() throws Exception {
        UUID projectId = UUID.randomUUID();
        ActivityAlertService service = mock(ActivityAlertService.class);
        when(service.listByProject(eq(projectId), anyInt(), anyInt()))
                .thenReturn(new ActivityAlertPageResponse(List.of(), 0L, 0, 20));
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(), eq(projectId), any())).thenReturn(true);
        MockMvc mvc = mockMvc(new ActivityAlertController(
                r -> Optional.of(humanPrincipal()), authService, service));

        mvc.perform(get("/api/v1/projects/{id}/activity-alerts", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAlerts").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void byProjectReturns401WhenNoPrincipal() throws Exception {
        MockMvc mvc = mockMvc(new ActivityAlertController(
                r -> Optional.empty(),
                mock(CoreProjectAuthorizationService.class),
                mock(ActivityAlertService.class)));

        mvc.perform(get("/api/v1/projects/{id}/activity-alerts", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void byProjectReturns404WhenNotAuthorized() throws Exception {
        UUID projectId = UUID.randomUUID();
        CoreProjectAuthorizationService authService = mock(CoreProjectAuthorizationService.class);
        when(authService.isAuthorized(any(), eq(projectId), any())).thenReturn(false);
        MockMvc mvc = mockMvc(new ActivityAlertController(
                r -> Optional.of(humanPrincipal()), authService,
                mock(ActivityAlertService.class)));

        mvc.perform(get("/api/v1/projects/{id}/activity-alerts", projectId))
                .andExpect(status().isNotFound());
    }

    private static AuthenticatedPrincipal humanPrincipal() {
        return new AuthenticatedPrincipal("ludo", PrincipalKind.HUMAN, "keycloak");
    }
}
