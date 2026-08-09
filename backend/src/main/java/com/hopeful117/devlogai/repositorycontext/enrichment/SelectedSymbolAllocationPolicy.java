package com.hopeful117.devlogai.repositorycontext.enrichment;

import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class SelectedSymbolAllocationPolicy {
    public static final String POLICY_ID = "selected-symbol-allocation";
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
        return java.util.stream.IntStream.range(0, ordered.size())
                .mapToObj(index -> new Allocation(ordered.get(index), index + 1,
                        List.of("FINAL_SCORE=" + ordered.get(index).relevanceScore(),
                                "SEMANTIC_MATCH_STRENGTH="
                                        + ordered.get(index).score().matchStrength().semantic(),
                                "GUIDANCE_MATCH_STRENGTH="
                                        + ordered.get(index).score().matchStrength().guidance())))
                .toList();
    }

    public record Allocation(
            RepositoryEvidence evidence,
            int rank,
            List<String> reasons
    ) {
        public Allocation { reasons = List.copyOf(reasons); }
    }
}
