package com.hopeful117.devlogai.repositorycontext.intelligence;

import com.hopeful117.devlogai.repositorycontext.ContextProfile;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;

import java.util.List;
import java.util.Map;

public record ContextProfileDefinition(
        String key,
        ContextProfile profile,
        String version,
        Map<EvidenceCriterion, Integer> criterionWeights,
        List<RepositoryContextLayer> preferredLayers,
        int minimumDiverseLayers,
        int tokenPriority
) {
    public ContextProfileDefinition {
        criterionWeights = Map.copyOf(criterionWeights);
        preferredLayers = List.copyOf(preferredLayers);
    }
}
