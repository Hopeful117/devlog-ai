# Story 0043 — Strengthen Understanding Refresh Test Harness — Repository Analysis

## Purpose

Understand why multiple refresh-path bugs escaped the current test suite and
determine the minimum test-harness improvements that would have caught them
before runtime.

## Story Understanding

This Story is not about fixing a single refresh bug.

It is about fixing the **testing blind spot** that allowed several refresh
failures to survive despite otherwise strong repository quality gates.

The recent sequence matters:

* Story 0041 fixed selected-knowledge closure.
* Story 0042 fixed source `AnalysisContext` closure.
* the running system still surfaced a diagnostics `/context` failure.
* the latest refreshes now fail with provider timeout instead of grounding
  mismatch.

The codebase is therefore not missing generic tests. It is missing the right
tests at the seams actually exercised by understanding refreshes.

## What Escaped And Why

### 1. Story 0041 escaped at the wrong layer

Story 0041 correctly fixed `SelectedKnowledge` closure.

However, it assumed the base `AnalysisContext` was already coherent.

That assumption was not protected by a test asserting a source-context
invariant such as:

* every visible observation support fact ID resolves inside
  `AnalysisContext.facts`.

So the tests were valid for the selected layer, but incomplete for the full
refresh path.

### 2. Story 0042 revealed a diagnostics endpoint blind spot

After the grounding fixes, refresh diagnostics still hit:

* `GET /api/v1/analyses/{id}/context`

and the running backend threw a `NullPointerException` in:

* `AnalysisDiagnosticsServiceImpl.getContext(...)`

That indicates the repository lacked a test on the UI-facing diagnostics seam
itself.

The bug is not in core analysis generation. It is in how persisted context is
re-exposed through diagnostics.

### 3. Runtime refresh behavior is broader than service-unit behavior

The current suite is strong on:

* service tests;
* contract validators;
* deterministic selection/ranking rules;
* backend quality gates.

But the refresh experience depends on a composed runtime path involving:

* analysis creation;
* context building;
* knowledge selection;
* diagnostics exposure;
* AI task orchestration;
* AI task result handling.

We do not yet have enough regression coverage that treats this as one
invariant-rich workflow.

## Relevant Current Coverage

### Strong areas

The repository already has strong targeted coverage around:

* `KnowledgeSelectionServiceImpl`
* `AnalysisContextServiceImpl`
* AI proposal validation
* controller/service slices for many domain endpoints

These tests are useful and should remain.

### Weak areas

The current weak spots appear to be:

* diagnostics endpoint tests for persisted context snapshots;
* cross-layer invariants spanning source context and selected knowledge;
* refresh-oriented integration tests built from known real bug shapes;
* regression fixtures derived from live failed analyses.

## Minimum Invariants Missing Today

The recent bugs suggest the following invariants should be tested explicitly:

### Invariant A — Source context closure

If an observation is visible in `AnalysisContext.observations`, then every
`supportingFactId` it exposes must resolve in `AnalysisContext.facts`.

### Invariant B — Selected snapshot closure

If an observation is visible in `SelectedKnowledge.selectedObservations`, then
every `supportingFactId` it exposes must resolve in
`SelectedKnowledge.selectedFacts`.

### Invariant C — Diagnostics context endpoint safety

If an AI task persisted a context snapshot, then
`GET /api/v1/analyses/{id}/context` must be serializable and must not fail due
to null-containing maps or similar snapshot-shape issues.

### Invariant D — Refresh-path compatibility

The snapshots emitted by Core and later consumed by the AI Engine or diagnostics
must remain mutually compatible across:

* persistence;
* replay;
* diagnostics exposure;
* validation.

## Recommended Test Strategy

The right answer is not one giant end-to-end test only.

It is a layered test harness with a few high-value refresh scenarios.

### Layer 1 — Invariant-focused service tests

Add or tighten deterministic tests around:

* source-context closure;
* selected-knowledge closure;
* null-safe diagnostics snapshot handling.

### Layer 2 — Web/endpoint regression tests

Add targeted controller/web tests for:

* `GET /api/v1/analyses/{id}/context`
* possibly related diagnostics endpoints used during refresh observation

These are the tests most likely to catch the exact `/context` class of failure.

### Layer 3 — Refresh-oriented integration slice

Add at least one scenario that exercises the refresh path across several
components using controlled fixtures, without relying on a real provider call.

The goal is not browser automation.

The goal is to protect the composed contract between:

* built context;
* persisted task snapshot;
* diagnostics retrieval;
* AI-task-facing expectations.

## Candidate Implementation Directions

### Option A — Only add more unit tests

Pros:

* cheap
* deterministic

Cons:

* likely misses the diagnostics/UI seam again
* does not address composed refresh behavior

Verdict:

* insufficient alone.

### Option B — Add a layered refresh test harness

Pros:

* catches seam failures at the right level
* preserves deterministic tests
* improves confidence without requiring a live provider

Cons:

* slightly broader than a pure unit-test Story

Verdict:

* recommended.

## Recommended Direction

Implement a layered refresh test harness with three parts:

1. invariant-focused service tests,
2. endpoint regression tests for diagnostics,
3. one refresh-oriented integration scenario spanning multiple layers.

The emphasis should stay on deterministic invariants, not provider timing.

## Risks

### Risk 1 — Overbuilding a fake E2E framework

Control:

* keep the Story focused on the minimum seams already proven to fail.

### Risk 2 — Brittle tests coupled to incidental JSON layout

Control:

* assert semantic invariants and contract behavior, not large opaque snapshots.

### Risk 3 — Leaving timeout failures totally unaddressed

Control:

* document that timeout diagnosis is a separate Story, but strengthen tests so
  future timeout work can safely evolve the refresh path.

## Conclusion

The missing capability is not generic test quantity. It is refresh-path
coverage at the right seams.

The minimum useful Story is therefore:

* strengthen the understanding-refresh test harness before the next bugfix
  Stories,
* especially around diagnostics endpoint safety and cross-layer grounding
  invariants.
