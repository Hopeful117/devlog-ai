package com.hopeful117.devlogai.projectcontextinput.controller;

import com.hopeful117.devlogai.projectcontextinput.dto.response.ProjectHumanContextInputResponse;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputStatus;
import com.hopeful117.devlogai.projectcontextinput.entity.ProjectHumanContextInputType;
import com.hopeful117.devlogai.projectcontextinput.service.ProjectHumanContextInputService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectHumanContextInputControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private ProjectHumanContextInputService service;
    private MockMvc mvc;
    private UUID projectId;
    private UUID inputId;
    private ProjectHumanContextInputResponse response;

    @BeforeEach
    void setUp() {
        service = mock(ProjectHumanContextInputService.class);
        mvc = mockMvc(new ProjectHumanContextInputController(service));
        projectId = UUID.randomUUID();
        inputId = UUID.randomUUID();
        response = new ProjectHumanContextInputResponse(
                inputId, projectId, "Medium-term goal",
                "Improve semantic usefulness for humans and agents.",
                ProjectHumanContextInputType.GOAL,
                ProjectHumanContextInputStatus.ACTIVE,
                null, null
        );
    }

    @Test
    void shouldExposeProjectContextInputRoutes() throws Exception {
        when(service.create(any(), any())).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));
        when(service.archive(projectId, inputId)).thenReturn(new ProjectHumanContextInputResponse(
                inputId, projectId, response.title(), response.contentMarkdown(), response.type(),
                ProjectHumanContextInputStatus.ARCHIVED, null, null
        ));

        mvc.perform(post("/api/v1/projects/{projectId}/context-inputs", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Medium-term goal","contentMarkdown":"Improve semantic usefulness.","type":"GOAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/projects/" + projectId + "/context-inputs/" + inputId))
                .andExpect(jsonPath("$.type").value("GOAL"));

        mvc.perform(get("/api/v1/projects/{projectId}/context-inputs", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()));

        mvc.perform(patch("/api/v1/projects/{projectId}/context-inputs/{inputId}/archive",
                        projectId, inputId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        verify(service).archive(projectId, inputId);
    }

    @Test
    void shouldRejectInvalidCreatePayload() throws Exception {
        mvc.perform(post("/api/v1/projects/{projectId}/context-inputs", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" ","contentMarkdown":" ","type":"UNKNOWN"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
