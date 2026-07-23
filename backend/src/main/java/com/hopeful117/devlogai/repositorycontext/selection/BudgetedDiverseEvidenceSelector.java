package com.hopeful117.devlogai.repositorycontext.selection;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Component
public class BudgetedDiverseEvidenceSelector implements EvidenceSelector {
    @Override
    public SelectionResult select(
            List<RepositoryEvidence> ranked,
            ContextRequest request
    ) {
        List<RepositoryEvidence> deduplicated = deduplicate(ranked);
        List<RepositoryEvidence> selected = new ArrayList<>();
        Set<String> selectedReferences = new HashSet<>();
        int usedTokens = 0;

        // First pass preserves layer diversity before filling remaining budget by rank.
        Set<RepositoryContextLayer> represented = new HashSet<>();
        for (RepositoryEvidence candidate : deduplicated) {
            if (represented.contains(candidate.layer())) continue;
            if (fits(candidate, selected.size(), usedTokens, request)) {
                selected.add(candidate);
                selectedReferences.add(candidate.reference());
                represented.add(candidate.layer());
                usedTokens += candidate.estimatedTokens();
            }
        }
        for (RepositoryEvidence candidate : deduplicated) {
            if (selectedReferences.contains(candidate.reference())) continue;
            if (fits(candidate, selected.size(), usedTokens, request)) {
                selected.add(candidate);
                selectedReferences.add(candidate.reference());
                usedTokens += candidate.estimatedTokens();
            }
        }
        int finalUsedTokens = usedTokens;
        int finalSelectedCount = selected.size();
        List<RepositoryContext.SelectionDecision> decisions = deduplicated.stream()
                .map(value -> new RepositoryContext.SelectionDecision(
                        value.reference(), selectedReferences.contains(value.reference()),
                        selectedReferences.contains(value.reference())
                                ? "SELECTED_BY_RANK_AND_DIVERSITY"
                                : exclusionReason(value, finalSelectedCount,
                                        finalUsedTokens, request),
                        value.relevanceScore(), value.estimatedTokens()))
                .toList();
        return new SelectionResult(selected, decisions, usedTokens);
    }

    private List<RepositoryEvidence> deduplicate(List<RepositoryEvidence> ranked) {
        var unique = new LinkedHashMap<String, RepositoryEvidence>();
        ranked.forEach(value -> unique.putIfAbsent(value.reference(), value));
        return List.copyOf(unique.values());
    }

    private boolean fits(
            RepositoryEvidence candidate,
            int selectedCount,
            int usedTokens,
            ContextRequest request
    ) {
        return selectedCount < request.budget().maximumEvidenceItems()
                && usedTokens + candidate.estimatedTokens()
                <= request.budget().maximumTokens();
    }

    private String exclusionReason(
            RepositoryEvidence candidate,
            int selectedCount,
            int usedTokens,
            ContextRequest request
    ) {
        if (selectedCount >= request.budget().maximumEvidenceItems())
            return "EVIDENCE_ITEM_BUDGET_EXCEEDED";
        if (usedTokens + candidate.estimatedTokens() > request.budget().maximumTokens())
            return "TOKEN_BUDGET_EXCEEDED";
        return "DUPLICATE_REFERENCE";
    }
}
