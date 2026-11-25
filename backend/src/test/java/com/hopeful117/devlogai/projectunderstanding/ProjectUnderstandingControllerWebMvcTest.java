package com.hopeful117.devlogai.projectunderstanding;

import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingOutcome;
import com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingResponse;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectUnderstandingControllerWebMvcTest extends ControllerWebMvcTestSupport {
    private static final com.hopeful117.devlogai.authorization.AuthenticatedPrincipal PRINCIPAL =
            new com.hopeful117.devlogai.authorization.AuthenticatedPrincipal(
                    "agent", com.hopeful117.devlogai.authorization.PrincipalKind.AI_AGENT, "test");
    private static final com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver PRINCIPALS =
            request -> java.util.Optional.of(PRINCIPAL);

    @Test
    void exposesTheTypedExecutionContract() throws Exception {
        ProjectUnderstandingService service = mock(ProjectUnderstandingService.class);
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        when(service.execute(eq(projectId), any(), any())).thenReturn(new ProjectUnderstandingResponse(
                analysisId, AnalysisStatus.IN_PROGRESS, sourceId, "main", "describe-project", "v1",
                ProjectUnderstandingOutcome.CREATED, Map.of("id", sourceId.toString())));

        mockMvc(new ProjectUnderstandingController(service, PRINCIPALS)).perform(
                        post("/api/v1/projects/{id}/understanding-executions", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"sourceId":"%s","targetRevision":"main"}
                                        """.formatted(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId.toString()))
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andExpect(jsonPath("$.intentId").value("describe-project"));

        var captor = ArgumentCaptor.forClass(
                com.hopeful117.devlogai.projectunderstanding.dto.ProjectUnderstandingRequest.class);
        verify(service).execute(eq(projectId), captor.capture(), eq(PRINCIPAL));
        assertThat(captor.getValue().sourceId()).isEqualTo(sourceId);
    }

    @Test
    void rejectsMissingSourceAndOversizedRevision() throws Exception {
        ProjectUnderstandingService service = mock(ProjectUnderstandingService.class);
        UUID projectId = UUID.randomUUID();
        mockMvc(new ProjectUnderstandingController(service, PRINCIPALS)).perform(
                        post("/api/v1/projects/{id}/understanding-executions", projectId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"targetRevision\":\"%s\"}".formatted("x".repeat(256))))
                .andExpect(status().isBadRequest());
    }
}
