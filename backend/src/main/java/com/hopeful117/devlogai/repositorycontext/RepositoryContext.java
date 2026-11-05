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
        RepositoryContextDiagnostics diagnostics,
        ContextBudget budget,
        int usedTokens,
        int candidateCount,
        int discardedCount,
        boolean truncated,
        List<SelectionDecision> selectionDecisions,
        List<String> warnings,
        String contextDigest,
        List<CandidateMetadata> candidates
) {
    public RepositoryContext {
        evidence = List.copyOf(evidence);
        activeProfileKeys = List.copyOf(activeProfileKeys);
        contextIntelligenceExplanations =
                List.copyOf(contextIntelligenceExplanations);
        selectedByLayer = Map.copyOf(selectedByLayer);
        if (diagnostics == null) diagnostics = RepositoryContextDiagnostics.empty();
        selectionDecisions = List.copyOf(selectionDecisions);
        warnings = List.copyOf(warnings);
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
    }

    public RepositoryContext(String contextVersion, ContextProfile profile,
            List<String> activeProfileKeys, String contextPlanVersion,
            List<String> contextIntelligenceExplanations,
            List<RepositoryEvidence> evidence,
            Map<RepositoryContextLayer, Integer> selectedByLayer,
            ContextBudget budget, int usedTokens, int candidateCount,
            int discardedCount, boolean truncated,
            List<SelectionDecision> selectionDecisions, List<String> warnings,
            String contextDigest) {
        this(contextVersion, profile, activeProfileKeys, contextPlanVersion,
                contextIntelligenceExplanations, evidence, selectedByLayer,
                RepositoryContextDiagnostics.empty(), budget, usedTokens,
                candidateCount, discardedCount, truncated, selectionDecisions,
                warnings, contextDigest, List.of());
    }

    public RepositoryContext(String contextVersion, ContextProfile profile,
            List<String> activeProfileKeys, String contextPlanVersion,
            List<String> contextIntelligenceExplanations,
            List<RepositoryEvidence> evidence,
            Map<RepositoryContextLayer, Integer> selectedByLayer,
            RepositoryContextDiagnostics diagnostics,
            ContextBudget budget, int usedTokens, int candidateCount,
            int discardedCount, boolean truncated,
            List<SelectionDecision> selectionDecisions, List<String> warnings,
            String contextDigest) {
        this(contextVersion, profile, activeProfileKeys, contextPlanVersion,
                contextIntelligenceExplanations, evidence, selectedByLayer,
                diagnostics, budget, usedTokens, candidateCount, discardedCount,
                truncated, selectionDecisions, warnings, contextDigest, List.of());
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

    public record CandidateMetadata(
            String reference,
            String kind,
            String originatingFile,
            int relevanceScore,
            String sourceType,
            List<String> relatedReferences
    ) {
        public CandidateMetadata {
            relatedReferences = List.copyOf(relatedReferences == null ? List.of() : relatedReferences);
        }

        public CandidateMetadata(String reference, String kind, String originatingFile,
                                 int relevanceScore) {
            this(reference, kind, originatingFile, relevanceScore, null, List.of());
        }
    }
}
