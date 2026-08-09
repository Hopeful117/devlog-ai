package com.hopeful117.devlogai.project.controller;

import com.hopeful117.devlogai.project.dto.response.ProjectResponse;
import com.hopeful117.devlogai.project.entity.ProjectStatus;
import com.hopeful117.devlogai.project.exception.ProjectSlugAlreadyExistsException;
import com.hopeful117.devlogai.project.service.ProjectService;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
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
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private ProjectService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ProjectService.class);
        mvc = mockMvc(new ProjectController(service));
    }

    @Test
    void shouldExposeProjectLifecycleRoutes() throws Exception {
        ProjectResponse response = ProjectResponse.builder()
                .id(UUID.randomUUID()).name("DevLog AI").slug("devlog-ai")
                .status(ProjectStatus.ACTIVE).build();
        when(service.create(any())).thenReturn(response);
        when(service.getAll()).thenReturn(List.of(response));
        when(service.getBySlug("devlog-ai")).thenReturn(response);
        when(service.update(org.mockito.ArgumentMatchers.eq("devlog-ai"), any()))
                .thenReturn(response);

        mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DevLog AI\",\"description\":\"Core\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/projects/devlog-ai"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].slug").value("devlog-ai"));
        mvc.perform(get("/api/v1/projects/devlog-ai"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("DevLog AI"));
        mvc.perform(put("/api/v1/projects/devlog-ai").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Updated\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/projects/devlog-ai/archive"))
                .andExpect(status().isNoContent());
        verify(service).archive("devlog-ai");
        mvc.perform(delete("/api/v1/projects/devlog-ai"))
                .andExpect(status().isNoContent());
        verify(service).delete("devlog-ai");
    }

    @Test
    void shouldRejectBlankUpdatedName() throws Exception {
        mvc.perform(put("/api/v1/projects/devlog-ai")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownProject() throws Exception {
        doThrow(new EntityNotFoundException("Project", "unknown"))
                .when(service).delete("unknown");

        mvc.perform(delete("/api/v1/projects/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    @Test
    void shouldRejectProjectWithoutName() throws Exception {
        mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Missing name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturnExplicitConflictForDuplicateSlug() throws Exception {
        when(service.create(any())).thenThrow(
                new ProjectSlugAlreadyExistsException("devlog-ai"));

        mvc.perform(post("/api/v1/projects")
                        .header("X-Correlation-ID", "project-conflict-42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"DevLog AI\",\"description\":\"Core\"}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Correlation-ID", "project-conflict-42"))
                .andExpect(jsonPath("$.code").value("PROJECT_SLUG_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.correlationId").value("project-conflict-42"));
    }
}
