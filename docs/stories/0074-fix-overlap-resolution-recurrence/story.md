# Story 0074 — Fix Overlap Resolution Recurrence

## Status

Done

## Priority

Critical

## Objective

Fix the critical bug where overlap review findings reappear after resolution,
making the "Resolve overlap" button effectively useless.

## Motivation

When a user resolves a `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` finding, the
system marks the finding as `RESOLVED` and sets non-canonical insights to
`SUPERSEDED` status. However, two independent defects cause the overlap
to reappear on the next evaluation cycle:

1. **The duplicate audit includes SUPERSEDED insights** —
   `InsightRepository.findByProjectIdOrderByCreatedAtDescIdDesc()` returns
   all insights regardless of status, so superseded insights reform the
   duplicate cluster.

2. **The finding deduplication guard ignores RESOLVED findings** —
   `hasEquivalentActiveFinding()` only checks `OPEN`/`ACKNOWLEDGED` findings,
   so a resolved finding with identical details is not detected as equivalent,
   and a new finding is created.

This creates an infinite loop: resolve → reappear → resolve → reappear.

Additionally, `supersedeInsight()` does not create any `KnowledgeRelation`
between the canonical and superseded insights, making the resolution
non-traceable.

## Scope

### In Scope

1. Filter superseded/archived insights from duplicate audit input
2. Extend finding deduplication guard to include RESOLVED findings for
   deterministic issue types
3. Create `KnowledgeRelation(RESOLVES)` when superseding an insight
4. Update all affected queries and services

### Out of Scope

* Content merging from superseded to canonical insight (future story)
* TF-IDF similarity improvements (Story 0075)
* Pre-promotion duplicate checks (Story 0076)
* Cross-project duplicate detection

## Constraints

* Must not break existing maintenance evaluation flow
* Must not change existing API contracts
* Must pass all existing tests

## Acceptance Criteria

* AC-1: After resolving an overlap finding, the same cluster does not
  reappear on the next evaluation
* AC-2: Superseded insights are excluded from duplicate audit input
* AC-3: RESOLVED findings with identical details are detected as equivalent
  and prevent re-creation
* AC-4: A `KnowledgeRelation(RESOLVES)` is created between canonical and
  superseded insights
* AC-5: All existing tests pass

## Dependencies

* Story 0072: Trusted Knowledge Deduplication Service
* Story 0073: Remediation Actions and Bugfixes
* `InsightRepository` — needs status-filtered query
* `KnowledgeRelationService` — needs to create RESOLVES relations
