# Story 0043 — Strengthen Understanding Refresh Test Harness — Engineering Report

## Status

Completed

## Story Recap

Story 0043 strengthened the DevLog test harness around project-understanding
refreshes after repeated runtime failures showed that our existing tests were
too component-local.

The point of the Story was not to fix every remaining bug directly.

It was to make the failure classes that escaped in Stories 0041 and 0042 far
more visible in automated verification before we continue with the next
bugfixes.

## Problem

Recent work exposed three distinct issues in the refresh journey:

* selected-knowledge grounding inconsistency;
* source `AnalysisContext` grounding inconsistency;
* diagnostics `/context` runtime failure.

The deeper issue was not only the bugs themselves.

It was that these bugs could escape despite a strong repository test suite,
because the suite did not assert enough at the seams where the refresh path
crosses layers.

## Implemented Outcome

Story 0043 added targeted regression coverage at those seams.

Updated test areas:

* `AnalysisDiagnosticsServiceTest`
* `AnalysisControllerWebMvcTest`
* `ProjectUnderstandingServiceTest`

Key additions:

* an explicit regression test for the current null-containing diagnostics
  context failure shape;
* controller-level coverage for `/api/v1/analyses/{id}/context` serialization
  when null values are present;
* a refresh-oriented multi-layer orchestration scenario covering analysis
  start, context build, selected-knowledge attachment, and AI-task submission.

## Why This Matters

This Story turns a painful lesson into executable safety:

* the diagnostics bug is no longer just a runtime surprise;
* the refresh path is no longer tested only as isolated service fragments;
* the suite now better reflects the real journey the product executes during a
  refresh.

In other words:

* Stories 0041 and 0042 improved correctness;
* Story 0043 improved our ability to notice this class of failure before it
  reaches the running system.

## What The Story Intentionally Does Not Do

This Story does **not**:

* fix diagnostics null-safety itself;
* fix the LLM timeout itself;
* provide full provider-live end-to-end automation.

Those remain the direct purpose of the follow-up bugfix Stories:

* [Story 0044](/home/ludo/Bureau/workspace/devlog-ai/docs/stories/0044-fix-analysis-context-diagnostics-null-safety/story.md:1)
* [Story 0045](/home/ludo/Bureau/workspace/devlog-ai/docs/stories/0045-fix-understanding-refresh-llm-timeout/story.md:1)

## Tests And Verification

Passed:

* `./mvnw -Dtest=AnalysisDiagnosticsServiceTest,AnalysisControllerWebMvcTest,ProjectUnderstandingServiceTest test`
* `./mvnw verify`
* JaCoCo coverage checks
* `git diff --check`

Quality gate result:

* backend tests: **587 PASS**
* JaCoCo: **PASS**

## Architectural Outcome

The repository now has better layered protection for refresh failures:

* Story 0042 protects source-context grounding;
* Story 0041 protects selected-knowledge grounding;
* Story 0043 protects the seams that expose and orchestrate those layers.

This is a healthier progression than trying to solve every future regression
with more isolated unit tests.

## Honest Limitations

The Story is intentionally narrower than a full harness overhaul.

Notably:

* it does not yet add a full invariant matrix across every lower-level test
  class;
* it does not yet simulate live provider timeout behavior;
* it still leaves the actual diagnostics null-safety fix and timeout fix to the
  next Stories.

That is acceptable because the highest-value seam coverage is now in place.

## Final Outcome

Completed.

Story 0043 strengthens the refresh-path safety net in the places where recent
bugs truly escaped, and creates a better foundation for Stories 0044 and 0045
to land against a more realistic and useful regression harness.

