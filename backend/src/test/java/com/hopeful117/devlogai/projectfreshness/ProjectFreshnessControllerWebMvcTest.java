package com.hopeful117.devlogai.projectfreshness;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectFreshnessControllerWebMvcTest extends ControllerWebMvcTestSupport {
    @Test
    void shouldExposeCheckAndLatestContracts() throws Exception {
        ProjectFreshnessService service = mock(ProjectFreshnessService.class);
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ProjectFreshnessResponse response = response(projectId, sourceId);
        when(service.check(projectId, sourceId)).thenReturn(response);
        when(service.latest(projectId, sourceId)).thenReturn(Optional.of(response));
        var mvc = mockMvc(new ProjectFreshnessController(service));

        mvc.perform(post("/api/v1/projects/{id}/freshness-checks", projectId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceId\":\"%s\"}".formatted(sourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("project-freshness-v1"))
                .andExpect(jsonPath("$.status").value("STALE"));
        mvc.perform(get("/api/v1/projects/{id}/freshness-checks/latest", projectId)
                        .param("sourceId", sourceId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturnNoContentWhenNeverCheckedAndValidateBody() throws Exception {
        ProjectFreshnessService service = mock(ProjectFreshnessService.class);
        UUID projectId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        when(service.latest(projectId, sourceId)).thenReturn(Optional.empty());
        var mvc = mockMvc(new ProjectFreshnessController(service));

        mvc.perform(get("/api/v1/projects/{id}/freshness-checks/latest", projectId)
                        .param("sourceId", sourceId.toString()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/projects/{id}/freshness-checks", projectId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldExposeTheProjectFreshnessSummary() throws Exception {
        ProjectFreshnessService service = mock(ProjectFreshnessService.class);
        UUID projectId = UUID.randomUUID();
        ProjectFreshnessSummary summary = new ProjectFreshnessSummary(
                ProjectFreshnessSummary.PROJECTION_VERSION, projectId,
                java.util.List.of(response(projectId, UUID.randomUUID())), 0, false);
        when(service.summary(projectId)).thenReturn(summary);
        var mvc = mockMvc(new ProjectFreshnessController(service));

        mvc.perform(get("/api/v1/projects/{id}/freshness-checks/summary", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("project-freshness-summary-v1"))
                .andExpect(jsonPath("$.checkedSources[0].status").value("STALE"))
                .andExpect(jsonPath("$.uncheckedSourceCount").value(0));
    }

    private ProjectFreshnessResponse response(UUID projectId, UUID sourceId) {
        return new ProjectFreshnessResponse(ProjectFreshnessResponse.PROJECTION_VERSION,
                UUID.randomUUID(), projectId,
                new ProjectFreshnessResponse.Source(sourceId, "repo", "main",
                        "origin/main", "a".repeat(40)), Instant.now(),
                ProjectFreshnessStatus.STALE, ProjectRefreshGuidance.REFRESH_RECOMMENDED,
                new ProjectFreshnessResponse.Baseline(UUID.randomUUID(), Instant.now(),
                        "b".repeat(40)), new ProjectFreshnessResponse.ReviewCounts(2, 1, 1, 0));
    }
}
