package com.hopeful117.devlogai.repositorycontext.selection;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.intelligence.EvidencePrecisionPolicy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class BudgetedDiverseEvidenceSelector implements EvidenceSelector {

    private static final Set<String> KNOWLEDGE_KINDS = Set.of(
            "INSIGHT", "ENGINEERING_STORY", "DECISION", "ARTIFACT",
            "MILESTONE", "CHALLENGE", "ENGINEERING_EVENT", "FACT",
            "OBSERVATION");

    @Override
    public SelectionResult select(List<RepositoryEvidence> ranked, ContextRequest request) {
        Deduplication deduplication = deduplicate(ranked);
        List<RepositoryEvidence> candidates = deduplication.unique();
        EvidencePrecisionPolicy policy = request.contextPlan().precisionPolicy();
        SelectionState state = new SelectionState(kindAllowance(candidates, request, policy));

        selectDiverseEvidence(candidates, request, policy, state);
        selectKnowledgeFloor(candidates, request, policy, state);
        for (RepositoryEvidence candidate : candidates)
            if (!state.selectedReferences.contains(candidate.reference()))
                selectOrdinary(candidate, request, policy, state);

        List<RepositoryContext.SelectionDecision> decisions = new ArrayList<>();
        candidates.forEach(candidate -> decisions.add(decision(candidate, state)));
        deduplication.duplicates().forEach(candidate -> decisions.add(
                new RepositoryContext.SelectionDecision(candidate.reference(), false,
                        "DUPLICATE_REFERENCE", candidate.relevanceScore(),
                        candidate.estimatedTokens())));
        return new SelectionResult(state.selected, decisions, state.usedTokens);
    }

    private void selectDiverseEvidence(List<RepositoryEvidence> candidates,
            ContextRequest request, EvidencePrecisionPolicy policy, SelectionState state) {
        Set<RepositoryContextLayer> represented = new HashSet<>();
        for (RepositoryContextLayer preferred : request.contextPlan().preferredLayers()) {
            if (represented.size() >= request.contextPlan().minimumDiverseLayers()) break;
            RepositoryEvidence candidate = candidates.stream()
                    .filter(value -> value.layer() == preferred)
                    .filter(value -> !state.selectedReferences.contains(value.reference()))
                    .filter(value -> value.relevanceScore() >= policy.minimumRelevanceScore())
                    .filter(value -> categoryEligible(value, policy, state))
                    .filter(value -> fits(value, state, request))
                    .findFirst().orElse(null);
            if (candidate != null) {
                add(candidate, "SELECTED_BY_DIVERSITY", state);
                represented.add(candidate.layer());
            }
        }
    }

    /**
     * Availability-aware knowledge floors (ADR-063 category-aware
     * composition): abundant Git evidence must not starve trusted/project
     * knowledge. Reserves at most ~10% of the item budget (clamped to
     * [2,8]) for knowledge-kind candidates that clear every existing gate
     * (relevance, kind share, item and token budgets). Fewer eligible
     * candidates leave the unused capacity to the ordinary rank pass;
     * irrelevant knowledge is never selected merely to satisfy a floor.
     */
    private void selectKnowledgeFloor(List<RepositoryEvidence> candidates,
            ContextRequest request, EvidencePrecisionPolicy policy,
            SelectionState state) {
        int floor = Math.max(2, Math.min(8,
                request.budget().maximumEvidenceItems() / 10));
        int reserved = 0;
        for (RepositoryEvidence candidate : candidates) {
            if (reserved >= floor) return;
            if (!KNOWLEDGE_KINDS.contains(candidate.kind())) continue;
            if (state.selectedReferences.contains(candidate.reference())) continue;
            if (candidate.relevanceScore() < policy.minimumRelevanceScore()) continue;
            if (state.kindCounts.getOrDefault(candidate.kind(), 0)
                    >= state.kindAllowance
                    && candidate.relevanceScore() < policy.strongRelevanceScore())
                continue;
            if (state.selected.size() >= request.budget().maximumEvidenceItems())
                return;
            if (state.usedTokens + candidate.estimatedTokens()
                    > request.budget().maximumTokens()) return;
            add(candidate, "SELECTED_BY_CATEGORY_FLOOR", state);
            reserved++;
        }
    }

    private void selectOrdinary(RepositoryEvidence candidate, ContextRequest request,
            EvidencePrecisionPolicy policy, SelectionState state) {
        if (candidate.relevanceScore() < policy.minimumRelevanceScore()) {
            state.reasons.put(candidate.reference(), "INSUFFICIENT_RELEVANCE");
            return;
        }
        if (!categoryEligible(candidate, policy, state)) {
            state.reasons.put(candidate.reference(), "CATEGORY_CONCENTRATION_LIMIT");
            return;
        }
        if (state.selected.size() >= request.budget().maximumEvidenceItems()) {
            state.reasons.put(candidate.reference(), "EVIDENCE_ITEM_BUDGET_EXCEEDED");
            return;
        }
        if (state.usedTokens + candidate.estimatedTokens()
                > request.budget().maximumTokens()) {
            state.reasons.put(candidate.reference(), "TOKEN_BUDGET_EXCEEDED");
            return;
        }
        boolean overflow = state.kindCounts.getOrDefault(candidate.kind(), 0)
                >= state.kindAllowance;
        add(candidate, overflow ? "SELECTED_BY_STRONG_RELEVANCE"
                : "SELECTED_BY_RANK", state);
    }

    private boolean categoryEligible(RepositoryEvidence candidate,
            EvidencePrecisionPolicy policy, SelectionState state) {
        return state.kindCounts.getOrDefault(candidate.kind(), 0) < state.kindAllowance
                || candidate.relevanceScore() >= policy.strongRelevanceScore();
    }

    private boolean fits(RepositoryEvidence candidate, SelectionState state,
            ContextRequest request) {
        return state.selected.size() < request.budget().maximumEvidenceItems()
                && state.usedTokens + candidate.estimatedTokens()
                <= request.budget().maximumTokens();
    }

    private void add(RepositoryEvidence candidate, String reason, SelectionState state) {
        state.selected.add(candidate);
        state.selectedReferences.add(candidate.reference());
        state.kindCounts.merge(candidate.kind(), 1, Integer::sum);
        state.usedTokens += candidate.estimatedTokens();
        state.reasons.put(candidate.reference(), reason);
    }

    private RepositoryContext.SelectionDecision decision(
            RepositoryEvidence candidate, SelectionState state) {
        return new RepositoryContext.SelectionDecision(candidate.reference(),
                state.selectedReferences.contains(candidate.reference()),
                state.reasons.getOrDefault(candidate.reference(), "NOT_SELECTED"),
                candidate.relevanceScore(), candidate.estimatedTokens());
    }

    private int kindAllowance(List<RepositoryEvidence> candidates, ContextRequest request,
            EvidencePrecisionPolicy policy) {
        int capacity = Math.min(candidates.size(), request.budget().maximumEvidenceItems());
        if (capacity == 0) return 0;
        return Math.max(1, (int) Math.ceil(
                capacity * policy.maximumKindSharePercentage() / 100.0));
    }

    private Deduplication deduplicate(List<RepositoryEvidence> ranked) {
        Map<String, RepositoryEvidence> unique = new LinkedHashMap<>();
        List<RepositoryEvidence> duplicates = new ArrayList<>();
        ranked.forEach(value -> {
            if (unique.putIfAbsent(value.reference(), value) != null)
                duplicates.add(value);
        });
        return new Deduplication(List.copyOf(unique.values()), List.copyOf(duplicates));
    }

    private record Deduplication(
            List<RepositoryEvidence> unique,
            List<RepositoryEvidence> duplicates
    ) {
    }

    private static final class SelectionState {
        private final int kindAllowance;
        private final List<RepositoryEvidence> selected = new ArrayList<>();
        private final Set<String> selectedReferences = new HashSet<>();
        private final Map<String, Integer> kindCounts = new HashMap<>();
        private final Map<String, String> reasons = new HashMap<>();
        private int usedTokens;

        private SelectionState(int kindAllowance) {
            this.kindAllowance = kindAllowance;
        }
    }
}
