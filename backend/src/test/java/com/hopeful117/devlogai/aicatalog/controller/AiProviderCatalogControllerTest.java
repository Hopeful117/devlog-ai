package com.hopeful117.devlogai.aicatalog.controller;

import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiProviderCatalogControllerTest {
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AiProviderCatalogController(new AiProviderCatalogService()))
            .build();

    @Test
    void shouldExposeReadOnlyProductCatalog() throws Exception {
        mvc.perform(get("/api/v1/ai/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogVersion").value("ai-provider-catalog-v1"))
                .andExpect(jsonPath("$.providers[0].providerId").value("mock"))
                .andExpect(jsonPath("$.providers[0].authenticationMethods[0]").value("NONE"))
                .andExpect(jsonPath("$.providers[2].providerId").value("openai_chatgpt"))
                .andExpect(jsonPath("$.providers[2].authenticationMethods[0]").value("OAUTH"))
                .andExpect(jsonPath("$.providers[4].providerId").value("anthropic"))
                .andExpect(jsonPath("$.providers[4].authenticationMethods[0]").value("API_KEY"));
    }
}
