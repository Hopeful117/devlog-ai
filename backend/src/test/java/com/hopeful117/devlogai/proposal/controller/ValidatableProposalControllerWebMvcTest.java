package com.hopeful117.devlogai.proposal.controller;

import com.hopeful117.devlogai.proposal.dto.response.ValidatableProposalResponse;
import com.hopeful117.devlogai.proposal.entity.ProposalStatus;
import com.hopeful117.devlogai.proposal.entity.ProposalType;
import com.hopeful117.devlogai.proposal.service.ValidatableProposalService;
import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidatableProposalControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllProposalRoutes() throws Exception {
        ValidatableProposalService service = mock(ValidatableProposalService.class);
        MockMvc mvc = mockMvc(new ValidatableProposalController(service));
        UUID id = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        ValidatableProposalResponse response = new ValidatableProposalResponse(id, projectId, analysisId,
                ProposalType.INSIGHT, ProposalStatus.PROPOSED, null, null, null);
        when(service.create(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProjectId(projectId)).thenReturn(List.of(response));
        when(service.getByAnalysisId(analysisId)).thenReturn(List.of(response));

        mvc.perform(post("/api/v1/proposals").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"%s\",\"analysisId\":\"%s\",\"type\":\"INSIGHT\",\"payload\":{\"title\":\"x\"}}"
                                .formatted(projectId, analysisId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PROPOSED"));
        mvc.perform(get("/api/v1/proposals/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/proposals/project/{id}", projectId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/proposals/analysis/{id}", analysisId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("INSIGHT"));
    }
}
