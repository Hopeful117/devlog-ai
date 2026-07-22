package com.hopeful117.devlogai.insight.controller;

import com.hopeful117.devlogai.insight.dto.response.InsightResponse;
import com.hopeful117.devlogai.insight.entity.InsightSeverity;
import com.hopeful117.devlogai.insight.entity.InsightType;
import com.hopeful117.devlogai.insight.service.InsightService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InsightControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllInsightRoutes() throws Exception {
        InsightService service = mock(InsightService.class);
        MockMvc mvc = mockMvc(new InsightController(service));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        InsightResponse response = new InsightResponse(id, projectId, analysisId,
                UUID.randomUUID(), UUID.randomUUID(), InsightType.ARCHITECTURAL,
                InsightSeverity.CRITICAL, "Boundary", "content", null, null);
        List<InsightResponse> responses = List.of(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(responses);
        when(service.getByAnalysis(analysisId)).thenReturn(responses);
        when(service.getByProjectAndType(projectId, InsightType.ARCHITECTURAL)).thenReturn(responses);
        when(service.getByProjectAndSeverity(projectId, InsightSeverity.CRITICAL)).thenReturn(responses);
        when(service.getByProjectAndTypeAndSeverity(projectId, InsightType.ARCHITECTURAL, InsightSeverity.CRITICAL))
                .thenReturn(responses);

        mvc.perform(get("/api/v1/insights/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/project/{id}", projectId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/analysis/{id}", analysisId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/project/{id}/type/ARCHITECTURAL", projectId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/project/{id}/severity/CRITICAL", projectId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/insights/project/{id}/type/ARCHITECTURAL/severity/CRITICAL", projectId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].severity").value("CRITICAL"));
    }
}
