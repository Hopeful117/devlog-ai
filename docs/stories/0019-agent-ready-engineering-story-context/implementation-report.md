# Implementation Report — Story 0019

## Status

Implementation, documentation reconciliation, and final quality validation completed.

## Summary

The Engineering Story endpoint now returns a versioned Agent-Ready projection by default and keeps
the existing rich context behind `detail=full`. Projection occurs only after the authoritative
Repository Context has completed collection, ranking, selection, symbol enrichment, content
allocation, and accounting. It never re-ranks evidence or promotes rejected candidates.

The compact contract preserves the outer Project Context and
`repositoryContext.evidence[].reference/summary` adapter path. It retains essential provenance,
revision, final score, bounded reasons and related references, content/symbol outcomes, aggregate
diagnostics, the Repository Context digest, and a separate projection digest. Individual rejected
candidate decisions and repeated score maps are absent.

## Implementation

* Added explicit `agent` and `full` response modes with standard invalid-parameter handling.
* Added immutable projection DTOs, policy configuration, canonical Jackson serialization, SHA-256
  identity, byte/token accounting, and a bounded projection failure.
* Enforced 32,768 canonical bytes and 8,192 estimated tokens by deterministic degradation:
  related references, extra reasons, declaration payloads, content text, then final-tail evidence.
* Reused one immutable Project Context snapshot for Repository Context and projection construction.
* Added phase timing, count, byte, and digest logs without changing persistence.
* Kept generic Repository Context, AI-task snapshots, ranking, selection, and enrichment unchanged.
* Corrected transported layer/kind aggregates after review so they always sum to the actual
  projected `selectedCount` after tail evidence removal.

## Tests and Validation

Focused controller, service, policy, projection, degradation, digest, accounting, failure, and
compatibility tests pass. The complete backend run passes with 445 tests, zero failures, zero
errors, and zero skipped tests. JaCoCo reports 86.48% instruction, 65.14% branch, and 83.15% line
coverage. The nine real Engineering Story adapter tests pass. `git diff --check` passes.

One complete authenticated run initially exposed an intermittent pre-existing symbol-limit test;
the same test passed immediately in isolation and in two subsequent complete 445-test runs.

The backend Docker image rebuilt and the exact Story 0019 request succeeded through both HTTP and
the real three-second adapter. Final compact accounting is internally consistent: 34 evidence
items, `selectedCount=34`, and layer/kind aggregate totals of 34.

## Benchmark Outcome

The pre-implementation rich baseline for the exact Story 0019 request was 164,445 HTTP bytes,
approximately 41,112 delivered byte/4 tokens, with 60 evidence items and 124 selection decisions.
Major contributors were 70,455 bytes of score/ranking detail and 26,094 bytes of selection
decisions.

The final default response is 33,064 HTTP bytes and 32,684 canonical bytes / 8,171 estimated
tokens: a 79.9% wire-byte reduction. It retains 34 already-selected items and aggregate rejection
counts, after removing 98 related references, 120 extra reasons, and 26 final-tail evidence items.
No content or declaration payload needed removal. The rich diagnostic mode remains 164,445 bytes.

The real adapter completed successfully in 0.88 seconds on the measured warm request. Earlier
baseline runs ranged from 0.71 to 1.51 seconds. These measurements demonstrate payload reduction
and local compatibility for this repository, not general productivity gains.

## SonarQube Outcome

All issues attributable to Story 0019 found during analysis were corrected: wildcard responses,
projection complexity, nested ternary logic, an assertion lambda, and unused code. New-code
coverage is 84.2% and duplication is 0.0%.

The first Quality Gate run remained red because its new-code period included three issues outside
the original Story 0019 diff:

* `java:S2229` and `java:S6809` in `ProjectHistoryServiceImpl#importHistory`, last changed by Story
  0018;
* `java:S5778` in `ProjectUnderstandingServiceTest`, also last changed by Story 0018.

After explicit human authorization, both history-import entry points were given compatible
transactional boundaries and now delegate to one private non-proxied implementation. The test
assertion constructs its request outside the exception lambda. Focused tests and the complete suite
pass after these corrections.

The final authenticated analysis reports Quality Gate `OK`, 84.2% new-code coverage, 0.0% new
duplication, zero new violations, and zero unresolved issues. AC-15 is satisfied.

## Documentation Reconciliation

Documentation update: Required and completed.

README documents the compact default, `detail=full`, policy configuration, dual digests,
degradation, timeout fallback, and mandatory current-repository verification. Architecture
documentation records the rich-domain/agent-transport boundary. ADR-046 records the decision and
rejected alternatives.

## Deviations

The implemented degradation applies each optional-detail stage across the projection before
testing the next stage, matching the approved stage order. For this representative request the
32-KiB policy necessarily removes final-tail evidence; the counters and warnings make that loss
explicit.

No commit, finalization, or Engineering Report has been produced.

## Recommendation

Ready for Code Review.
