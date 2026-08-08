package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class SelectedContentAllocationPolicy {
    public static final String POLICY_ID = "selected-content-allocation";
    public static final String POLICY_VERSION = "v1";

    public List<Allocation> allocate(List<RepositoryEvidence> eligible) {
        List<RepositoryEvidence> ordered = eligible.stream()
                .sorted(Comparator.comparingInt(RepositoryEvidence::relevanceScore).reversed()
                        .thenComparing(Comparator.comparingInt(
                                (RepositoryEvidence value) ->
                                        value.score().matchStrength().semantic()).reversed())
                        .thenComparing(Comparator.comparingInt(
                                (RepositoryEvidence value) ->
                                        value.score().matchStrength().guidance()).reversed())
                        .thenComparing(RepositoryEvidence::reference))
                .toList();
        List<Allocation> result = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            RepositoryEvidence value = ordered.get(index);
            result.add(new Allocation(value, index + 1, List.of(
                    "FINAL_SCORE=" + value.relevanceScore(),
                    "SEMANTIC_MATCH_STRENGTH="
                            + value.score().matchStrength().semantic(),
                    "GUIDANCE_MATCH_STRENGTH="
                            + value.score().matchStrength().guidance())));
        }
        return List.copyOf(result);
    }

    public record Allocation(
            RepositoryEvidence evidence,
            int rank,
            List<String> reasons
    ) {
        public Allocation {
            reasons = List.copyOf(reasons);
        }
    }
}
