package com.hopeful117.devlogai.contextmaintenance.controller;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceContextSurface;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingIssueType;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingSeverity;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceFindingStatus;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceSuggestedActionCategory;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceEvaluationService;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceFindingService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceFindingControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private MaintenanceFindingService service;
    private MaintenanceEvaluationService evaluationService;
    private MockMvc mvc;
    private UUID projectId;
    private MaintenanceFindingResponse response;

    @BeforeEach
    void setUp() {
        service = mock(MaintenanceFindingService.class);
        evaluationService = mock(MaintenanceEvaluationService.class);
        mvc = mockMvc(new MaintenanceFindingController(service, evaluationService));
        projectId = UUID.randomUUID();
        response = new MaintenanceFindingResponse(
                UUID.randomUUID(), projectId,
                MaintenanceContextSurface.PROJECT_PROJECTION,
                MaintenanceFindingIssueType.PROJECTION_REFRESH_GAP,
                MaintenanceFindingSeverity.HIGH,
                MaintenanceFindingStatus.OPEN,
                MaintenanceSuggestedActionCategory.REVIEW,
                true,
                "Projection freshness is lagging behind repository changes.",
                "A manual review is required before relying on the current projection.",
                Instant.parse("2026-08-14T10:00:00Z"),
                Instant.parse("2026-08-14T10:05:00Z")
        );
    }

    @Test
    void shouldExposeProjectMaintenanceFindingRoute() throws Exception {
        when(service.getByProject(projectId)).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/projects/{projectId}/maintenance-findings", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].contextSurface").value("PROJECT_PROJECTION"))
                .andExpect(jsonPath("$[0].severity").value("HIGH"))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].suggestedAction").value("REVIEW"))
                .andExpect(jsonPath("$[0].humanReviewRequired").value(true));

        verify(service).getByProject(projectId);
    }

    @Test
    void shouldReturnEmptyListWhenProjectHasNoFindings() throws Exception {
        when(service.getByProject(projectId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/projects/{projectId}/maintenance-findings", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldPreserveProjectNotFoundContract() throws Exception {
        when(service.getByProject(projectId))
                .thenThrow(new EntityNotFoundException("Project", projectId));

        mvc.perform(get("/api/v1/projects/{projectId}/maintenance-findings", projectId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldExposeMaintenanceEvaluationRoute() throws Exception {
        MaintenanceEvaluationResponse evaluation = new MaintenanceEvaluationResponse(
                MaintenanceEvaluationResponse.PROJECTION_VERSION,
                projectId,
                1,
                0,
                List.of(response)
        );
        when(evaluationService.evaluate(projectId)).thenReturn(evaluation);

        mvc.perform(post("/api/v1/projects/{projectId}/maintenance-findings/evaluations", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("maintenance-evaluation-v1"))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.createdCount").value(1))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.createdFindings[0].issueType").value("PROJECTION_REFRESH_GAP"));

        verify(evaluationService).evaluate(projectId);
    }
}
