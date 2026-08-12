# Story 0044 — Fix Analysis Context Diagnostics Null Safety — Implementation Report

## Status

Implemented

## Summary

Implemented a narrow diagnostics-service bugfix so
`/api/v1/analyses/{id}/context` no longer fails when a persisted
`contextSnapshot` contains null values.

The fix preserves the existing diagnostics HTTP contract:

* the endpoint still returns a `Map<String, Object>`;
* null values remain visible as `null` in JSON;
* entity-not-found behavior remains unchanged.

## Changes

### 1. Replaced the null-intolerant copy path in `AnalysisDiagnosticsServiceImpl`

Updated:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceImpl.java`

Previous behavior:

* `getContext(...)` called `Map.copyOf(...)` on the persisted task snapshot;
* snapshots containing null values caused a `NullPointerException`.

New behavior:

* `getContext(...)` now uses a dedicated null-tolerant defensive copy helper;
* the helper preserves map structure and copies nested maps/lists
  deterministically;
* top-level and nested null values remain intact, including null entries inside
  nested lists.

Outcome:

* diagnostics replay no longer rejects valid persisted snapshots solely because
  they contain null values.

### 2. Converted the service regression from failure-shape to success-shape

Updated:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceTest.java`

Changes:

* replaced the prior regression asserting `NullPointerException`;
* now verifies that:
  * the map is returned successfully;
  * the null-valued entry is preserved;
  * nested structure remains accessible;
  * nested lists may also contain null values safely.

Outcome:

* Story 0043’s temporary bug-shape capture is now upgraded into a permanent
  success regression.

### 3. Kept the controller seam contract aligned

Reused:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java`

Outcome:

* the existing WebMvc regression still proves `/context` serializes
  null-containing maps correctly;
* no controller contract change was required.

## Behavioral Outcome

### Now fixed

* persisted diagnostics context snapshots containing null values no longer
  crash `AnalysisDiagnosticsServiceImpl.getContext(...)`
* `/api/v1/analyses/{id}/context` can expose those snapshots successfully

### Preserved

* diagnostics response shape
* null visibility in JSON
* `EntityNotFoundException` behavior when no snapshot exists
* separation from grounding fixes and timeout handling

## Contract Outcome

Diagnostics contract change: **None**

Clarification:

* the endpoint already intended to expose persisted snapshot structure;
* Story 0044 restores that intended behavior for null-containing payloads
  rather than redefining the contract.

## Scope Boundaries Maintained

Deliberately not changed:

* grounding validators
* `AnalysisContext` construction
* `SelectedKnowledge` construction
* AI provider timeout behavior
* diagnostics DTO redesign

That keeps Story 0044 tightly scoped to diagnostics null-safety.

## Documentation Outcome

Updated or added:

* `docs/stories/0044-fix-analysis-context-diagnostics-null-safety/story.md`
* `docs/stories/0044-fix-analysis-context-diagnostics-null-safety/repository-analysis.md`
* `docs/stories/0044-fix-analysis-context-diagnostics-null-safety/implementation-plan.md`
* `docs/stories/0044-fix-analysis-context-diagnostics-null-safety/implementation-report.md`

## Validation

Performed:

* targeted diagnostics tests
* full backend quality gate
* repository diff formatting check

Results:

* `./mvnw -Dtest=AnalysisDiagnosticsServiceTest,AnalysisControllerWebMvcTest test`: pass
* `./mvnw verify`: pass
* backend tests: `587` pass
* JaCoCo coverage checks: pass
* `git diff --check`: pass

## Remaining Limitations

* this Story does not address the refresh-path timeout problem
* this Story does not introduce a typed diagnostics context model
* the endpoint still exposes replayed raw snapshot structure by design
