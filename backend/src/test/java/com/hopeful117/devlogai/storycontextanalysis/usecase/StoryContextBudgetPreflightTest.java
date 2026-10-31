package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryContextBudgetPreflightTest {
    private final StoryContextBudgetPreflight preflight = new StoryContextBudgetPreflight(
            new AiProviderCatalogService(), new ObjectMapper());

    @Test
    void acceptsDocumentedModelCapacity() {
        var result = preflight.check("openai", "gpt-5.6-luna", Map.of("context", "small"), Map.of());

        assertTrue(result.accepted());
        assertEquals(1_048_576, result.capacity());
        assertTrue(result.inputEstimate() > 0);
    }

    @Test
    void rejectsUnknownModelCapacityBeforeProviderInvocation() {
        var result = preflight.check("openrouter", "provider/model", Map.of(), Map.of());

        assertTrue(!result.accepted());
        assertEquals("BUDGET_CAPACITY_UNKNOWN", result.code());
    }
}
