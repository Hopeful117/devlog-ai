package com.hopeful117.devlogai.source.controller;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.source.dto.response.SourceResponse;
import com.hopeful117.devlogai.source.entity.GitProvider;
import com.hopeful117.devlogai.source.entity.SourceType;
import com.hopeful117.devlogai.source.service.SourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SourceControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private SourceService service;
    private MockMvc mvc;
    private UUID sourceId;
    private UUID projectId;
    private SourceResponse response;

    @BeforeEach
    void setUp() {
        service = mock(SourceService.class);
        mvc = mockMvc(new SourceController(service));
        sourceId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        response = new SourceResponse(sourceId, projectId, SourceType.GIT_REPOSITORY,
                "core", "https://example.test/core.git", "main", GitProvider.GENERIC_GIT,
                true, null, null, null);
    }

    @Test
    void shouldExposeSourceRoutes() throws Exception {
        when(service.create(any())).thenReturn(response);
        when(service.getById(sourceId)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));
        when(service.setActive(sourceId, false)).thenReturn(new SourceResponse(
                sourceId, projectId, SourceType.GIT_REPOSITORY, "core",
                "https://example.test/core.git", "main", GitProvider.GENERIC_GIT,
                false, null, null, null));

        mvc.perform(post("/api/v1/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","type":"GIT_REPOSITORY","name":"core",
                                 "repositoryUrl":"https://example.test/core.git","provider":"GENERIC_GIT"}
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/sources/" + sourceId));
        mvc.perform(get("/api/v1/sources/{id}", sourceId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("GIT_REPOSITORY"));
        mvc.perform(get("/api/v1/sources/project/{id}", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].projectId").value(projectId.toString()));
        mvc.perform(patch("/api/v1/sources/{id}/activation", sourceId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void shouldReturnBadRequestForInvalidUuidAndEnum() throws Exception {
        mvc.perform(get("/api/v1/sources/not-a-uuid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/v1/sources").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","type":"UNKNOWN","name":"core",
                                 "repositoryUrl":"https://example.test/core.git"}
                                """.formatted(projectId)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }
}
