package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.ai.task.entity.AiTaskStatus;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentExecution;
import com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevLogStoryAgentControllerTest {
    @Test
    void bindsStructuredBodyAndIdempotencyHeader() throws Exception {
        DevLogStoryAgentService service = mock(DevLogStoryAgentService.class);
        UUID taskId = UUID.randomUUID();
        when(service.execute(any())).thenReturn(new DevLogStoryAgentExecution(
                AiTaskStatus.PROCESSING, taskId, taskId, Map.of(), Map.of("status", "PROCESSING")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DevLogStoryAgentController(service)).build();

        mvc.perform(post("/api/v1/projects/devlog-ai/story-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "request-1")
                        .content("""
                                {"storyId":"%s","intent":"engineering-story-context-analysis",
                                 "files":["README.md"],"guidance":{"focus":"controller"}}
                                """.formatted(taskId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.aiTaskId").value(taskId.toString()))
                .andExpect(jsonPath("$.snapshotId").value(taskId.toString()))
                .andExpect(jsonPath("$.result").isEmpty())
                .andExpect(jsonPath("$.diagnostics.status").value("PROCESSING"));

        verify(service).execute(any());
    }

    @Test
    void rejectsMissingRequiredBodyFields() throws Exception {
        DevLogStoryAgentService service = mock(DevLogStoryAgentService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DevLogStoryAgentController(service)).build();

        mvc.perform(post("/api/v1/projects/devlog-ai/story-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"files\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
