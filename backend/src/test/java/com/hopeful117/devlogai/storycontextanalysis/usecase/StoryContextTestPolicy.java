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
                .minimumOutputTokens(complexity.name().equals("COMPLEX") ? 4_096 : 256)
                .targetOutputTokens(complexity.name().equals("COMPLEX") ? 8_192 : 4_096)
                .maximumOutputTokens(32_768)
                .contextCapacityTokens(1_048_576)
                .measurementCertainty("TEST_FIXTURE")
                .status("ACTIVE")
                .createdAt(Instant.EPOCH)
                .build());
    }
}
