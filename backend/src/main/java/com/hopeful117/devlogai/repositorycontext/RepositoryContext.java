package com.hopeful117.devlogai.repositorycontext;

import java.util.List;
import java.util.Map;

public record RepositoryContext(
        String contextVersion,
        ContextProfile profile,
        List<String> activeProfileKeys,
        String contextPlanVersion,
        List<String> contextIntelligenceExplanations,
        List<RepositoryEvidence> evidence,
        Map<RepositoryContextLayer, Integer> selectedByLayer,
        ContextBudget budget,
        int usedTokens,
        int candidateCount,
        int discardedCount,
        boolean truncated,
        List<SelectionDecision> selectionDecisions,
        List<String> warnings,
        String contextDigest
) {
    public RepositoryContext {
        evidence = List.copyOf(evidence);
        activeProfileKeys = List.copyOf(activeProfileKeys);
        contextIntelligenceExplanations =
                List.copyOf(contextIntelligenceExplanations);
        selectedByLayer = Map.copyOf(selectedByLayer);
        selectionDecisions = List.copyOf(selectionDecisions);
        warnings = List.copyOf(warnings);
    }

    public record ContextBudget(
            int maximumEvidenceItems,
            int maximumSummaryCharacters,
            int maximumHistoryItems,
            int maximumTokens
    ) {
    }

    public record SelectionDecision(
            String evidenceReference,
            boolean selected,
            String reason,
            int relevanceScore,
            int estimatedTokens
    ) {
    }
}
