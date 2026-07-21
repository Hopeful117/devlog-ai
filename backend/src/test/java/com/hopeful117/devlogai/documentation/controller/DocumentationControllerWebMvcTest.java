package com.hopeful117.devlogai.documentation.controller;

import com.hopeful117.devlogai.documentation.dto.response.DocumentationResponse;
import com.hopeful117.devlogai.documentation.entity.DocumentationType;
import com.hopeful117.devlogai.documentation.service.DocumentationService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentationControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllDocumentationRoutes() throws Exception {
        DocumentationService service = mock(DocumentationService.class);
        MockMvc mvc = mockMvc(new DocumentationController(service));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        DocumentationResponse response = new DocumentationResponse(id, projectId, "Architecture",
                DocumentationType.ARCHITECTURE, "content", 1, null, null);
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));
        when(service.getByProjectAndType(projectId, DocumentationType.ARCHITECTURE))
                .thenReturn(List.of(response));

        mvc.perform(post("/api/v1/documentations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"title\":\"Architecture\",\"type\":\"ARCHITECTURE\",\"content\":\"content\"}"
                                .formatted(projectId))).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/documentations/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/documentations/project/{id}", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value(1));
        mvc.perform(get("/api/v1/documentations/project/{id}/type/ARCHITECTURE", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("ARCHITECTURE"));
    }
}
