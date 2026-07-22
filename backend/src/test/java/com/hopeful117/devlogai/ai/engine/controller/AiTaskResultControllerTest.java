package com.hopeful117.devlogai.ai.engine.controller;

import com.hopeful117.devlogai.ai.engine.dto.AiTaskResultAcknowledgement;
import com.hopeful117.devlogai.ai.engine.service.AiTaskResultService;
import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.shared.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiTaskResultControllerTest {

    private AiTaskResultService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(AiTaskResultService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AiTaskResultController(service)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void shouldAcknowledgeCompletedResultContract() throws Exception {
        UUID correlationId = UUID.randomUUID();
        when(service.handle(eq(correlationId), any())).thenReturn(
                new AiTaskResultAcknowledgement(
                        correlationId,
                        true,
                        false,
                        AiTaskStatus.COMPLETED,
                        1
                )
        );

        mockMvc.perform(post(
                        "/api/v1/ai/tasks/{correlationId}/result",
                        correlationId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "%s",
                                  "externalJobId": "job-42",
                                  "status": "COMPLETED",
                                  "completedAt": "2026-07-21T20:00:00Z",
                                  "proposals": [{
                                    "type": "INSIGHT",
                                    "payload": {"summary": "Modular architecture"},
                                    "confidence": 0.85,
                                    "supportingFactIds": [],
                                    "supportingObservationIds": [],
                                    "evidenceReferences": ["src/app.py:10"]
                                  }],
                                  "error": null
                                }
                                """.formatted(correlationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId")
                        .value(correlationId.toString()))
                .andExpect(jsonPath("$.acknowledged").value(true))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.taskStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.proposalCount").value(1));
    }

    @Test
    void shouldRejectIncompleteCallbackPayload() throws Exception {
        UUID correlationId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/v1/ai/tasks/{correlationId}/result",
                        correlationId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldExposeStructuredNotReadyConflict() throws Exception {
        UUID correlationId = UUID.randomUUID();
        when(service.handle(eq(correlationId), any())).thenThrow(
                new AiTaskResultConflictException(
                        "AI_TASK_NOT_READY",
                        AiTaskStatus.CREATED,
                        "AI task cannot receive a result yet."
                )
        );

        mockMvc.perform(post(
                        "/api/v1/ai/tasks/{correlationId}/result",
                        correlationId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "%s",
                                  "externalJobId": "job-42",
                                  "status": "COMPLETED",
                                  "completedAt": "2026-07-21T20:00:00Z",
                                  "proposals": [],
                                  "error": null
                                }
                                """.formatted(correlationId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_TASK_NOT_READY"))
                .andExpect(jsonPath("$.currentStatus").value("CREATED"))
                .andExpect(jsonPath("$.message")
                        .value("AI task cannot receive a result yet."));
    }
}
