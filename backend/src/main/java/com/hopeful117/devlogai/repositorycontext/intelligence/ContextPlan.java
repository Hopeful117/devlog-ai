package com.hopeful117.devlogai.repositorycontext.intelligence;

import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;

import java.util.List;
import java.util.Map;

public record ContextPlan(
        String planVersion,
        List<ContextProfileDefinition> profiles,
        Map<EvidenceCriterion, Integer> composedWeights,
        List<RepositoryContextLayer> preferredLayers,
        int minimumDiverseLayers,
        List<String> explanations
) {
    public ContextPlan {
        profiles = List.copyOf(profiles);
        composedWeights = Map.copyOf(composedWeights);
        preferredLayers = List.copyOf(preferredLayers);
        explanations = List.copyOf(explanations);
    }

    public ContextProfile primaryProfile() {
        return profiles.getFirst().profile();
    }

    public List<String> profileKeys() {
        return profiles.stream().map(ContextProfileDefinition::key).toList();
    }
}
