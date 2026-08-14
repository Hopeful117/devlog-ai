package com.hopeful117.devlogai.contextmaintenance.controller;

import com.hopeful117.devlogai.contextmaintenance.dto.request.CreateMaintenanceAssessmentRequest;
import com.hopeful117.devlogai.contextmaintenance.dto.response.MaintenanceAssessmentResponse;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentConfidenceLevel;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentRecommendedAction;
import com.hopeful117.devlogai.contextmaintenance.entity.MaintenanceAssessmentSemanticClassification;
import com.hopeful117.devlogai.contextmaintenance.service.MaintenanceAssessmentService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaintenanceAssessmentControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private MaintenanceAssessmentService service;
    private MockMvc mvc;
    private UUID projectId;
    private MaintenanceAssessmentResponse response;

    @BeforeEach
    void setUp() {
        service = mock(MaintenanceAssessmentService.class);
        mvc = mockMvc(new MaintenanceAssessmentController(service));
        projectId = UUID.randomUUID();
        response = new MaintenanceAssessmentResponse(
                UUID.randomUUID(),
                projectId,
                UUID.randomUUID(),
                MaintenanceAssessmentConfidenceLevel.HIGH,
                MaintenanceAssessmentSemanticClassification.LIKELY_DUPLICATE,
                MaintenanceAssessmentRecommendedAction.ESCALATE,
                "The finding appears to be a genuine duplicate.",
                null,
                Instant.parse("2026-08-14T10:00:00Z"),
                Instant.parse("2026-08-14T10:05:00Z")
        );
    }

    @Test
    void shouldExposeProjectMaintenanceAssessmentRoute() throws Exception {
        when(service.getByProject(projectId)).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/projects/{projectId}/maintenance-assessments", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$[0].confidenceLevel").value("HIGH"))
                .andExpect(jsonPath("$[0].semanticClassification").value("LIKELY_DUPLICATE"))
                .andExpect(jsonPath("$[0].recommendedAction").value("ESCALATE"))
                .andExpect(jsonPath("$[0].rationale").value("The finding appears to be a genuine duplicate."));

        verify(service).getByProject(projectId);
    }

    @Test
    void shouldReturnEmptyListWhenProjectHasNoAssessments() throws Exception {
        when(service.getByProject(projectId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/projects/{projectId}/maintenance-assessments", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void shouldExposeFindingScopedAssessmentRoute() throws Exception {
        UUID findingId = UUID.randomUUID();
        when(service.getByFinding(projectId, findingId)).thenReturn(List.of(response));

        mvc.perform(get("/api/v1/projects/{projectId}/maintenance-assessments/findings/{findingId}",
                        projectId, findingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()));

        verify(service).getByFinding(projectId, findingId);
    }

    @Test
    void shouldExposeCreateAssessmentRoute() throws Exception {
        UUID findingId = UUID.randomUUID();
        when(service.create(eq(projectId), any(CreateMaintenanceAssessmentRequest.class)))
                .thenReturn(response);

        mvc.perform(post("/api/v1/projects/{projectId}/maintenance-assessments", projectId)
                        .contentType("application/json")
                        .content("""
                                {
                                    "findingId": "%s",
                                    "confidenceLevel": "HIGH",
                                    "semanticClassification": "LIKELY_DUPLICATE",
                                    "recommendedAction": "ESCALATE",
                                    "rationale": "The finding appears to be a genuine duplicate."
                                }
                                """.formatted(findingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confidenceLevel").value("HIGH"))
                .andExpect(jsonPath("$.semanticClassification").value("LIKELY_DUPLICATE"));

        verify(service).create(eq(projectId), any(CreateMaintenanceAssessmentRequest.class));
    }

    @Test
    void shouldReturnValidationErrorWhenRequiredFieldsAreMissing() throws Exception {
        mvc.perform(post("/api/v1/projects/{projectId}/maintenance-assessments", projectId)
                        .contentType("application/json")
                        .content("""
                                {
                                    "findingId": null,
                                    "rationale": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
