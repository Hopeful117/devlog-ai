package com.hopeful117.devlogai.history.controller;

import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryCommitMatch;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryMatch;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryMatchedOn;
import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistorySearchResult;
import com.hopeful117.devlogai.history.service.ProjectHistorySearchService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProjectHistorySearchControllerWebMvcTest extends ControllerWebMvcTestSupport {

    private static final UUID PROJECT_ID =
            UUID.fromString("f3d56247-aada-4a76-982b-e6802c0b309c");

    private ProjectHistorySearchService searchService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        searchService = mock(ProjectHistorySearchService.class);
        mvc = mockMvc(new ProjectHistoryController(
                mock(com.hopeful117.devlogai.history.service.ProjectHistoryService.class),
                searchService));
    }

    @Test
    void shouldReturnSearchResultWithMatchesAndResource() throws Exception {
        String sha = "3cd3723206eae38d518eb696a1dd50c0476264d0";
        when(searchService.search(PROJECT_ID, "markdown rendering", 5))
                .thenReturn(new ProjectHistorySearchResult(
                        "markdown rendering", 1, false,
                        List.of(new ProjectHistoryCommitMatch(
                                sha, "fix project note markdown preview", "ludo",
                                Instant.parse("2026-08-16T15:50:15Z"), PROJECT_ID,
                                25,
                                List.of(new ProjectHistoryMatch(
                                        ProjectHistoryMatchedOn.COMMIT_MESSAGE,
                                        "fix project note markdown preview")),
                                "devlog://projects/devlog-ai/commits/" + sha))));

        mvc.perform(MockMvcRequestBuilders
                        .get("/api/v1/project-history/projects/{projectId}/commits/search",
                                PROJECT_ID)
                        .queryParam("query", "markdown rendering")
                        .queryParam("limit", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("markdown rendering"))
                .andExpect(jsonPath("$.totalMatches").value(1))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.results[0].commitSha").value(sha))
                .andExpect(jsonPath("$.results[0].relevance").value(25))
                .andExpect(jsonPath("$.results[0].matches[0].matchedOn")
                        .value("COMMIT_MESSAGE"))
                .andExpect(jsonPath("$.results[0].resource")
                        .value("devlog://projects/devlog-ai/commits/" + sha));

        verify(searchService).search(PROJECT_ID, "markdown rendering", 5);
    }

    @Test
    void shouldPropagateInvalidLimitAs400() throws Exception {
        when(searchService.search(PROJECT_ID, "markdown", 0)).thenThrow(
                new com.hopeful117.devlogai.shared.exception.InvalidParameterException(
                        "limit", 0));

        mvc.perform(MockMvcRequestBuilders
                        .get("/api/v1/project-history/projects/{projectId}/commits/search",
                                PROJECT_ID)
                        .queryParam("query", "markdown")
                        .queryParam("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCallServiceWithDefaultLimitWhenAbsent() throws Exception {
        when(searchService.search(PROJECT_ID, "engine", null))
                .thenReturn(new ProjectHistorySearchResult("engine", 0, false,
                        List.of()));

        mvc.perform(MockMvcRequestBuilders
                        .get("/api/v1/project-history/projects/{projectId}/commits/search",
                                PROJECT_ID)
                        .queryParam("query", "engine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isEmpty());

        verify(searchService).search(PROJECT_ID, "engine", null);
    }
}
