# Implementation Report

## Story

**Story 0051** — Reduce Context Noise Before Enrichment

## Overview

Story 0051 was implemented to address two concrete issues observed during live DevLog usage:

1. `project-state` exposed a noisy set of repetitive `proposedProposals`, making the active context harder to read for both humans and agents.
2. `engineering-story-context` could still fail with `500` when the projected agent payload could not fit within the configured projection budget.

The implementation keeps the existing API contracts intact while improving deterministic reduction behavior on `project-state` and making agent-context projection degrade gracefully instead of failing on oversized repository context payloads.

## Modified Files

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionService.java`
  Adds stronger projection degradation:
  * summary compaction
  * minimal single-evidence fallback
  * final empty-evidence fallback instead of avoidable `500`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/AgentRepositoryContext.java`
  Adds support helpers used by projection degradation, including compact/minimal evidence transformations.
* `backend/src/main/java/com/hopeful117/devlogai/projectstate/service/ProjectStateProjectionServiceImpl.java`
  Integrates proposal noise reduction into `activeWork` and `pendingActions`.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionServiceTest.java`
  Covers summary compaction and tight-budget fallback behavior.
* `backend/src/test/java/com/hopeful117/devlogai/projectstate/service/ProjectStateProjectionServiceTest.java`
  Verifies the projection service uses the reduced proposal lists.

## New Files

* `backend/src/main/java/com/hopeful117/devlogai/projectstate/ProjectStateProposalNoiseReducer.java`
  Deterministic reducer that clusters semantically repetitive proposals and performs a final strict title-based deduplication pass.
* `backend/src/test/java/com/hopeful117/devlogai/projectstate/ProjectStateProposalNoiseReducerTest.java`
  Covers exact duplicates, whitespace/case variants, near-duplicate LLM variants, and exact-title duplicates with differing summaries.
* `docs/stories/0051-reduce-context-noise-before-enrichment/implementation-report.md`
  This implementation record.

## Validation

```text
Command: ./mvnw -Dtest=ProjectStateProposalNoiseReducerTest,AgentContextProjectionServiceTest,ProjectStateProjectionServiceTest test
Result: Passed — 16 tests, 0 failures, 0 errors.

Command: curl http://localhost:18080/api/v1/projects/f3d56247-aada-4a76-982b-e6802c0b309c/state
Result: Passed — activeWork.proposedProposals reduced from 31 noisy entries to 12 unique titles in live runtime.

Command: curl -X POST http://localhost:18080/api/projects/f3d56247-aada-4a76-982b-e6802c0b309c/engineering-story-context
Result: Passed — endpoint now returns HTTP 200 in live runtime.

Command: ./mvnw clean verify -B
Result: Passed — 611 tests, 0 failures, 0 errors; JaCoCo checks passed.
```

## Live Outcome

After rebuilding the backend container:

* `project-state` returned a materially cleaner active proposal set, with duplicate exact titles removed and repetitive near-duplicates collapsed.
* `engineering-story-context` returned `200` instead of `500`.
* Under the observed oversized repository-context conditions, the endpoint degraded to a valid agent payload with projection warnings and `repositoryEvidenceCount=0` instead of failing the request.

## Deviations

The original implementation attempt only compacted to a minimal evidence form and still failed in live runtime for the real project. During validation, the implementation was extended with a final empty-evidence fallback so the endpoint returns a bounded payload instead of raising an exception.

The proposal reducer was also strengthened after live inspection showed that a first semantic pass still left an exact-title duplicate. A deterministic strict-title merge pass was added and covered by tests.

## Remaining Work

None within the approved story scope.

## Recommendation

Ready for Review
