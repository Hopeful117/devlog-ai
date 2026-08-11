package com.hopeful117.devlogai.timeline.controller;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import com.hopeful117.devlogai.timeline.dto.TimelineEntry;
import com.hopeful117.devlogai.timeline.dto.TimelineEntryType;
import com.hopeful117.devlogai.timeline.dto.TimelineResponse;
import com.hopeful117.devlogai.timeline.service.TimelineProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TimelineControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldReturnTimelineSuccessfully() throws Exception {
        TimelineProjectionService service = mock(TimelineProjectionService.class);
        MockMvc mvc = mockMvc(new TimelineController(service));

        UUID projectId = UUID.randomUUID();
        TimelineResponse response = new TimelineResponse(
                projectId,
                "Timeline Project",
                List.of(new TimelineEntry(
                        UUID.randomUUID(),
                        TimelineEntryType.KNOWLEDGE_EVENT,
                        Instant.parse("2026-08-01T10:00:00Z"),
                        "Chose timeline model",
                        "ARCHITECTURE"
                ))
        );
        when(service.getTimeline(projectId)).thenReturn(response);

        mvc.perform(get("/api/v1/projects/{projectId}/timeline", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Timeline Project"))
                .andExpect(jsonPath("$.entries[0].type").value("KNOWLEDGE_EVENT"))
                .andExpect(jsonPath("$.entries[0].title").value("Chose timeline model"))
                .andExpect(jsonPath("$.entries[0].detail").value("ARCHITECTURE"))
                .andExpect(jsonPath("$.entries[0].timestamp").value("2026-08-01T10:00:00Z"));
    }

    @Test
    void shouldReturn404WhenProjectNotFound() throws Exception {
        TimelineProjectionService service = mock(TimelineProjectionService.class);
        MockMvc mvc = mockMvc(new TimelineController(service));

        UUID projectId = UUID.randomUUID();
        when(service.getTimeline(projectId))
                .thenThrow(new EntityNotFoundException("Project", projectId));

        mvc.perform(get("/api/v1/projects/{projectId}/timeline", projectId))
                .andExpect(status().isNotFound());
    }
}