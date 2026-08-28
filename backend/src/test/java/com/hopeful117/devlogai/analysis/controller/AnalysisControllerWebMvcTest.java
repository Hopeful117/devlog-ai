package com.hopeful117.devlogai.analysis.controller;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.dto.request.CreateAnalysisRequest;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse;
import com.hopeful117.devlogai.analysis.evidence.dto.AiTaskSelectedEvidenceResponse.TaskIdentity;
import com.hopeful117.devlogai.analysis.evidence.service.AiTaskSelectedEvidenceService;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.hopeful117.devlogai.analysis.diagnostics.service.AnalysisDiagnosticsService;

class AnalysisControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllAnalysisRoutesAndTaskTypeContract() throws Exception {
        AnalysisService service = mock(AnalysisService.class);
        AnalysisWorkflowService workflow = mock(AnalysisWorkflowService.class);
        AnalysisDiagnosticsService diagnostics = mock(AnalysisDiagnosticsService.class);
        AiTaskSelectedEvidenceService selectedEvidence = mock(AiTaskSelectedEvidenceService.class);
        MockMvc mvc = mockMvc(new AnalysisController(
                service, workflow, diagnostics, selectedEvidence));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        AnalysisResponse response = new AnalysisResponse(id, projectId,
                AnalysisType.ARCHITECTURE_REVIEW, AnalysisStatus.PENDING, null, null, null, null);
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));
        when(service.getByProjectAndType(projectId, AnalysisType.ARCHITECTURE_REVIEW))
                .thenReturn(List.of(response));
        when(service.getByProjectAndStatus(projectId, AnalysisStatus.PENDING)).thenReturn(List.of(response));
        when(workflow.start(id))
                .thenReturn(new AnalysisWorkflowResult(id, AnalysisStatus.IN_PROGRESS, 2, 1,
                        taskId, AiTaskStatus.SUBMITTED, correlationId));
        when(diagnostics.getWarnings(id)).thenReturn(List.of());
        when(diagnostics.getContext(id)).thenReturn(java.util.Map.of("analysisId", id.toString()));
        when(selectedEvidence.getSelectedEvidence(id)).thenReturn(
                AiTaskSelectedEvidenceResponse.snapshotPending(id, projectId,
                        new TaskIdentity(taskId,
                                AiTaskType.INSIGHT_GENERATION,
                                AiTaskStatus.SUBMITTED,
                                Instant.parse("2026-08-27T10:00:00Z"))));

        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"projectId\":\"%s\",\"type\":\"ARCHITECTURE_REVIEW\"," +
                                "\"intentId\":\"architecture-overview-v1\"," +
                                "\"targetRevision\":\"release-1\"," +
                                "\"userGuidance\":{\"focus\":\"distributed architecture\"," +
                                "\"audience\":\"recruiters\",\"levelOfDetail\":\"concise\"," +
                                "\"writingStyle\":\"pedagogical\",\"outputContext\":\"portfolio\"," +
                                "\"priorities\":[\"Docker before Spring Boot\"]}}")
                                .formatted(projectId)))
                .andExpect(status().isCreated());
        ArgumentCaptor<CreateAnalysisRequest> request = ArgumentCaptor.forClass(CreateAnalysisRequest.class);
        verify(service).create(request.capture());
        org.junit.jupiter.api.Assertions.assertEquals("release-1", request.getValue().getTargetRevision());
        org.junit.jupiter.api.Assertions.assertEquals("architecture-overview-v1", request.getValue().getIntentId());
        org.junit.jupiter.api.Assertions.assertEquals("distributed architecture",
                request.getValue().getUserGuidance().focus());
        org.junit.jupiter.api.Assertions.assertEquals(List.of("Docker before Spring Boot"),
                request.getValue().getUserGuidance().priorities());
        mvc.perform(get("/api/v1/analyses/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analyses/project/{id}", projectId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analyses/project/{id}/type/ARCHITECTURE_REVIEW", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("ARCHITECTURE_REVIEW"));
        mvc.perform(get("/api/v1/analyses/project/{id}/status/PENDING", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(post("/api/v1/analyses/{id}/workflow", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiTaskStatus").value("SUBMITTED"));
        mvc.perform(get("/api/v1/analyses/{id}/diagnostics", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analyses/{id}/warnings", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analyses/{id}/context", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.analysisId").value(id.toString()));
        mvc.perform(get("/api/v1/analyses/{id}/selected-evidence", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("SNAPSHOT_PENDING"))
                .andExpect(jsonPath("$.analysisId").value(id.toString()))
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.task.id").value(taskId.toString()))
                .andExpect(jsonPath("$.task.taskType").value("INSIGHT_GENERATION"))
                .andExpect(jsonPath("$.task.status").value("SUBMITTED"));
        verify(selectedEvidence).getSelectedEvidence(id);
    }

    @Test
    void shouldSerializeDiagnosticsContextSnapshotsContainingNullValues() throws Exception {
        AnalysisService service = mock(AnalysisService.class);
        AnalysisWorkflowService workflow = mock(AnalysisWorkflowService.class);
        AnalysisDiagnosticsService diagnostics = mock(AnalysisDiagnosticsService.class);
        AiTaskSelectedEvidenceService selectedEvidence = mock(AiTaskSelectedEvidenceService.class);
        MockMvc mvc = mockMvc(new AnalysisController(
                service, workflow, diagnostics, selectedEvidence));
        UUID id = UUID.randomUUID();

        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("analysisId", id.toString());
        context.put("nullableField", null);
        context.put("nested", java.util.Map.of("kind", "context"));

        when(diagnostics.getContext(id)).thenReturn(context);

        mvc.perform(get("/api/v1/analyses/{id}/context", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(id.toString()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"nullableField\":null")))
                .andExpect(jsonPath("$.nested.kind").value("context"));
    }

    @Test
    void shouldUseExistingNotFoundEnvelopeForMissingSelectedEvidenceAnalysis() throws Exception {
        AnalysisService service = mock(AnalysisService.class);
        AnalysisWorkflowService workflow = mock(AnalysisWorkflowService.class);
        AnalysisDiagnosticsService diagnostics = mock(AnalysisDiagnosticsService.class);
        AiTaskSelectedEvidenceService selectedEvidence = mock(AiTaskSelectedEvidenceService.class);
        MockMvc mvc = mockMvc(new AnalysisController(
                service, workflow, diagnostics, selectedEvidence));
        UUID id = UUID.randomUUID();
        when(selectedEvidence.getSelectedEvidence(id))
                .thenThrow(new EntityNotFoundException("Analysis", id));

        mvc.perform(get("/api/v1/analyses/{id}/selected-evidence", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }
}
