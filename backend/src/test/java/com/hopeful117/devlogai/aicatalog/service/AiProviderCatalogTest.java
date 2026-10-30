package com.hopeful117.devlogai.aicatalog.service;

import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicatalog.model.AiProviderCatalogEntry;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModel;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModelAvailability;
import com.hopeful117.devlogai.aicatalog.model.AiCatalogLifecycleStatus;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiProviderCatalogTest {
    private final AiProviderCatalogService catalog = new AiProviderCatalogService();

    @Test
    void shouldExposeAllSupportedProvidersInStableOrder() {
        assertEquals(List.of("mock", "openai", "openai_chatgpt", "gemini", "anthropic", "openrouter"),
                catalog.all().stream().map(entry -> entry.providerId()).toList());
        assertEquals("ai-provider-catalog-v1", catalog.catalogVersion());
    }

    @Test
    void shouldExposeVerticalSliceAuthenticationMethodsAndRecommendations() {
        var entries = catalog.all();

        var mock = entries.get(0);
        assertEquals(List.of(AiAuthenticationMethod.NONE), mock.authenticationMethods());
        assertEquals("deterministic-v1", mock.recommendedModel());

        var chatgpt = entries.get(2);
        assertEquals(List.of(AiAuthenticationMethod.OAUTH), chatgpt.authenticationMethods());
        assertEquals("gpt-5.6-luna", chatgpt.recommendedModel());

        var anthropic = entries.get(4);
        assertEquals(List.of(AiAuthenticationMethod.API_KEY), anthropic.authenticationMethods());
        assertTrue(anthropic.models().isEmpty());
        assertNull(anthropic.recommendedModel());
    }

    @Test
    void shouldKeepCatalogCollectionsImmutable() {
        var providers = catalog.all();

        assertThrows(UnsupportedOperationException.class, () -> providers.clear());
        assertThrows(UnsupportedOperationException.class,
                () -> providers.getFirst().authenticationMethods().add(AiAuthenticationMethod.API_KEY));
    }

    @Test
    void shouldScopeKnownModelsByProvider() {
        var openai = catalog.all().get(1);
        var chatgpt = catalog.all().get(2);

        assertEquals("gpt-5.6-luna", openai.models().getFirst().identifier());
        assertEquals("gpt-5.6-luna", chatgpt.models().getFirst().identifier());
        assertEquals("openai", openai.providerId());
        assertEquals("openai_chatgpt", chatgpt.providerId());
    }

    @Test
    void shouldRejectUnknownProviderAndModel() {
        assertThrows(EntityNotFoundException.class, () -> catalog.resolve("unknown"));
        assertThrows(EntityNotFoundException.class,
                () -> catalog.resolveModel("openai", "unknown-model"));
    }

    @Test
    void shouldRepresentUnavailableRecommendationWithoutASelection() {
        var anthropic = catalog.resolve("anthropic");

        assertTrue(anthropic.models().isEmpty());
        assertNull(anthropic.recommendedModel());
    }

    @Test
    void shouldRepresentKnownUnavailableModelWithoutRejectingItsIdentifier() {
        var unavailable = new AiProviderModel(
                "known-model", "Known model", List.of("STRUCTURED_OUTPUT"),
                AiProviderModelAvailability.UNAVAILABLE);
        var provider = new AiProviderCatalogEntry(
                "test-provider", "Test provider", List.of(AiAuthenticationMethod.API_KEY),
                List.of(unavailable), null, List.of("STRUCTURED_OUTPUT"),
                AiCatalogLifecycleStatus.EXPERIMENTAL);

        assertEquals(AiProviderModelAvailability.UNAVAILABLE,
                provider.models().getFirst().availability());
        assertEquals("known-model", provider.models().getFirst().identifier());
    }

    @Test
    void shouldRejectInvalidModelDefinitions() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderModel(" ", "Model", List.of(),
                        AiProviderModelAvailability.AVAILABLE));

        var first = new AiProviderModel("duplicate", "First", List.of(),
                AiProviderModelAvailability.AVAILABLE);
        var second = new AiProviderModel("duplicate", "Second", List.of(),
                AiProviderModelAvailability.AVAILABLE);
        assertThrows(IllegalArgumentException.class,
                () -> new AiProviderCatalogEntry(
                        "test-provider", "Test provider", List.of(AiAuthenticationMethod.NONE),
                List.of(first, second), null, List.of(), AiCatalogLifecycleStatus.STABLE));
    }

    @Test
    void shouldProjectKnownUnavailableModelThroughTheCatalogService() {
        var unavailable = new AiProviderModel(
                "known-model", "Known model", List.of("STRUCTURED_OUTPUT"),
                AiProviderModelAvailability.UNAVAILABLE);
        var provider = new AiProviderCatalogEntry(
                "test-provider", "Test provider", List.of(AiAuthenticationMethod.API_KEY),
                List.of(unavailable), null, List.of("STRUCTURED_OUTPUT"),
                AiCatalogLifecycleStatus.EXPERIMENTAL);
        var projectedCatalog = new AiProviderCatalogService(Map.of("test-provider", provider));

        var projectedModel = projectedCatalog.resolveModel("test-provider", "known-model");
        assertEquals(AiProviderModelAvailability.UNAVAILABLE, projectedModel.availability());
        assertEquals("known-model", projectedModel.identifier());
    }
}
