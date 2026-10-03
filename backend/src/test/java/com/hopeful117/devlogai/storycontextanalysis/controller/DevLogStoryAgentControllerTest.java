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
                                 "question":"Which controller changed?",
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

    @Test
    void defaultsOptionalFilesToAnEmptyList() throws Exception {
        DevLogStoryAgentService service = mock(DevLogStoryAgentService.class);
        UUID taskId = UUID.randomUUID();
        when(service.execute(any())).thenReturn(new DevLogStoryAgentExecution(
                AiTaskStatus.PROCESSING, taskId, taskId, Map.of(), Map.of("status", "PROCESSING")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DevLogStoryAgentController(service)).build();

        mvc.perform(post("/api/v1/projects/devlog-ai/story-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intent\":\"engineering-story-context-analysis\",\"question\":\"What changed?\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"));

        var request = org.mockito.ArgumentCaptor.forClass(
                com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentRequest.class);
        verify(service).execute(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().files()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(request.getValue().storyId()).isNull();
    }

    @Test
    void usesIntentAsQuestionWhenQuestionIsOmitted() throws Exception {
        DevLogStoryAgentService service = mock(DevLogStoryAgentService.class);
        UUID taskId = UUID.randomUUID();
        when(service.execute(any())).thenReturn(new DevLogStoryAgentExecution(
                AiTaskStatus.PROCESSING, taskId, taskId, Map.of(), Map.of("status", "PROCESSING")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DevLogStoryAgentController(service)).build();

        mvc.perform(post("/api/v1/projects/trading-os/story-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intent\":\"Investigate Trading OS\"}"))
                .andExpect(status().isAccepted());

        var request = org.mockito.ArgumentCaptor.forClass(
                com.hopeful117.devlogai.storycontextanalysis.usecase.DevLogStoryAgentRequest.class);
        verify(service).execute(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().question())
                .isEqualTo("Investigate Trading OS");
    }

    @Test
    void returnsFailureDiagnosticsAsStructuredAgentResponse() throws Exception {
        DevLogStoryAgentService service = mock(DevLogStoryAgentService.class);
        UUID taskId = UUID.randomUUID();
        when(service.execute(any())).thenReturn(new DevLogStoryAgentExecution(
                AiTaskStatus.FAILED, taskId, taskId, Map.of(),
                Map.of("status", "FAILED", "failureCode", "INVALID_LLM_OUTPUT",
                        "failureMessage", "The generated output was invalid")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DevLogStoryAgentController(service)).build();

        mvc.perform(post("/api/v1/projects/devlog-ai/story-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"intent":"engineering-story-context-analysis",
                                 "question":"What failed?",
                                 "files":["README.md"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.diagnostics.failureCode").value("INVALID_LLM_OUTPUT"))
                .andExpect(jsonPath("$.diagnostics.failureMessage")
                        .value("The generated output was invalid"));
    }
}
