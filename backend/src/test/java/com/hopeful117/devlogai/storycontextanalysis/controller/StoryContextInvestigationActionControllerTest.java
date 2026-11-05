package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.ai.task.service.AiTaskService;
import com.hopeful117.devlogai.storycontextanalysis.investigation.InvestigationActionRequest;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackAuthenticator;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentMetrics;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAgentCallbackFacade;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import com.hopeful117.devlogai.storycontextanalysis.usecase.StoryAgentFollowUpService;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAnalysisQueryService;
import com.hopeful117.devlogai.storycontextanalysis.usecase.StoryContextInvestigationActionService;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoryContextInvestigationActionControllerTest {
    @Test
    void rejectsUnknownActionFieldsBeforeServiceExecution() throws Exception {
        StoryContextInvestigationActionService service = mock(StoryContextInvestigationActionService.class);
        MockMvc mvc = mvc(service);
        String body = "{\"kind\":\"SEARCH_EVIDENCE\",\"actionId\":\"a1\",\"projectSlug\":\"devlog\","
                + "\"revision\":\"r1\",\"purpose\":\"find\",\"limits\":{\"resultLimit\":1,\"maximumBytes\":100,\"maximumTokens\":20},\"query\":\"x\",\"unexpected\":true}";

        mvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void acceptsHmacAuthenticatedActionWithoutCallerPrincipal() throws Exception {
        StoryContextInvestigationActionService service = mock(StoryContextInvestigationActionService.class);
        UUID taskId = UUID.randomUUID();
        when(service.executeAuthenticatedService(eq(taskId), any())).thenReturn(
                new StoryContextInvestigationActionService.ActionResponse(taskId, false, Map.of("outcome", "EMPTY")));
        MockMvc mvc = mvc(service);
        String body = "{\"protocolVersion\":\"story-context-agent-protocol/v2\",\"executionId\":\"" + taskId + "\","
                + "\"parentTaskId\":\"" + taskId + "\",\"correlationId\":\"" + taskId + "\","
                + "\"provider\":\"openai\",\"credentialBindingId\":\"" + taskId + "\",\"providerOutputTokens\":0,"
                + "\"kind\":\"SEARCH_EVIDENCE\",\"actionId\":\"a1\",\"projectSlug\":\"caller-value\","
                + "\"revision\":\"caller-revision\",\"purpose\":\"find\",\"limits\":{\"resultLimit\":1,\"maximumBytes\":100,\"maximumTokens\":20},\"query\":\"x\"}";
        String path = "/api/v1/story-context-agent/tasks/" + taskId + "/investigation/actions";

        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                        .header("X-SCA-Signature", sign(path, body)))
                .andExpect(status().isOk());
        verify(service).executeAuthenticatedService(eq(taskId), any(InvestigationActionRequest.class));
    }

    private MockMvc mvc(StoryContextInvestigationActionService service) {
        return MockMvcBuilders.standaloneSetup(new StoryContextAgentProtocolController(
                mock(AnalyzeStoryContextUseCase.class), mock(StoryContextAgentCallbackFacade.class), mock(AiTaskService.class),
                new StoryContextAgentCallbackAuthenticator("secret"), new StoryContextAgentMetrics(new SimpleMeterRegistry()),
                new ObjectMapper(), null, null, null, mock(StoryContextAnalysisQueryService.class), service)).build();
    }

    private String path() { return "/api/v1/story-context-agent/tasks/" + UUID.randomUUID() + "/investigation/actions"; }

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
