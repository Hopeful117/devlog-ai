package com.hopeful117.devlogai.aicatalog.service;

import com.hopeful117.devlogai.aicatalog.model.AiAuthenticationMethod;
import com.hopeful117.devlogai.aicatalog.model.AiCatalogLifecycleStatus;
import com.hopeful117.devlogai.aicatalog.model.AiProviderCatalogEntry;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModel;
import com.hopeful117.devlogai.aicatalog.model.AiProviderModelAvailability;
import com.hopeful117.devlogai.aicatalog.model.AiParameterDescriptor;
import com.hopeful117.devlogai.shared.exception.EntityNotFoundException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiProviderCatalogService {
    public static final String CATALOG_VERSION = "ai-provider-catalog-v1";
    private static final String STRUCTURED_OUTPUT = "STRUCTURED_OUTPUT";
    private static final Map<String, AiProviderCatalogEntry> DEFAULT_PROVIDERS = catalog();
    public static final String MAX_OUTPUT_TOKENS = "maxOutputTokens";
    public static final int MIN_MAX_OUTPUT_TOKENS = 256;
    public static final int MAX_MAX_OUTPUT_TOKENS = 32_768;
    private final Map<String, AiProviderCatalogEntry> providers;

    public AiProviderCatalogService() {
        this(DEFAULT_PROVIDERS);
    }

    AiProviderCatalogService(Map<String, AiProviderCatalogEntry> providers) {
        this.providers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(providers));
    }

    public List<AiProviderCatalogEntry> all() {
        return List.copyOf(providers.values());
    }

    public AiProviderCatalogEntry resolve(String providerId) {
        var provider = providers.get(catalogProviderId(providerId));
        if (provider == null) {
            throw new EntityNotFoundException("AI provider", providerId);
        }
        return provider;
    }

    private String catalogProviderId(String providerId) {
        return "chatgpt".equals(providerId) ? "openai_chatgpt" : providerId;
    }

    public AiProviderModel resolveModel(String providerId, String modelIdentifier) {
        var provider = resolve(providerId);
        return provider.models().stream()
                .filter(model -> model.identifier().equals(modelIdentifier))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException(
                        "AI model", providerId + ":" + modelIdentifier));
    }

    public Integer contextCapacityTokens(String providerId, String modelIdentifier) {
        return resolveModel(providerId, modelIdentifier).contextCapacityTokens();
    }

    public String catalogVersion() {
        return CATALOG_VERSION;
    }

    private static Map<String, AiProviderCatalogEntry> catalog() {
        Map<String, AiProviderCatalogEntry> result = new LinkedHashMap<>();
        register(result, provider(
                "mock", "Mock", List.of(AiAuthenticationMethod.NONE),
                 List.of(model("deterministic-v1", "Deterministic v1", 1_048_576)),
                "deterministic-v1", AiCatalogLifecycleStatus.STABLE));
        register(result, provider(
                "openai", "OpenAI", List.of(AiAuthenticationMethod.API_KEY),
                 List.of(model("gpt-5.6-luna", "GPT-5.6 Luna", 1_048_576)),
                "gpt-5.6-luna", AiCatalogLifecycleStatus.STABLE));
        register(result, provider(
                "openai_chatgpt", "ChatGPT", List.of(AiAuthenticationMethod.OAUTH),
                 List.of(model("gpt-5.6-luna", "GPT-5.6 Luna", 1_048_576)),
                "gpt-5.6-luna", AiCatalogLifecycleStatus.EXPERIMENTAL));
        register(result, provider(
                "gemini", "Gemini", List.of(AiAuthenticationMethod.API_KEY),
                List.of(), null, AiCatalogLifecycleStatus.EXPERIMENTAL));
        register(result, provider(
                "anthropic", "Anthropic", List.of(AiAuthenticationMethod.API_KEY),
                 List.of(model("claude-opus-5-5", "Claude Opus 5.5", 1_000_000)),
                "claude-opus-5-5", AiCatalogLifecycleStatus.STABLE));
        register(result, provider(
                "openrouter", "OpenRouter", List.of(AiAuthenticationMethod.API_KEY),
                List.of(), null, AiCatalogLifecycleStatus.EXPERIMENTAL));
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static AiProviderCatalogEntry provider(
            String providerId,
            String displayName,
            List<AiAuthenticationMethod> authenticationMethods,
            List<AiProviderModel> models,
            String recommendedModel,
            AiCatalogLifecycleStatus lifecycleStatus) {
        return new AiProviderCatalogEntry(
                providerId,
                displayName,
                authenticationMethods,
                models,
                recommendedModel,
                List.of(STRUCTURED_OUTPUT),
                lifecycleStatus,
                models.isEmpty() ? List.of() : List.of(new AiParameterDescriptor(
                        MAX_OUTPUT_TOKENS, "Maximum output tokens", "integer",
                        MIN_MAX_OUTPUT_TOKENS, MAX_MAX_OUTPUT_TOKENS, 2_000)));
    }

    private static AiProviderModel model(String identifier, String displayName, int contextCapacityTokens) {
        return new AiProviderModel(identifier, displayName, List.of(STRUCTURED_OUTPUT),
                AiProviderModelAvailability.AVAILABLE, contextCapacityTokens);
    }

    private static void register(Map<String, AiProviderCatalogEntry> catalog,
                                 AiProviderCatalogEntry entry) {
        if (catalog.put(entry.providerId(), entry) != null) {
            throw new IllegalArgumentException("Duplicate AI provider in catalog: " + entry.providerId());
        }
    }
}
