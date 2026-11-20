# Story 0219 - Code Review

## Review Scope

Reviewed the Story 0219 implementation diff against the approved Story,
Repository Analysis, and Implementation Plan.

## Findings

The independent review identified three major findings. They were corrected in
the current implementation:

- REST/MCP now expose additive `executionScope` and `provenance` fields from the
  persisted task snapshot.
- Unresolvable revisions are represented as `UNKNOWN` in the agent projection
  while strict grounding/submission checks remain fail-closed.
- The projection now rejects a mismatch between the propagated execution scope
  revision and the canonical evidence revision, and marks evidence/freshness
  mismatches explicitly.

The implementation:

- preserves `RepositoryRevisionScope` through context filtering;
- exposes scope additively without changing grounding authorization;
- preserves freshness statuses and warnings;
- accepts nullable freshness revisions;
- leaves retrieval, ranking, budgets, prompts, providers and Python unchanged;
- does not modify the unrelated MCP worktree files.

## Validation Evidence

- 35 focused Story Context submission/callback/projection tests passed.
- 36 focused repository scope, document collector, REST and protocol tests
  passed.
- Production backend and contracts packaging passed with tests skipped.
- `git diff --check` passed.
- Backend focused regression after the corrections: 38 tests passed.
- MCP `StoryContextAnalysisToolTest`: 7 tests passed.

## Residual Risk

The complete backend test compilation remains blocked by eight pre-existing
errors outside Story 0219:

- missing `rejectedCount()` and `rejectionReasonCounts()` accessors in an
  unrelated mapper test;
- outdated `ObjectiveUnderstanding` constructor calls in unrelated tests.

These failures must be resolved before claiming a repository-wide green test
suite. They were not changed because they are outside Story 0219 scope.

## Review Outcome

READY_FOR_HUMAN_ACCEPTANCE, with the full-suite blocker recorded above and the
independent review findings addressed.
