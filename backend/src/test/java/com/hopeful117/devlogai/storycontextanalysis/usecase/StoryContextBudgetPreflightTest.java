package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.aicatalog.service.AiProviderCatalogService;
import com.hopeful117.devlogai.ai.engine.dto.PromptRequest;
import com.hopeful117.devlogai.ai.task.entity.AiTaskType;
import com.hopeful117.devlogai.intent.model.IntentDefinition;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryContextBudgetPreflightTest {
    private final StoryContextBudgetPreflight preflight = new StoryContextBudgetPreflight(
            new AiProviderCatalogService(), new ObjectMapper(), StoryContextTestPolicy.resolver());

    @Test
    void acceptsDocumentedModelCapacity() {
        var result = preflight.check(prompt("openai", "gpt-5.6-luna", Map.of("context", "small"),
                Map.of("maxOutputTokens", 4_096)));

        assertTrue(result.accepted());
        assertEquals("AUTHORIZED", result.outcome());
        assertEquals(1_048_576, result.capacity());
        assertTrue(result.inputEstimate() > 0);
    }

    @Test
    void resolvesEngineChatGptProviderToCatalogCapacity() {
        var result = preflight.check(prompt("chatgpt", "gpt-5.6-luna", Map.of(),
                Map.of("maxOutputTokens", 4_096)));

        assertTrue(result.accepted());
        assertEquals(1_048_576, result.capacity());
    }

    @Test
    void recordsCoreProjectionReductionWithoutCreatingAnotherSelection() {
        var projection = Map.of(
                "accounting", Map.of("truncated", true),
                "context", "already-authorized");

        var result = preflight.check(prompt("openai", "gpt-5.6-luna", projection,
                Map.of("maxOutputTokens", 4_096)));

        assertTrue(result.accepted());
        assertEquals("PROJECTION_REDUCED", result.outcome());
    }

    @Test
    void rejectsOutputAllowanceBeforeProviderInvocation() {
        var constrained = new StoryContextBudgetPreflight(new AiProviderCatalogService() {
            @Override
            public Integer contextCapacityTokens(String providerId, String modelIdentifier) {
                return 5_000;
            }
        }, new ObjectMapper(), StoryContextTestPolicy.resolver());
        var result = constrained.check(prompt(
                "openai", "gpt-5.6-luna", Map.of("context", "x"), Map.of("maxOutputTokens", 6_000)));

        assertTrue(!result.accepted());
         assertEquals("BUDGET_OUTPUT_ALLOWANCE_INSUFFICIENT", result.code());
        assertEquals("REJECTED", result.outcome());
    }

    @Test
    void rejectsUnknownModelCapacityBeforeProviderInvocation() {
        var result = preflight.check(prompt("openrouter", "provider/model", Map.of(), Map.of()));

        assertTrue(!result.accepted());
        assertEquals("BUDGET_CAPACITY_UNKNOWN", result.code());
    }

    @Test
    void derivesMissingOutputAllowanceFromTheCorePolicy() {
        var result = preflight.check(prompt("openai", "gpt-5.6-luna", Map.of(), Map.of()));

        assertTrue(result.accepted());
        assertEquals(4_096, result.outputLimit());
        assertEquals("CORE_POLICY", result.outputLimitSource());
        assertEquals(Map.of("maxOutputTokens", 4_096), result.effectiveParameters());
    }

    @Test
    void rejectsInvalidOutputAllowanceWithoutUsingTheDefault() {
        var result = preflight.check(prompt(
                "openai", "gpt-5.6-luna", Map.of(), Map.of("maxOutputTokens", 12.5)));

        assertTrue(!result.accepted());
        assertEquals("BUDGET_OUTPUT_ALLOWANCE_INVALID", result.code());
        assertEquals("REJECTED", result.outcome());
    }

    @Test
    void rejectsIntegralFloatingPointOutputAllowance() {
        var result = preflight.check(prompt(
                "openai", "gpt-5.6-luna", Map.of(), Map.of("maxOutputTokens", 4_096.0)));

        assertTrue(!result.accepted());
        assertEquals("BUDGET_OUTPUT_ALLOWANCE_INVALID", result.code());
    }

    @Test
    void rejectsComplexStoryContextBelowCompletionAllowanceBeforeProviderInvocation() {
        var result = preflight.check(prompt(
                "openai", "gpt-5.6-luna", Map.of(), Map.of("maxOutputTokens", 2_000),
                Map.of("requiredAnalysisFields", List.of(
                        "objectiveUnderstanding", "architectureFindings", "decisionFindings",
                        "evidenceFindings", "historicalContext", "constraintFindings",
                        "impactedComponentFindings", "uncertainties", "missingInformation",
                        "implementationQuestions", "implementationPreparation", "confidence"))));

        assertTrue(!result.accepted());
        assertEquals("BUDGET_COMPLETION_ALLOWANCE_INSUFFICIENT", result.code());
        assertEquals(4_096, result.requiredOutputLimit());
    }

    private static PromptRequest prompt(String provider, String model,
                                        Map<String, Object> projection, Map<String, Object> parameters) {
        return prompt(provider, model, projection, parameters, Map.of("type", "object"));
    }

    private static PromptRequest prompt(String provider, String model,
                                        Map<String, Object> projection, Map<String, Object> parameters,
                                        Map<String, Object> outputSchema) {
        IntentDefinition intent = new IntentDefinition(
                "engineering-story-context-analysis", "v1", "Analyze context", List.of(),
                List.of(), outputSchema, "story-context-analysis-prompt-v1");
        return new PromptRequest(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                AiTaskType.STORY_CONTEXT_ANALYSIS, intent, null, projection, intent.outputSchema(),
                Map.of(), Map.of(), "a".repeat(64), null, "b".repeat(64), null, provider, model, parameters);
    }
}
