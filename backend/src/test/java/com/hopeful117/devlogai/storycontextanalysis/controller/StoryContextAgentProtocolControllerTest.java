package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.ai.engine.exception.AiTaskResultConflictException;
import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.shared.exception.handler.GlobalExceptionHandler;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackAuthenticator;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackFacade;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentMetrics;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoryContextAgentProtocolControllerTest {
    @Test
    void terminalPayloadConflictReachesConflictHandler() throws Exception {
        UUID taskId = UUID.randomUUID();
        String path = "/api/v1/story-context-agent/tasks/" + taskId + "/callback";
        String body = "{\"correlationId\":\"" + taskId
                + "\",\"status\":\"COMPLETED\",\"completedAt\":\"2026-09-27T00:00:00Z\",\"proposals\":[]}";
        StoryContextAgentCallbackFacade facade = mock(StoryContextAgentCallbackFacade.class);
        when(facade.callback(any(), any())).thenThrow(new AiTaskResultConflictException(
                "AI_TASK_RESULT_CONFLICT", null, "different terminal payload"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StoryContextAgentProtocolController(
                mock(AnalyzeStoryContextUseCase.class), facade, mock(AiTaskService.class),
                new StoryContextAgentCallbackAuthenticator("secret"), new StoryContextAgentMetrics(registry),
                new ObjectMapper())).setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-SCA-Signature", sign(path, body)))
                .andExpect(status().isConflict());
        assertEquals(1, registry.find("sca_operation_latency").tag("operation", "callback").timer().count());
    }

    @Test
    void malformedCallbackIsRejectedBeforeFacadeAndDoesNotMutate() throws Exception {
        UUID taskId = UUID.randomUUID();
        String path = "/api/v1/story-context-agent/tasks/" + taskId + "/callback";
        String body = "{\"correlationId\":\"" + taskId
                + "\",\"status\":\"COMPLETED\",\"completedAt\":\"2026-09-27T00:00:00Z\",\"proposals\":null}";
        StoryContextAgentCallbackFacade facade = mock(StoryContextAgentCallbackFacade.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StoryContextAgentProtocolController(
                mock(AnalyzeStoryContextUseCase.class), facade, mock(AiTaskService.class),
                new StoryContextAgentCallbackAuthenticator("secret"), new StoryContextAgentMetrics(registry),
                new ObjectMapper())).setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-SCA-Signature", sign(path, body)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(facade);
        assertEquals(1, registry.find("sca_operation_latency").tag("operation", "callback").timer().count());
    }

    @Test
    void callbackAuthenticationFailureIsTimedExactlyOnce() throws Exception {
        UUID taskId = UUID.randomUUID();
        String path = "/api/v1/story-context-agent/tasks/" + taskId + "/callback";
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StoryContextAgentProtocolController(
                mock(AnalyzeStoryContextUseCase.class), mock(StoryContextAgentCallbackFacade.class),
                mock(AiTaskService.class), new StoryContextAgentCallbackAuthenticator("secret"),
                new StoryContextAgentMetrics(registry), new ObjectMapper())).build();

        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        assertEquals(1, registry.find("sca_operation_latency").tag("operation", "callback").timer().count());
    }

    private String sign(String path, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] prefix = ("POST\n" + path + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[prefix.length + bytes.length];
        System.arraycopy(prefix, 0, input, 0, prefix.length);
        System.arraycopy(bytes, 0, input, prefix.length, bytes.length);
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(input));
    }
}
