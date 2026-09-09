# Story 0117 - Cross-Analysis Historical Knowledge Retrieval

## Status

**IMPLEMENTATION AUTHORIZED**

## Baseline

- Baseline SHA: `98852b6bcdf104b18fee90487945b4ec07a804f9`
- Implementation branch: `story/0117-cross-analysis-historical-knowledge-retrieval`
- Governing ADRs: ADR-063, ADR-067
- Preserved Stories: 0107, 0114, 0115, 0116

## Objective

Extend Story Context Analysis so that Story-aware knowledge selection can consider bounded
historical Facts and Observations from previous Analyses, instead of being limited to the
current baseline ProjectProfile Analysis.

The historical expansion must remain deterministic, bounded, provenance-driven, and
compatible with the existing `KnowledgeSelectionService`.

## Problem

Story Context Analysis currently receives Story-aware selected knowledge, but Facts and
Observations remain bounded to the baseline Analysis. This prevents DevLog from
reconstructing relevant historical engineering context when useful knowledge exists in
previous Analyses.

The solution must not load all project Facts or introduce RAG, vector search, AI-driven
retrieval, or a second knowledge-ranking system.

## Architecture Direction

```text
Current Engineering Story
        |
        v
existing Story scope
        |
        v
derive deterministic provenance anchors
        |
        v
bounded historical Analysis window
        |
        v
bounded historical Facts
        |
        v
HistoricalKnowledgeCandidateService
  - exact provenance matching
  - deduplication
  - candidate bounding
        |
        v
Observations linked to retained Facts
        |
        v
baseline + historical candidates
        |
        v
existing KnowledgeSelectionService
        |
        v
Story-aware ranking
        |
        v
existing budgets / closure / grounding
```

## Responsibility Boundaries

### Repository layer

Repositories expose simple, bounded persistence primitives. They do not implement Story
relevance scoring or business-level historical reconstruction:

- retrieve a bounded window of completed Analyses for a project;
- retrieve Facts belonging to a bounded set of Analyses;
- retrieve Observations associated with retained supporting Fact IDs.

### HistoricalKnowledgeCandidateService

The service owns historical candidate orchestration:

- extract exact deterministic provenance anchors from current Story/repository scope;
- obtain bounded Analyses and Facts;
- retain only Facts whose provenance intersects supported Story anchors;
- deduplicate by stable Fact identity and apply the historical candidate bound;
- retrieve compatible Observations;
- return historical candidates to the existing selection pipeline.

### KnowledgeSelectionService

The existing service remains authoritative for Story-aware and guidance ranking,
Observation-to-Fact closure, deterministic ordering, final budgets, and final
`SelectedKnowledge`. No parallel ranking mechanism is introduced.

## Acceptance Criteria

1. Baseline knowledge remains unchanged and historical candidates only supplement it.
2. A Fact from a previous completed Analysis is eligible when its provenance exactly
   matches the current Story scope.
3. Historical Facts with no exact provenance intersection remain excluded; recency alone
   is insufficient.
4. Explicit Analysis and Fact windows bound retrieval before project history reaches
   application memory.
5. Repositories perform persistence retrieval and bounding only.
6. Matching is exact and deterministic; no fuzzy, semantic, vector, or AI retrieval occurs.
7. Existing `KnowledgeSelectionService` remains the only final relevance-ranking authority.
8. Historical Facts are deduplicated by stable Fact identity.
9. Historical Observations are eligible only when every supporting Fact is retained and
   existing closure semantics remain valid.
10. Empty historical expansion preserves baseline-only execution without fallback.
11. Identical persisted data, scope, and limits produce identical candidate ordering.
12. Story 0116 grounding authority remains unchanged.
13. Existing SCA transport, output, callback, freshness, and selected-knowledge contracts
    remain compatible.
14. Tests prove no unrestricted project-wide Fact or Observation retrieval is used.
15. An older provenance-matching Fact is eligible while a newer unrelated Fact is not;
    final Story ranking remains in `KnowledgeSelectionService`.

## Explicitly Out Of Scope

- ADR, roadmap, or Story Markdown body retrieval
- Decision or EngineeringEvent selection
- cross-Story dependency modeling
- RAG, vectors, embeddings, fuzzy matching, or AI-assisted retrieval
- proactive behavior, significance detection, frontend changes, or new grounding semantics
- a new persistence model unless existing mappings strictly require it

## Quality Gates

- focused repository and historical candidate service tests
- existing Story-aware selection and SCA integration tests
- full backend verification with JaCoCo
- full AI Engine regression
- `git diff --check`
- no generated build artifacts committed

## Authorization

- Implementation: explicitly authorized
- Commit: authorized after scope completion and successful validation
- Push, merge, PR, remote operations, and branch deletion: not authorized
