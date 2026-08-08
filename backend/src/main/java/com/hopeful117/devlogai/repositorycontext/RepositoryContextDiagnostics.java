package com.hopeful117.devlogai.repositorycontext;

import java.util.List;
import java.util.Map;

public record RepositoryContextDiagnostics(
        Map<RepositoryContextLayer, Integer> candidatesByLayer,
        Map<String, Integer> candidatesByKind,
        Map<String, Integer> selectedByKind,
        List<PreferredLayerAvailability> preferredLayerAvailability,
        int uniqueCandidateCount,
        int duplicateCandidateCount
) {
    public RepositoryContextDiagnostics {
        candidatesByLayer = Map.copyOf(candidatesByLayer);
        candidatesByKind = Map.copyOf(candidatesByKind);
        selectedByKind = Map.copyOf(selectedByKind);
        preferredLayerAvailability = List.copyOf(preferredLayerAvailability);
    }

    public static RepositoryContextDiagnostics empty() {
        return new RepositoryContextDiagnostics(Map.of(), Map.of(), Map.of(),
                List.of(), 0, 0);
    }

    public record PreferredLayerAvailability(
            RepositoryContextLayer layer,
            boolean candidateAvailable,
            String reason
    ) {
    }
}
