package com.hopeful117.devlogai.challenge.controller;

import com.hopeful117.devlogai.challenge.dto.response.ChallengeResponse;
import com.hopeful117.devlogai.challenge.entity.ChallengeStatus;
import com.hopeful117.devlogai.challenge.service.ChallengeService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChallengeControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllChallengeRoutes() throws Exception {
        ChallengeService service = mock(ChallengeService.class);
        MockMvc mvc = mockMvc(new ChallengeController(service));

        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        ChallengeResponse response = new ChallengeResponse(
                id,
                projectId,
                "DB migration failure",
                "Flyway fails",
                "Blocks deploy",
                ChallengeStatus.OPEN,
                null,
                null,
                null
        );

        ChallengeResponse updatedResponse = new ChallengeResponse(
                id,
                projectId,
                "DB migration failure",
                "Flyway fails",
                "Blocks deploy",
                ChallengeStatus.RESOLVED,
                "Fixed",
                null,
                null
        );

        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProject(projectId)).thenReturn(List.of(response));
        when(service.update(any(), any())).thenReturn(updatedResponse);

        mvc.perform(post("/api/v1/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","title":"DB migration failure",
                                 "description":"Flyway fails","impact":"Blocks deploy"}
                                """.formatted(projectId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "/api/v1/challenges/" + id));

        mvc.perform(get("/api/v1/challenges/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("DB migration failure"));

        mvc.perform(get("/api/v1/challenges/project/{id}", projectId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].projectId")
                        .value(projectId.toString()));

        mvc.perform(put("/api/v1/challenges/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"RESOLVED","resolution":"Fixed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }
}
