package com.hopeful117.devlogai.story.controller;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.story.dto.response.EngineeringStoryResponse;
import com.hopeful117.devlogai.story.entity.StoryStatus;
import com.hopeful117.devlogai.story.service.EngineeringStoryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class EngineeringStoryControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private EngineeringStoryResponse response(UUID id, UUID projectId) {
        return new EngineeringStoryResponse(
                id, projectId, 1, "Story 0001", "docs/stories/0001",
                "abc123", "def456", StoryStatus.IN_PROGRESS,
                Instant.now(), Instant.now(), null);
    }

    @Test
    void shouldExposeAllStoryRoutes() throws Exception {
        EngineeringStoryService service = mock(EngineeringStoryService.class);
        MockMvc mvc = mockMvc(new EngineeringStoryController(service));

        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        EngineeringStoryResponse response = response(id, projectId);

        when(service.register(any())).thenReturn(response);
        when(service.startImplementation(eq(id), eq(projectId), any())).thenReturn(response);
        when(service.complete(eq(id), eq(projectId), any())).thenReturn(response);
        when(service.getById(id, projectId)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));

        mvc.perform(post("/api/v1/projects/{projectId}/stories", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"Story 0001","storyNumber":1,"storyPath":"docs/stories/0001"}
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/projects/" + projectId + "/stories/" + id));

        mvc.perform(post("/api/v1/projects/{projectId}/stories/{storyId}/start",
                        projectId, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseCommit":"abc123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mvc.perform(post("/api/v1/projects/{projectId}/stories/{storyId}/complete",
                        projectId, id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetCommit":"def456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mvc.perform(get("/api/v1/projects/{projectId}/stories/{storyId}", projectId, id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Story 0001"));

        mvc.perform(get("/api/v1/projects/{projectId}/stories", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId").value(projectId.toString()));
    }
}