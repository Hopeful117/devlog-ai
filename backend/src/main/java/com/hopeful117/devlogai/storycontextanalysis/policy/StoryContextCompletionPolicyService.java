package com.hopeful117.devlogai.storycontextanalysis.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class StoryContextCompletionPolicyService implements StoryContextCompletionPolicyResolver {
    public static final String ACTIVE = "ACTIVE";
    public static final String POLICY_VERSION = "story-context-completion-v1";

    private final StoryContextCompletionPolicyRepository repository;

    @Transactional(readOnly = true)
    @Override
    public Optional<StoryContextCompletionPolicy> resolve(
            String provider, String model, String intent, StoryContextComplexityClass complexityClass) {
        return repository.findByProviderAndModelIdentifierAndIntentIdAndComplexityClassAndStatus(
                provider, model, intent, complexityClass, ACTIVE)
                .filter(policy -> POLICY_VERSION.equals(policy.getPolicyVersion()));
    }
}
