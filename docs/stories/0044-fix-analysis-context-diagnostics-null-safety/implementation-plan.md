# Story 0044 — Fix Analysis Context Diagnostics Null Safety — Implementation Plan

## Status

Planned

## Planning Goal

Apply the smallest safe fix that makes diagnostics context replay null-safe,
while preserving the current `/api/v1/analyses/{id}/context` contract and the
regression harness added in Story 0043.

## Key Decision

Fix the bug strictly at the diagnostics service layer.

Do **not**:

* redesign diagnostics,
* sanitize away null values,
* or change the controller contract.

The current HTTP seam is already correctly covered by tests. The failing point
is the service’s use of `Map.copyOf(...)` on persisted context snapshots.

## Why This Approach

The repository already tells us three important things:

1. persisted `contextSnapshot` maps may contain null values;
2. the controller layer can serialize null-containing maps correctly;
3. the current crash occurs before serialization.

That means the best fix is a narrow service-level repair that preserves the
existing structure instead of introducing a broader DTO or a lossy
sanitization step.

## In-Scope Implementation Steps

### Step 1 — Replace the null-intolerant copy path in `AnalysisDiagnosticsServiceImpl`

Primary target:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceImpl.java`

Planned change:

* replace `Map.copyOf(...)` in `getContext(...)` with a deterministic
  null-tolerant defensive copy;
* preserve insertion order when possible;
* keep `EntityNotFoundException` behavior unchanged when no task snapshot is
  available.

Design preference:

* use a small private helper rather than inlining copy logic directly inside
  `getContext(...)`.

### Step 2 — Update the service regression from “fails” to “works”

Primary target:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceTest.java`

Planned changes:

* replace the current failure-shape assertion with a success assertion for
  null-containing snapshots;
* verify that null values remain visible in the returned context map rather
  than being silently dropped;
* preserve the existing non-null happy-path and not-found tests.

### Step 3 — Keep the controller seam regression aligned

Primary target:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java`

Planned behavior:

* keep the existing WebMvc seam test proving `/context` can serialize a
  null-containing map;
* adjust only if the service-facing contract requires a small assertion
  refinement.

### Step 4 — Document any contract clarification explicitly

Story-local documentation:

* if the fix preserves the same JSON shape with null values intact, document
  that explicitly as “no contract change”;
* if any narrow contract nuance changes, record it clearly in the
  implementation artifacts.

## Explicit Out-Of-Scope Choices

This Story will **not**:

* address provider timeout behavior
* revisit grounding validation
* redesign diagnostics into a typed DTO
* recursively normalize all possible nested structures unless required by the
  actual persisted contract

Those concerns belong to other Stories, especially 0045.

## Files Likely To Change

Expected:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceTest.java`

Possible:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java`

## Validation Plan

At minimum:

* `./mvnw -Dtest=AnalysisDiagnosticsServiceTest,AnalysisControllerWebMvcTest test`
* `./mvnw verify`
* `git diff --check`

## Risks

### Risk 1 — Accidental contract drift

Mitigation:

* keep the controller regression in place;
* preserve null visibility in the returned JSON.

### Risk 2 — Returning a structure that callers can mutate unexpectedly

Mitigation:

* still return a defensive top-level copy rather than the entity-backed map
  instance directly.

### Risk 3 — Overengineering nested-copy behavior

Mitigation:

* start with the minimum copy behavior needed by the actual failing case and
  current contract.

## Planned Outcome

After this Story:

* `/api/v1/analyses/{id}/context` will stop failing on persisted snapshots
  containing null values;
* Story 0043’s regression harness will stay green against the repaired
  behavior;
* the next remaining refresh blocker can be treated cleanly as the timeout
  Story rather than being mixed with diagnostics replay bugs.

