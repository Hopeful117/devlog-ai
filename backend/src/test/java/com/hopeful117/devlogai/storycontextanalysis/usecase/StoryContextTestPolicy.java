package com.hopeful117.devlogai.storycontextanalysis.usecase;

import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextCompletionPolicy;
import com.hopeful117.devlogai.storycontextanalysis.policy.StoryContextCompletionPolicyResolver;

import java.time.Instant;
import java.util.Optional;

final class StoryContextTestPolicy {
    private StoryContextTestPolicy() {
    }

    static StoryContextCompletionPolicyResolver resolver() {
        return (provider, model, intent, complexity) -> Optional.of(StoryContextCompletionPolicy.builder()
                .policyVersion("story-context-completion-v1")
                .provider(provider)
                .modelIdentifier(model)
                .intentId(intent)
                .complexityClass(complexity)
                .minimumOutputTokens(switch (complexity.name()) {
                    case "REDUCED" -> 2_048;
                    case "STANDARD" -> 4_096;
                    case "COMPLEX" -> 8_192;
                    default -> throw new IllegalArgumentException("Unsupported complexity: " + complexity);
                })
                .targetOutputTokens(switch (complexity.name()) {
                    case "REDUCED", "COMPLEX" -> 8_192;
                    case "STANDARD" -> 6_000;
                    default -> throw new IllegalArgumentException("Unsupported complexity: " + complexity);
                })
                .maximumOutputTokens(switch (complexity.name()) {
                    case "REDUCED" -> 8_192;
                    case "STANDARD" -> 16_384;
                    case "COMPLEX" -> 32_768;
                    default -> throw new IllegalArgumentException("Unsupported complexity: " + complexity);
                })
                .contextCapacityTokens(1_048_576)
                .measurementCertainty("TEST_FIXTURE")
                .status("ACTIVE")
                .createdAt(Instant.EPOCH)
                .build());
    }
}
