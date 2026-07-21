package com.hopeful117.devlogai.milestone.controller;

import com.hopeful117.devlogai.milestone.dto.response.MilestoneResponse;
import com.hopeful117.devlogai.milestone.entity.MilestoneStatus;
import com.hopeful117.devlogai.milestone.service.MilestoneService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MilestoneControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllMilestoneRoutes() throws Exception {
        MilestoneService service = mock(MilestoneService.class);
        MockMvc mvc = mockMvc(new MilestoneController(service));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        MilestoneResponse response = new MilestoneResponse(id, projectId, "V1", null,
                MilestoneStatus.PLANNED, Instant.parse("2026-07-21T00:00:00Z"), null, null, null);
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));
        when(service.getByProjectAndStatus(projectId, MilestoneStatus.PLANNED)).thenReturn(List.of(response));

        mvc.perform(post("/api/v1/milestones").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"name\":\"V1\",\"startedAt\":\"2026-07-21T00:00:00Z\"}"
                                .formatted(projectId))).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/milestones/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/milestones/project/{id}", projectId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/milestones/project/{id}/status/PLANNED", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PLANNED"));
    }
}
