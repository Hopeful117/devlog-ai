package com.hopeful117.devlogai.knowledge.controller;

import com.hopeful117.devlogai.knowledge.dto.response.KnowledgeEventResponse;
import com.hopeful117.devlogai.knowledge.entity.KnowledgeEventType;
import com.hopeful117.devlogai.knowledge.service.KnowledgeEventService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class KnowledgeEventControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldSerializeBodiesDespiteControllerStereotype() throws Exception {
        KnowledgeEventService service = mock(KnowledgeEventService.class);
        MockMvc mvc = mockMvc(new KnowledgeEventController(service));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        KnowledgeEventResponse response = new KnowledgeEventResponse(id, projectId,
                KnowledgeEventType.FEATURE, "Sources", "Added", null, null);
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));

        mvc.perform(post("/api/v1/knowledge-events").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"type\":\"FEATURE\",\"title\":\"Sources\"}"
                                .formatted(projectId)))
                .andExpect(status().isCreated()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/v1/knowledge-events/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("FEATURE"));
        mvc.perform(get("/api/v1/knowledge-events/project/{id}", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Sources"));
    }
}
