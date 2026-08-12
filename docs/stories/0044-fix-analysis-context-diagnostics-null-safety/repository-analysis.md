# Story 0044 — Fix Analysis Context Diagnostics Null Safety — Repository Analysis

## Purpose

Understand why the diagnostics `/context` endpoint still fails during the
refresh journey and determine the smallest safe fix that restores runtime
observability without redesigning diagnostics.

## Story Understanding

This Story is **not** another grounding fix.

Stories 0041 and 0042 addressed grounding coherence in:

* `SelectedKnowledge`
* `AnalysisContext`

Story 0044 addresses a different bug on the same user journey:

* the backend can still fail when a persisted context snapshot is re-exposed
  through diagnostics.

So the problem here is diagnostics null-safety, not AI-task correctness.

## Current Evidence

Story 0043 already encoded the observed failure shape as regression coverage.

That test proved the current service behavior can throw when a persisted
`contextSnapshot` contains at least one null value.

Relevant evidence now lives in:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java`

Important interpretation:

* the controller layer is already capable of serializing null-containing maps;
* the failure occurs before serialization, inside the diagnostics service.

## Relevant Components

### `AnalysisDiagnosticsServiceImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceImpl.java`

Current `getContext(...)` behavior:

```java
return aiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc(analysisId)
        .map(AiTask::getContextSnapshot)
        .map(Map::copyOf)
        .orElseThrow(...);
```

Key issue:

* `Map.copyOf(...)` rejects null keys and null values;
* persisted task snapshots are allowed to contain null values;
* therefore a valid persisted snapshot can crash diagnostics replay.

This is the direct bug source.

### `AnalysisController`

File:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/controller/AnalysisController.java`

Current behavior:

* simply returns `ResponseEntity.ok(analysisDiagnosticsService.getContext(id))`

Interpretation:

* the controller is not the bug source;
* once the service returns a safe map, the HTTP seam should remain stable.

### `AiTask.contextSnapshot`

Observed role:

* serves as persisted replay/debug context;
* may contain nested structures and null values coming from the real prompt
  payload shape.

Implication:

* the diagnostics layer must tolerate null values in persisted snapshots;
* rejecting them at replay time is inconsistent with their storage.

## Root Cause

The bug comes from applying an immutable-copy helper with stricter constraints
than the persisted snapshot contract.

Specifically:

1. `AiTask.contextSnapshot` may contain null values.
2. `AnalysisDiagnosticsServiceImpl.getContext(...)` uses `Map.copyOf(...)`.
3. `Map.copyOf(...)` throws `NullPointerException` when any key or value is
   null.
4. `/api/v1/analyses/{id}/context` therefore fails before the controller can
   serialize the snapshot.

This is a replay-time diagnostics defect, not a data-generation defect.

## Why Story 0043 Was Necessary First

Story 0043 did the right thing by capturing the current failure shape first.

That gave us:

* a service-level regression for the actual bug;
* a controller-level contract test proving the HTTP seam can represent nulls;
* a clearer separation between:
  * test-harness strengthening,
  * diagnostics null-safety repair,
  * timeout remediation.

Story 0044 can now stay narrow and intentional.

## Candidate Fix Directions

### Option A — Sanitize nulls away

Approach:

* recursively strip null values before returning diagnostics context.

Pros:

* avoids the exception.

Cons:

* silently changes the persisted context shape;
* makes diagnostics less truthful;
* risks hiding information that may matter during debugging.

Verdict:

* not preferred.

### Option B — Preserve structure and replace `Map.copyOf(...)` with a null-tolerant defensive copy

Approach:

* return a defensive copy that preserves insertion order and null values;
* keep nested values untouched unless defensive copying is explicitly needed at
  deeper levels.

Pros:

* minimal behavioral change;
* keeps the diagnostics contract truthful;
* aligns with the controller regression already added in Story 0043.

Cons:

* requires deciding how much immutability is actually needed for replay maps.

Verdict:

* recommended.

### Option C — Redesign diagnostics context as a typed DTO

Approach:

* replace raw map replay with a typed diagnostics model.

Pros:

* stronger long-term contract.

Cons:

* much broader than the bug requires;
* high review cost;
* unnecessary while the current persisted shape is still map-based.

Verdict:

* out of scope.

## Recommended Direction

Apply a narrow service-layer fix in `AnalysisDiagnosticsServiceImpl`:

* stop using `Map.copyOf(...)` for `contextSnapshot` replay;
* return a deterministic null-tolerant defensive map copy instead;
* preserve the current HTTP contract and the persisted logical structure.

The target behavior is:

* `/api/v1/analyses/{id}/context` succeeds when persisted snapshots contain
  null values;
* null values remain visible as `null` in JSON;
* entity-not-found behavior remains unchanged.

## Risks

### Risk 1 — Returning a mutable structure unintentionally

Control:

* still create a defensive copy at the top level;
* if needed, document clearly whether nested maps/lists are returned as-is or
  recursively copied.

### Risk 2 — Accidentally changing diagnostics shape

Control:

* keep the controller contract exactly as tested in Story 0043.

### Risk 3 — Mixing this fix with timeout work

Control:

* keep Story 0044 strictly about diagnostics null-safety;
* leave provider timeout remediation to Story 0045.

## Conclusion

The remaining `/context` failure is a narrow service-layer bug caused by
`Map.copyOf(...)` being incompatible with the persisted diagnostics snapshot
contract.

The smallest correct fix is to preserve the replayed context structure while
replacing that null-intolerant copy step with a deterministic null-tolerant
defensive copy.

