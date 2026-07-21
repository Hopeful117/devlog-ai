package com.hopeful117.devlogai.decision.controller;

import com.hopeful117.devlogai.decision.dto.response.DecisionResponse;
import com.hopeful117.devlogai.decision.service.DecisionService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DecisionControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllDecisionRoutes() throws Exception {
        DecisionService service = mock(DecisionService.class);
        MockMvc mvc = mockMvc(new DecisionController(service));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        DecisionResponse response = new DecisionResponse(id, projectId, "Use REST", "context",
                "REST", "simple", null, null, null);
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));

        mvc.perform(post("/api/v1/decisions").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Use REST","context":"context",
                                 "choice":"REST","rationale":"simple"}
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/decisions/" + id));
        mvc.perform(get("/api/v1/decisions/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.choice").value("REST"));
        mvc.perform(get("/api/v1/decisions/project/{id}", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].projectId").value(projectId.toString()));
    }
}
