package com.hopeful117.devlogai.validation.controller;

import com.hopeful117.devlogai.shared.controller.ControllerWebMvcTestSupport;
import com.hopeful117.devlogai.validation.dto.response.ValidationResponse;
import com.hopeful117.devlogai.validation.entity.ValidationDecision;
import com.hopeful117.devlogai.validation.service.ValidationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidationControllerWebMvcTest extends ControllerWebMvcTestSupport {

    @Test
    void shouldExposeAllValidationRoutes() throws Exception {
        ValidationService service = mock(ValidationService.class);
        MockMvc mvc = mockMvc(new ValidationController(service));
        UUID id = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        UUID validatorId = UUID.randomUUID();
        ValidationResponse response = new ValidationResponse(id, proposalId,
                ValidationDecision.ACCEPTED, null, validatorId, "ok");
        when(service.validate(any())).thenReturn(response);
        when(service.getById(id)).thenReturn(response);
        when(service.getByProposalId(proposalId)).thenReturn(response);

        mvc.perform(post("/api/v1/validations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"proposalId\":\"%s\",\"decision\":\"ACCEPTED\",\"comment\":\"ok\",\"validatedBy\":\"%s\"}"
                                .formatted(proposalId, validatorId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.decision").value("ACCEPTED"));
        mvc.perform(get("/api/v1/validations/{id}", id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/validations/proposal/{id}", proposalId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proposalId").value(proposalId.toString()));
    }
}
