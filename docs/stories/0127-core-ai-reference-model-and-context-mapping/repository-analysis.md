# Story 0127 - Repository Analysis

## Status

**IMPLEMENTED - READY FOR HUMAN REVIEW**

## Baseline

- Branch: `main`
- Baseline HEAD: `c4facb6`
- Implementation remains uncommitted in the current worktree.
- Governing authority: ADR-068 and Story 0127.

## Existing Boundaries Reused

- `SelectedKnowledge` remains the already-authorized selection boundary.
- `SelectedKnowledgePromptProjectionService` remains the provider projection.
- `AiTask` remains the execution aggregate and persistence boundary.
- Java Core remains the deterministic authority for mapping and persistence.
- Python and provider contracts remain unchanged.
- Existing Flyway and JSONB persistence conventions are reused.

## Implemented Topology

```text
Authorized SelectedKnowledge
          ├──────────────────────────────┐
          ↓                              ↓
AiReferenceRegistryFactory       Existing provider projection
          ↓                              ↓
AiReferenceRegistry               selectedKnowledgeSnapshot / PromptRequest
          ↓                              ↓
AiReferenceMappingSnapshot        Python provider
          ↓
AiTask JSONB mapping snapshot
```

## Repository Findings

1. The registry is created from authorized `SelectedKnowledge`; it does not
   access repositories or perform selection, ranking or retrieval.
2. Analysis-local entities receive deterministic opaque handles such as
   `F001`, `O001`, `A001` and `PP001`.
3. Stable project and repository entities reuse canonical identity semantics
   inside typed references.
4. Semantic-section and relationship projections reuse one binding for the same
   entity rather than allocating parallel identities.
5. Architecture knowledge aliases the underlying Insight binding.
6. Grounding capabilities are recorded as internal metadata and remain separate
   from contextual entities.
7. The mapping snapshot is persisted separately from provider-facing selected
   knowledge and is absent from `AiTaskResponse`.
8. Null mapping snapshots remain readable for legacy tasks.
9. The mapping digest is deterministic and intentionally does not contribute to
   the existing `contextDigest`.

## Preparation Paths

### Standard Analysis

The task service creates the registry from the same `SelectedKnowledge` used by
the existing provider projection, persists the mapping snapshot, and preserves
the existing prompt payload.

### Story Context Analysis

`AnalyzeStoryContextUseCase` creates the equivalent registry from its selected
knowledge and passes it to task creation without changing the provider request.

## Deliberate Boundary

Provider-facing typed references, callback resolution, grounding contract
migration and Story 0128 remain deferred. Story 0126 interaction trace
persistence and callback contracts remain unchanged.

## Test Coverage Assessment

- Focused reference tests cover value semantics, registry uniqueness, aliases,
  grounding capabilities and digest determinism.
- Persistence integration tests cover JSONB reload and legacy null mapping.
- Preparation tests cover standard and Story Context Analysis wiring.
- Provider projection regression coverage confirms selected knowledge remains
  unchanged.
- Full backend verification passed: 1,316 tests, 0 failures.
- Flyway migrations validated and applied through V49.

## Conclusion

The repository contains the intended Core-side reference and mapping
foundation. Java Core remains the identity, mapping and persistence authority;
the new metadata is not trusted knowledge and is not yet provider-facing.
