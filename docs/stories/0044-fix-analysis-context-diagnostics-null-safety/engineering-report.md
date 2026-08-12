# Story 0044 — Fix Analysis Context Diagnostics Null Safety — Engineering Report

## Status

Completed

## Story Recap

Story 0044 repaired the diagnostics `/context` failure that still made the
understanding refresh journey harder to use and harder to inspect.

The bug was narrower than the grounding issues fixed in Stories 0041 and 0042.

It affected replay of persisted diagnostics snapshots when those snapshots
contained null values.

## Problem

The endpoint:

* `GET /api/v1/analyses/{id}/context`

was expected to expose the last persisted AI task context snapshot for
diagnostics and debugging.

However, `AnalysisDiagnosticsServiceImpl.getContext(...)` replayed the stored
snapshot through `Map.copyOf(...)`.

That made the diagnostics path null-intolerant:

* top-level null values caused `NullPointerException`;
* valid persisted snapshots could fail during replay;
* the user-facing refresh journey then lost a useful debugging surface exactly
  when it was most needed.

## Implemented Outcome

Story 0044 replaced the null-intolerant replay path with a defensive
null-preserving copy strategy in:

* [AnalysisDiagnosticsServiceImpl.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/main/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceImpl.java:84)

Implemented behavior:

* top-level null values are preserved;
* nested map values are preserved;
* nested list entries may also contain null values safely;
* callers still receive an immutable replayed structure.

Regression coverage was updated in:

* [AnalysisDiagnosticsServiceTest.java](/home/ludo/Bureau/workspace/devlog-ai/backend/src/test/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceTest.java:298)

The existing controller seam from Story 0043 remained valid and continued to
prove that:

* `/api/v1/analyses/{id}/context` serializes null-containing payloads
  correctly once the service returns them safely.

## Why This Matters

This Story restores the intended value of the diagnostics endpoint:

* persisted context snapshots can now be replayed safely;
* null-containing data is treated as legitimate snapshot content rather than as
  an internal crash condition;
* debugging refresh failures becomes less blind because the diagnostics surface
  is usable again.

This is especially important after the recent refresh instability: a broken
debug endpoint compounds runtime failures by removing observability.

## What The Story Intentionally Does Not Do

This Story does **not**:

* change grounding validation rules;
* alter `AnalysisContext` construction;
* alter `SelectedKnowledge` construction;
* fix the LLM timeout problem;
* redesign diagnostics into a typed DTO model.

Those concerns remain explicitly outside the scope of Story 0044.

## Tests And Verification

Passed:

* `./mvnw -Dtest=AnalysisDiagnosticsServiceTest,AnalysisControllerWebMvcTest test`
* `./mvnw verify`
* JaCoCo coverage checks
* `git diff --check`

Quality gate result:

* backend tests: **587 PASS**
* JaCoCo: **PASS**

## Architectural Outcome

The refresh-path hardening now has a cleaner separation:

* Story 0041 fixes selected-knowledge grounding closure;
* Story 0042 fixes source `AnalysisContext` grounding closure;
* Story 0043 strengthens the test harness around the refresh seams;
* Story 0044 restores null-safe diagnostics replay for persisted snapshots.

That layering is healthier than folding unrelated runtime problems into one
large, ambiguous bugfix.

## Honest Limitations

Story 0044 improves diagnostics replay, but it does not make the full refresh
journey “healthy” on its own.

Remaining limits:

* it does not address timeout behavior;
* it does not solve every refresh failure mode;
* it still exposes raw snapshot structure by design.

That is acceptable because the Story’s goal was a precise diagnostics-service
repair, not a broader refresh redesign.

## Final Outcome

Completed.

Story 0044 removes a real null-safety defect from the diagnostics replay path,
keeps the existing HTTP contract intact, and makes the `/context` endpoint
usable again for null-containing persisted snapshots.
