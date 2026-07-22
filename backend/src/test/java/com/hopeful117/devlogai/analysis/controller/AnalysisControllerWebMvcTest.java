package com.hopeful117.devlogai.analysis.controller;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.analysis.dto.response.AnalysisResponse;
import com.hopeful117.devlogai.analysis.dto.request.CreateAnalysisRequest;
import com.hopeful117.devlogai.analysis.entity.AnalysisStatus;
import com.hopeful117.devlogai.analysis.entity.AnalysisType;
import com.hopeful117.devlogai.analysis.service.AnalysisService;
import com.hopeful117.devlogai.analysis.workflow.AnalysisWorkflowService;
import com.hopeful117.devlogai.analysis.workflow.dto.AnalysisWorkflowResult;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllAnalysisRoutesAndTaskTypeContract() throws Exception {
        AnalysisService service = mock(AnalysisService.class);
        AnalysisWorkflowService workflow = mock(AnalysisWorkflowService.class);
        MockMvc mvc = mockMvc(new AnalysisController(service, workflow));
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
        when(workflow.start(id, com.hopeful117.devlogai.ai.task.entity.AiTaskType.INSIGHT_GENERATION))
                .thenReturn(new AnalysisWorkflowResult(id, AnalysisStatus.IN_PROGRESS, 2, 1,
                        taskId, AiTaskStatus.SUBMITTED, correlationId));

        mvc.perform(post("/api/v1/analyses").contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"projectId\":\"%s\",\"type\":\"ARCHITECTURE_REVIEW\"," +
                                "\"targetRevision\":\"release-1\"}").formatted(projectId)))
                .andExpect(status().isCreated());
        ArgumentCaptor<CreateAnalysisRequest> request = ArgumentCaptor.forClass(CreateAnalysisRequest.class);
        verify(service).create(request.capture());
        org.junit.jupiter.api.Assertions.assertEquals("release-1", request.getValue().getTargetRevision());
        mvc.perform(get("/api/v1/analyses/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analyses/project/{id}", projectId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/analyses/project/{id}/type/ARCHITECTURE_REVIEW", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("ARCHITECTURE_REVIEW"));
        mvc.perform(get("/api/v1/analyses/project/{id}/status/PENDING", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(post("/api/v1/analyses/{id}/workflow", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"INSIGHT_GENERATION\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.aiTaskStatus").value("SUBMITTED"));
    }
}
