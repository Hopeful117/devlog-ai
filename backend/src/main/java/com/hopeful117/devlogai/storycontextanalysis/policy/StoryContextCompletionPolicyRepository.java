package com.hopeful117.devlogai.storycontextanalysis.policy;

import org.springframework.data.repository.Repository;
import org.springframework.data.repository.RepositoryDefinition;

import java.util.Optional;
import java.util.UUID;

@RepositoryDefinition(domainClass = StoryContextCompletionPolicy.class, idClass = UUID.class)
public interface StoryContextCompletionPolicyRepository extends Repository<StoryContextCompletionPolicy, UUID> {
    Optional<StoryContextCompletionPolicy> findByProviderAndModelIdentifierAndIntentIdAndComplexityClassAndStatus(
            String provider, String modelIdentifier, String intentId,
            StoryContextComplexityClass complexityClass, String status);
}
