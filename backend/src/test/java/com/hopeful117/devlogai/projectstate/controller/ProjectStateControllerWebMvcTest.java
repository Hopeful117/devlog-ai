package com.hopeful117.devlogai.projectstate.controller;

import com.hopeful117.devlogai.projectstate.dto.response.ActiveWorkSection;
import com.hopeful117.devlogai.projectstate.dto.response.ObjectiveSection;
import com.hopeful117.devlogai.projectstate.dto.response.PendingActionsSection;
import com.hopeful117.devlogai.projectstate.dto.response.ProjectStateResponse;
import com.hopeful117.devlogai.projectstate.dto.response.RecentChangesSection;
import com.hopeful117.devlogai.projectstate.dto.response.RecentEvolutionSection;
import com.hopeful117.devlogai.projectstate.dto.response.RecentKnowledgeSection;
import com.hopeful117.devlogai.projectstate.dto.response.RoadmapProgressSection;
import com.hopeful117.devlogai.projectstate.service.ProjectStateProjectionService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectStateControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldReturnProjectStateSuccessfully() throws Exception {
        ProjectStateProjectionService service = mock(ProjectStateProjectionService.class);
        MockMvc mvc = mockMvc(new ProjectStateController(service));

        UUID projectId = UUID.randomUUID();
        ProjectStateResponse response = new ProjectStateResponse(
                projectId,
                "Test Project",
                new ObjectiveSection("description", null, null, Collections.emptyList()),
                new ActiveWorkSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new RecentChangesSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new RoadmapProgressSection(Collections.emptyList(), Collections.emptyList()),
                new PendingActionsSection(Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
                new RecentKnowledgeSection(Collections.emptyList()),
                new RecentEvolutionSection(Collections.emptyList())
        );

        when(service.getProjectState(projectId)).thenReturn(response);

        mvc.perform(get("/api/v1/projects/{projectId}/state", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.projectName").value("Test Project"))
                .andExpect(jsonPath("$.objective.description").value("description"))
                .andExpect(jsonPath("$.recentKnowledge.recentKnowledge").isArray())
                .andExpect(jsonPath("$.recentEvolution.recentEvolution").isArray());
    }

    @Test
    void shouldReturn404WhenProjectNotFound() throws Exception {
        ProjectStateProjectionService service = mock(ProjectStateProjectionService.class);
        MockMvc mvc = mockMvc(new ProjectStateController(service));

        UUID projectId = UUID.randomUUID();
        when(service.getProjectState(projectId))
                .thenThrow(new EntityNotFoundException("Project", projectId));

        mvc.perform(get("/api/v1/projects/{projectId}/state", projectId))
                .andExpect(status().isNotFound());
    }
}
