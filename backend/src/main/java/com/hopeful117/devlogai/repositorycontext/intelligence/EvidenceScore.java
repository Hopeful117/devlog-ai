package com.hopeful117.devlogai.repositorycontext.intelligence;

import java.util.List;
import java.util.Map;

public record EvidenceScore(
        String policyVersion,
        Map<EvidenceCriterion, Integer> criteria,
        Map<EvidenceCriterion, Integer> weights,
        int finalScore,
        List<String> explanations
) {
    public EvidenceScore {
        criteria = Map.copyOf(criteria);
        weights = Map.copyOf(weights);
        explanations = List.copyOf(explanations);
        if (criteria.values().stream().anyMatch(value -> value < 0 || value > 100))
            throw new IllegalArgumentException("Evidence criteria must be between 0 and 100");
        if (finalScore < 0 || finalScore > 100)
            throw new IllegalArgumentException("Final Evidence score must be between 0 and 100");
    }

    public static EvidenceScore unscored() {
        return new EvidenceScore("unscored", Map.of(), Map.of(), 0,
                List.of("NOT_YET_RANKED"));
    }
}
