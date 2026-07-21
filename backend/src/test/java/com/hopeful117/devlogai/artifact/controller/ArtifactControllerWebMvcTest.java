package com.hopeful117.devlogai.artifact.controller;

import com.hopeful117.devlogai.artifact.dto.response.ArtifactResponse;
import com.hopeful117.devlogai.artifact.entity.ArtifactType;
import com.hopeful117.devlogai.artifact.service.ArtifactService;
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

class ArtifactControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllArtifactRoutes() throws Exception {
        ArtifactService service = mock(ArtifactService.class);
        MockMvc mvc = mockMvc(new ArtifactController(service));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ArtifactResponse response = new ArtifactResponse(id, projectId, "Dockerfile",
                ArtifactType.INFRASTRUCTURE, "Dockerfile", null, null, null);
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));
        when(service.getByProjectAndType(projectId, ArtifactType.INFRASTRUCTURE))
                .thenReturn(List.of(response));

        mvc.perform(post("/api/v1/artifacts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"name\":\"Dockerfile\",\"type\":\"INFRASTRUCTURE\"}"
                                .formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/artifacts/" + id));
        mvc.perform(get("/api/v1/artifacts/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/artifacts/project/{id}", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Dockerfile"));
        mvc.perform(get("/api/v1/artifacts/project/{id}/type/INFRASTRUCTURE", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("INFRASTRUCTURE"));
    }
}
