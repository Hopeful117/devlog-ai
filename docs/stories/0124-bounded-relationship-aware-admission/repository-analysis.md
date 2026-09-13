# Repository Analysis

## Current Flow

- `KnowledgeSelectionServiceImpl` ranks active Insights and currently selects
  the first ten.
- `ProjectContextProviderImpl` retrieves validated Engineering Events through a
  bounded repository query; the context bound is increased to cover the
  standalone event budget plus the maximum relational capacity.
- `SelectedKnowledgePromptProjectionService` already preserves only relations
  whose two endpoints are projected. This Story changes admission, not the
  projection contract.
- `KnowledgeRelationSnapshot` provides explicit directed one-hop relations with
  typed endpoints. No inferred or transitive relationships are available.

## Constraints Preserved

- Java Core remains the sole context and trust authority.
- Relationship admission is bounded and deterministic.
- Only `INSIGHT` and `ENGINEERING_EVENT` endpoints can be admitted by this
  phase-one selector.
- `RESOLVES` is not eligible for `architecture-overview`.
- Existing standalone ranking remains unchanged; related endpoints are appended
  in deterministic relation order.
- Relationship capacity defaults to zero, preserving current production
  behavior until explicitly enabled and avoiding an invented production
  allocation.

## Candidate and Admission Bounds

When relationship admission is enabled, the Insight candidate query is bounded
to `maximumInsights + relationalCapacity`. The shared Engineering Event context
bound is 20: the existing final event budget is 10 plus the maximum allowed
relational capacity of 10. Final relationship admission is independently
limited by the configured capacity. With the fail-closed default of zero, the
existing unpaged Insight query is retained because no relationship candidate
can be admitted.
