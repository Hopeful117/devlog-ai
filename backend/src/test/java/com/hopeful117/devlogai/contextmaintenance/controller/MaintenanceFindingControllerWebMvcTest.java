package com.hopeful117.devlogai.contextmaintenance.controller;

import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceFindingResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceEvaluationResponse;
import com.hopeful117.devlogai.contextmaintenance.dto.request.MaintenanceFindingActionRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                List.of(),
                List.of(),
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

    @Test
    void shouldExposeAcknowledgementRoute() throws Exception {
        UUID findingId = UUID.randomUUID();
        MaintenanceFindingResponse acknowledged = new MaintenanceFindingResponse(
                response.id(), projectId, response.contextSurface(), response.issueType(),
                response.severity(), MaintenanceFindingStatus.ACKNOWLEDGED, response.suggestedAction(),
                response.humanReviewRequired(), response.summary(), response.details(), List.of(),
                List.of(),
                response.createdAt(), response.updatedAt()
        );
        when(service.acknowledge(eq(projectId), eq(findingId), any(MaintenanceFindingActionRequest.class)))
                .thenReturn(acknowledged);

        mvc.perform(post("/api/v1/projects/{projectId}/maintenance-findings/{findingId}/acknowledgements",
                        projectId, findingId)
                        .contentType("application/json")
                        .content("""
                                {"actedBy":"00000000-0000-0000-0000-000000000123","comment":"Reviewed and acknowledged"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void shouldExposeDismissalRoute() throws Exception {
        UUID findingId = UUID.randomUUID();
        MaintenanceFindingResponse dismissed = new MaintenanceFindingResponse(
                response.id(), projectId, response.contextSurface(), response.issueType(),
                response.severity(), MaintenanceFindingStatus.DISMISSED, response.suggestedAction(),
                response.humanReviewRequired(), response.summary(), response.details(), List.of(),
                List.of(),
                response.createdAt(), response.updatedAt()
        );
        when(service.dismiss(eq(projectId), eq(findingId), any(MaintenanceFindingActionRequest.class)))
                .thenReturn(dismissed);

        mvc.perform(post("/api/v1/projects/{projectId}/maintenance-findings/{findingId}/dismissals",
                        projectId, findingId)
                        .contentType("application/json")
                        .content("""
                                {"actedBy":"00000000-0000-0000-0000-000000000123","comment":"False positive after review"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void shouldExposeResolutionRoute() throws Exception {
        UUID findingId = UUID.randomUUID();
        MaintenanceFindingResponse resolved = new MaintenanceFindingResponse(
                response.id(), projectId, response.contextSurface(), response.issueType(),
                response.severity(), MaintenanceFindingStatus.RESOLVED, response.suggestedAction(),
                response.humanReviewRequired(), response.summary(), response.details(), List.of(),
                List.of(),
                response.createdAt(), response.updatedAt()
        );
        when(service.resolve(eq(projectId), eq(findingId), any(MaintenanceFindingActionRequest.class)))
                .thenReturn(resolved);

        mvc.perform(post("/api/v1/projects/{projectId}/maintenance-findings/{findingId}/resolutions",
                        projectId, findingId)
                        .contentType("application/json")
                        .content("""
                                {"actedBy":"00000000-0000-0000-0000-000000000123","comment":"Resolved through external cleanup"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
