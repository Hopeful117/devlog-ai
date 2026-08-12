# Story 0043 — Strengthen Understanding Refresh Test Harness — Implementation Plan

## Status

Planned

## Planning Goal

Add the smallest high-value test-harness improvements that would have caught
the recent refresh-path bugs earlier, without overbuilding a brittle fake E2E
framework.

## Key Decision

Implement a **layered refresh test harness** rather than one oversized end-to-end
test.

The Story will strengthen three seams:

1. source-context and selected-knowledge invariants,
2. diagnostics endpoint safety,
3. one refresh-oriented multi-layer scenario.

This keeps the coverage deterministic while still protecting the real refresh
journey.

## Why This Approach

The recent failures did not come from missing generic unit tests.

They came from missing tests at the exact boundaries where the refresh path
crosses layers:

* `AnalysisContext` construction
* `SelectedKnowledge` construction
* diagnostics context exposure
* understanding refresh orchestration

The best response is therefore not to add more isolated tests everywhere, but
to make those seams first-class test targets.

## In-Scope Implementation Steps

### Step 1 — Formalize the invariant set in tests

Strengthen test coverage so the following invariants are explicitly asserted:

* every visible observation support fact ID in `AnalysisContext` resolves in
  `AnalysisContext.facts`
* every visible observation support fact ID in `SelectedKnowledge` resolves in
  `SelectedKnowledge.selectedFacts`
* persisted diagnostics context snapshots remain safely exposable through the
  diagnostics endpoint layer

Primary target files:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceTest.java`

### Step 2 — Add diagnostics endpoint regression coverage

Cover the refresh-consumed endpoint seam directly with WebMvc tests so runtime
failures like `/api/v1/analyses/{id}/context` cannot escape solely because the
service tests passed.

Primary target file:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java`

Planned scenarios:

* successful retrieval of context snapshots
* null-containing or edge-shaped diagnostics context snapshots where the
  service/controller contract must still behave correctly

Note:

This Story adds the harness. It does not need to fix every underlying bug in
the same commit if the regression is documented and covered.

### Step 3 — Add one refresh-oriented multi-layer scenario

Add one scenario that exercises the composed refresh workflow more realistically
than a single service-unit test, while still avoiding provider-coupled E2E.

Recommended location:

* `backend/src/test/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingControllerWebMvcTest.java`
  and/or a new narrowly scoped integration-style test in the refresh workflow
  package

Planned shape:

* start an understanding refresh request
* verify the analysis and AI-task orchestration path uses coherent snapshots
* verify diagnostics/context retrieval contracts remain valid for the analysis
  produced by the refresh path

This scenario will be mock-driven and deterministic rather than provider-live.

### Step 4 — Use known bug shapes as regression drivers

Encode regression cases derived from the real failures seen in Stories 0041 and
0042:

* dangling observation support fact IDs in selected knowledge
* dangling observation support fact IDs in source context
* diagnostics context retrieval on persisted snapshots that include nulls

This ensures the test harness is shaped by real runtime evidence instead of
generic assumptions.

## Explicit Out-Of-Scope Choices

The Story will **not**:

* add browser tests
* add full Docker/provider orchestration into the test suite
* redesign the AI prompt contract
* fix the LLM timeout root cause
* replace unit/service tests with only broad integration scenarios

## Files Likely To Change

Expected:

* `backend/src/test/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/analysis/diagnostics/service/AnalysisDiagnosticsServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/projectunderstanding/ProjectUnderstandingControllerWebMvcTest.java`

Possible:

* one additional narrowly scoped refresh-oriented test file if existing test
  classes become overloaded

## Validation Plan

At minimum:

* targeted backend test execution for all modified refresh-harness classes
* full backend `./mvnw verify`
* `git diff --check`

## Risks

### Risk 1 — Overengineering the harness

Mitigation:

* keep only one multi-layer refresh scenario
* prefer invariant tests over giant snapshots

### Risk 2 — Creating brittle tests tied to serialization trivia

Mitigation:

* assert contract semantics and failure classes rather than exact large payload
  dumps

### Risk 3 — Blurring harness work and bugfix work

Mitigation:

* keep this Story focused on strengthening the safety net first
* leave direct bugfixes for Stories 0044 and 0045 unless a minimal supporting
  adjustment is unavoidable for testability

## Planned Outcome

After this Story:

* refresh-path seams will have explicit regression coverage
* diagnostics endpoint failures like `/context` will be much harder to miss
* future refresh bugfix Stories will evolve against a stronger, more relevant
  safety net
