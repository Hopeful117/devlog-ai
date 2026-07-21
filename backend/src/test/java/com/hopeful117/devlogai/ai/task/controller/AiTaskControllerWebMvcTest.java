package com.hopeful117.devlogai.ai.task.controller;

import com.hopeful117.devlogai.ai.task.dto.response.AiTaskResponse;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiTaskControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private AiTaskService service;
    private MockMvc mvc;
    private UUID id;
    private UUID analysisId;
    private UUID correlationId;
    private AiTaskResponse response;

    @BeforeEach
    void setUp() {
        service = mock(AiTaskService.class);
        mvc = mockMvc(new AiTaskController(service));
        id = UUID.randomUUID();
        analysisId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        response = new AiTaskResponse(id, analysisId, correlationId,
                AiTaskType.INSIGHT_GENERATION, AiTaskStatus.CREATED, null,
                null, 0, null, null, null, null, null, null);
    }

    @Test
    void shouldExposeEveryAiTaskLifecycleRoute() throws Exception {
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByCorrelationId(correlationId)).thenReturn(response);
        when(service.getByAnalysisId(analysisId)).thenReturn(List.of(response));
        when(service.submit(org.mockito.ArgumentMatchers.eq(id), any())).thenReturn(withStatus(AiTaskStatus.SUBMITTED));
        when(service.startProcessing(id)).thenReturn(withStatus(AiTaskStatus.PROCESSING));
        when(service.complete(id)).thenReturn(withStatus(AiTaskStatus.COMPLETED));
        when(service.fail(org.mockito.ArgumentMatchers.eq(id), any())).thenReturn(withStatus(AiTaskStatus.FAILED));

        mvc.perform(post("/api/v1/ai-tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"%s\",\"taskType\":\"INSIGHT_GENERATION\"}".formatted(analysisId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.taskType").value("INSIGHT_GENERATION"));
        mvc.perform(get("/api/v1/ai-tasks/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ai-tasks/correlation/{id}", correlationId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ai-tasks/analysis/{id}", analysisId)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/ai-tasks/{id}/submit", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalJobId\":\"job-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(post("/api/v1/ai-tasks/{id}/start", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PROCESSING"));
        mvc.perform(post("/api/v1/ai-tasks/{id}/complete", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        mvc.perform(post("/api/v1/ai-tasks/{id}/fail", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"failureCode\":\"ENGINE_ERROR\",\"failureMessage\":\"failed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
    }

    @Test
    void shouldRejectUnknownAiTaskTypeAsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/ai-tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"analysisId\":\"%s\",\"taskType\":\"ARCHITECTURE_REVIEW\"}".formatted(analysisId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private AiTaskResponse withStatus(AiTaskStatus status) {
        return new AiTaskResponse(id, analysisId, correlationId,
                AiTaskType.INSIGHT_GENERATION, status, null,
                "job-1", 1, null, null, null, null, null, null);
    }
}
