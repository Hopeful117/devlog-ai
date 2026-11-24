# Story 0234 — Garantir un quota minimal de preuves CORE_KNOWLEDGE dans la sélection avec file scope

## Status

DESIGN_APPROVED

## Context

During the qualitative evaluation of the epic "Fiabiliser les réponses DevLog pour les agents"
(2026-11-24), a focused file scope (2 packages) reduced selected candidates from ~60 (no scope)
to 25–28. At this level, grounding failed with `GROUNDING_MISSING_EVIDENCE` (Config C) or
returned silently empty content (Config B).

Root cause: `BudgetedDiverseEvidenceSelector.selectKnowledgeFloor()` applies
`policy.minimumRelevanceScore()` to CORE_KNOWLEDGE candidates. With a focused file scope,
Insights, Decisions, EngineeringEvents, and Stories score below that threshold and are excluded.
These are exactly the preuves transversales needed to ground a substantive response.

**Finding reference**: Epic evaluation finding F1 — "Effondrement de la sélection avec file scope"

## Governing ADRs

- ADR-063 — Context architecture: selection strategy, evidence diversity
- ADR-069 — Context authority boundary

## Design

### New method `selectCoreKnowledgeFloor()` in `BudgetedDiverseEvidenceSelector`

Add a guaranteed floor of CORE_KNOWLEDGE items that bypasses `minimumRelevanceScore`,
called between `selectDiverseEvidence()` and `selectKnowledgeFloor()`:

```java
private static final Set<String> CORE_KNOWLEDGE_GUARANTEED_KINDS =
    Set.of("INSIGHT", "DECISION", "ENGINEERING_EVENT", "ENGINEERING_STORY");

private void selectCoreKnowledgeFloor(
        List<RepositoryEvidence> candidates,
        ContextRequest request,
        SelectionState state) {
    int floor = request.minimumCoreKnowledgeItems();  // default 10, configurable
    int selected = 0;
    for (RepositoryEvidence candidate : candidates) {
        if (selected >= floor) return;
        if (!"CORE_KNOWLEDGE".equals(candidate.provenance().sourceType())) continue;
        if (!CORE_KNOWLEDGE_GUARANTEED_KINDS.contains(candidate.kind())) continue;
        if (state.selectedReferences.contains(candidate.reference())) continue;
        if (!fits(candidate, state, request)) return;
        add(candidate, "SELECTED_BY_CORE_KNOWLEDGE_FLOOR", state);
        selected++;
    }
}
```

### Configuration

Add `devlog.repository-context.min-core-knowledge-items` (default `10`) to
`RepositoryContextEngine` via `@Value` and pass it into `ContextRequest`.

### Invariants preserved

`RepositoryContextAdapter.filterByStoryScope()` already exempts CORE_KNOWLEDGE from the
commit-window filter (`isTechnicalKind()` returns false for INSIGHT/DECISION/etc.). No change
needed there.

## Acceptance Criteria

1. With any file scope (even a single directory), the selected context includes at least 10
   CORE_KNOWLEDGE items (Insights, Decisions, EngineeringEvents, Stories).
2. The floor is configurable via `devlog.repository-context.min-core-knowledge-items`.
3. `BudgetedDiverseEvidenceSelectorTest` covers: file scope + 25 total candidates with 5
   CORE_KNOWLEDGE → all 5 CORE_KNOWLEDGE selected.
4. Config B of the epic evaluation (story 0228, 2 packages) no longer returns empty content.
5. The overall selection budget is not increased; CORE_KNOWLEDGE floor displaces lower-scored
   technical evidence when the budget is tight.
