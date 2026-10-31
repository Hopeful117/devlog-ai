package com.hopeful117.devlogai.storycontextanalysis.controller;

import com.hopeful117.devlogai.authorization.AuthenticatedPrincipal;
import com.hopeful117.devlogai.authorization.AuthenticatedPrincipalResolver;
import com.hopeful117.devlogai.authorization.PrincipalKind;
import com.hopeful117.devlogai.storycontextanalysis.service.AuthorizedStoryContextSnapshotReader;
import com.hopeful117.devlogai.storycontextanalysis.service.StoryContextAnalysisQueryService;
import com.hopeful117.devlogai.storycontextanalysis.usecase.AnalyzeStoryContextUseCase;
import com.hopeful117.devlogai.shared.exception.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoryContextAnalysisControllerTest {
    @Test
    void rejectsAnalysisSubmissionWithoutAuthenticatedPrincipal() throws Exception {
        AnalyzeStoryContextUseCase useCase = mock(AnalyzeStoryContextUseCase.class);
        MockMvc mvc = mvc(useCase, request -> Optional.empty());

        mvc.perform(post("/api/v1/projects/devlog-ai/analyze-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(useCase);
    }

    @Test
    void passesAuthenticatedPrincipalToAnalysisUseCase() throws Exception {
        AnalyzeStoryContextUseCase useCase = mock(AnalyzeStoryContextUseCase.class);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "opencode-agent", PrincipalKind.AI_AGENT, "keycloak");
        UUID taskId = UUID.randomUUID();
        when(useCase.execute(any(), any(), any(), any(), any(), any(), eq(principal), eq("chatgpt")))
                .thenReturn(taskId);
        MockMvc mvc = mvc(useCase, request -> Optional.of(principal));

        mvc.perform(post("/api/v1/projects/devlog-ai/analyze-context")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"intent\":\"inspect context\"}"))
                .andExpect(status().isAccepted());

        verify(useCase).execute(eq("devlog-ai"), eq(null), eq("inspect context"), any(), any(),
                eq(null), eq(principal), eq("chatgpt"));
    }

    @Test
    void rejectsResultReadWithoutAuthenticatedPrincipal() throws Exception {
        MockMvc mvc = mvc(mock(AnalyzeStoryContextUseCase.class), request -> Optional.empty());

        mvc.perform(get("/api/v1/ai/tasks/{id}/story-context-analysis", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorizesResultBeforeQueryingIt() throws Exception {
        AuthorizedStoryContextSnapshotReader reader = mock(AuthorizedStoryContextSnapshotReader.class);
        StoryContextAnalysisQueryService query = mock(StoryContextAnalysisQueryService.class);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "agent", PrincipalKind.AI_AGENT, "keycloak");
        UUID taskId = UUID.randomUUID();
        when(query.findResponseByAiTaskId(taskId)).thenReturn(Optional.empty());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new StoryContextAnalysisController(
                mock(AnalyzeStoryContextUseCase.class), query, reader,
                request -> Optional.of(principal)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/v1/ai/tasks/{id}/story-context-analysis", taskId))
                .andExpect(status().isNotFound());

        verify(reader).authorize(principal, taskId);
        verify(query).findResponseByAiTaskId(taskId);
    }

    private static MockMvc mvc(AnalyzeStoryContextUseCase useCase,
                               AuthenticatedPrincipalResolver resolver) {
        return MockMvcBuilders.standaloneSetup(new StoryContextAnalysisController(
                useCase, mock(StoryContextAnalysisQueryService.class),
                mock(AuthorizedStoryContextSnapshotReader.class), resolver))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
}
