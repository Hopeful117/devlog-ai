package com.hopeful117.devlogai.proposal.review;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProposalReviewControllerWebMvcTest extends ControllerWebMvcTestSupport {
    @Test
    void shouldExposeVersionedPagedReviewContract() throws Exception {
        ProposalReviewService service = mock(ProposalReviewService.class);
        UUID analysisId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        var response = new ProposalReviewResponse(ProposalReviewResponse.PROJECTION_VERSION,
                analysisId, projectId, new ProposalReviewResponse.Counts(3, 2, 1, 0),
                new ProposalReviewResponse.Page(1, 10, 2, true, false), List.of());
        when(service.get(analysisId, 1, 10)).thenReturn(response);
        MockMvc mvc = mockMvc(new ProposalReviewController(service));

        mvc.perform(get("/api/v1/analyses/{id}/proposal-review", analysisId)
                        .param("page", "1").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("proposal-review-v2"))
                .andExpect(jsonPath("$.counts.pending").value(2))
                .andExpect(jsonPath("$.page.hasPrevious").value(true));
        verify(service).get(analysisId, 1, 10);
    }

    @Test
    void shouldRejectMalformedPaginationBeforeCallingService() throws Exception {
        ProposalReviewService service = mock(ProposalReviewService.class);
        MockMvc mvc = mockMvc(new ProposalReviewController(service));

        mvc.perform(get("/api/v1/analyses/{id}/proposal-review", UUID.randomUUID())
                        .param("page", "not-a-number"))
                .andExpect(status().isBadRequest());
    }
}
