package com.hopeful117.devlogai.storycontextanalysis.policy;

import java.util.Optional;

@FunctionalInterface
public interface StoryContextCompletionPolicyResolver {
    Optional<StoryContextCompletionPolicy> resolve(
            String provider, String model, String intent, StoryContextComplexityClass complexityClass);
}
