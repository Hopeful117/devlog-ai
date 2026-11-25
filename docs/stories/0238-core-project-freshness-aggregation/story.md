# Story 0238 - Core Project Freshness Aggregation

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Design approved and implementation authorized by the human on 2026-11-25,
with D1 and D2 taken as proposed. No provider call, live evaluation, commit,
push or merge was performed.

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)

## Governing Artifacts

- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- Story 0219 - Freshness, Scope and Provenance Explicitness
- Story 0237 - ABC Harness Validity (AC-7 freshness gate)
- [Q1 Live Run 28 Assessment](../../evaluation/devlog-ai-q1-live-run28-assessment-v1.md)

## Problem

Every Story Context response reports top-level freshness `UNKNOWN`, even
when Core has classified every checked source.

Root cause, verified in the repository:

1. `EngineeringContextFacadeImpl` stores `freshnessService.summary(projectId)`
   (a `ProjectFreshnessSummary`) under `freshness.summary`.
2. `ProjectFreshnessSummary` carries `version`, `projectId`,
   `checkedSources`, `uncheckedSourceCount` and `truncated`, but **no
   aggregated status**.
3. `StoryContextAgentProjectionV1.freshness()` converts the summary to a map
   and looks for `state`, then `status`, then `freshness.status`. None
   exists, so the state falls back to `UNKNOWN`.

Observed effect (run 28 and the 2026-11-25 DevLog preflight): the single
checked source is `PARTIALLY_FRESH` with guidance `REFRESH_RECOMMENDED`, while
the response states `freshness.status=UNKNOWN`.

Consequences:

- agents cannot tell "unknown" from "known and partially fresh", which defeats
  the epic objective of explicit freshness;
- the Story 0237 freshness gate blocks every live run;
- the epic closure criterion "freshness not UNKNOWN" is unreachable.

## Goals

- Core computes one authoritative aggregated project freshness status from
  the per-source classifications it already owns.
- The Story Context projection and the EngineeringContext expose that status
  instead of falling back to `UNKNOWN`.
- `UNKNOWN` remains a genuine state, used only when Core cannot classify.

## Proposed Design

Add to `ProjectFreshnessSummary` an aggregated `status` (a
`ProjectFreshnessStatus`) and `guidance` (a `ProjectRefreshGuidance`),
computed once in `ProjectFreshnessService.summary()` by a pure, unit-tested
aggregation function owned by `projectfreshness`. Consumers read the field and
never recompute it.

Proposed aggregation rule (decision D1, to be confirmed). Take the worst
checked status, using this severity order:

`UNKNOWN` > `NO_BASELINE` > `STALE` > `PARTIALLY_FRESH` > `CURRENT`

with these rules:

- no checked source, or `uncheckedSourceCount > 0`, or `truncated=true`:
  the result can never be `CURRENT`. It is at best `PARTIALLY_FRESH`, or
  `UNKNOWN` when nothing was checked;
- guidance is the guidance of the source that decided the status.

`StoryContextAgentProjectionV1.freshness()` reads `summary.status()` when the
summary is a `ProjectFreshnessSummary`. The existing string fallback stays for
the other shapes. `revisionConsistency` and `warnings` are unchanged.

## In Scope

- `backend/.../projectfreshness/ProjectFreshnessSummary.java` and
  `ProjectFreshnessService.java` (aggregation).
- `backend/.../storycontextanalysis/usecase/StoryContextAgentProjectionV1.java`
  (read the aggregated status).
- Other direct consumers of `ProjectFreshnessSummary` only for compilation and
  serialization compatibility: `RepositoryContextAdapter`,
  `AgentEngineeringStoryContext`, `MaintenanceEvaluationServiceImpl`.
- Unit and integration tests. DTO/JSON compatibility is additive.

## Out of Scope

- Changing per-source classification (`ProjectFreshnessClassifier`).
- Refreshing the project baseline (an operational step, not code).
- Making stale context look fresh, or dropping `PROJECT_CONTEXT_STALE`.
- Python AI Engine or evaluation harness changes.
- Budget, retrieval or prompt changes.

## Acceptance Criteria

### AC-1 - Aggregated status exists
`ProjectFreshnessSummary` exposes `status` and `guidance`, computed by Core.

### AC-2 - Aggregation rule
Unit tests cover:
- each single-source status;
- mixed statuses (the worst one wins);
- no checked source (`UNKNOWN`);
- unchecked or truncated sources (never `CURRENT`).

### AC-3 - Projection uses the Core status
A Story Context projection with one `PARTIALLY_FRESH` source reports
`freshness.status=PARTIALLY_FRESH` and `freshness.state=PARTIALLY_FRESH`.
A genuinely unclassifiable source still reports `UNKNOWN`.

### AC-4 - Compatibility
JSON consumers keep receiving every existing field. New fields are additive.
Response digests that embed freshness are recomputed, never reused.

### AC-5 - Single authority
No consumer recomputes the aggregation. The Python AI Engine and the
evaluation harness only read the Core value.

### AC-6 - Validation
Focused tests pass, plus:
- `ProjectFreshnessServiceTest`;
- `ProjectFreshnessClassifierTest`;
- the `StoryContextAgentProjectionV1` tests;
- the `EngineeringContextFacade` tests;
- `./backend/mvnw -pl backend -am clean verify -B`.

## Decisions Required

- D1: confirm the severity order, in particular whether `NO_BASELINE` should
  rank above `UNKNOWN`.
- D2: confirm that unchecked or truncated sources cap the result at
  `PARTIALLY_FRESH` rather than forcing `UNKNOWN`.

## Operational Follow-up (not code)

Even with this fix, the project baseline dates from 2026-09-22 (status
`PARTIALLY_FRESH`). Before the next live evaluation, refresh the baseline
analysis so that the observed freshness reflects the evaluated revision.

## Workflow and Gates

1. Human design approval (D1, D2).
2. Explicit implementation authorization.
3. Implement, validate, review diff.
4. Human acceptance before Story 0237's freshness gate is relied upon.

## Implementation Report

### Decisions applied
- D1 as proposed: `UNKNOWN > NO_BASELINE > STALE > PARTIALLY_FRESH > CURRENT`.
- D2 as proposed: unchecked or truncated sources cap `CURRENT` at
  `PARTIALLY_FRESH` (guidance `VERIFY_BASELINE`); no checked source gives
  `UNKNOWN`.
- D3 (implementation-level, discovered): the projection contract shared with
  the Python AI Engine (`StoryAgentFreshness`, `extra="forbid"`) only accepts
  `state` in `FRESH | STALE | UNKNOWN | NOT_ESTABLISHED`. Emitting the raw
  Core status would make the AI Engine reject every projection. The
  projection therefore keeps the precise Core value in `status` and maps
  `state`: `CURRENT -> FRESH`, `PARTIALLY_FRESH | STALE -> STALE`,
  `NO_BASELINE -> NOT_ESTABLISHED`, otherwise `UNKNOWN`. `guidance` is kept on
  `ProjectFreshnessSummary` (REST, EngineeringContext) but not added to the
  Story Context projection. No Python change.

### Files Changed
- `backend/.../projectfreshness/ProjectFreshnessAggregation.java` (new): pure aggregation.
- `backend/.../projectfreshness/ProjectFreshnessSummary.java`: `status` and
  `guidance` components, always the Core aggregation; a conflicting supplied
  value is rejected; the 5-argument constructor is preserved.
- `backend/.../storycontextanalysis/usecase/StoryContextAgentProjectionV1.java`:
  reads `summary.status()`, maps `state` to the contract, drops `guidance`.
- Tests: `ProjectFreshnessAggregationTest` (new, 11 cases), three projection
  tests in `AnalyzeStoryContextUseCaseTest`.

### Effect
With the run 28 data (one `PARTIALLY_FRESH` source), the projection now
reports `status=PARTIALLY_FRESH`, `state=STALE` instead of `UNKNOWN`. The
Story 0237 freshness gate passes on `STALE` and still blocks on `UNKNOWN`,
`NOT_ESTABLISHED` and `NO_BASELINE`.

### Validation
- Focused: `ProjectFreshness*Test`, `AnalyzeStoryContextUseCaseTest`,
  `EngineeringContextFacadeImplTest`, `MaintenanceEvaluation*Test`: 86 tests, 0 failures.
- Python contract check: the new freshness shape validates against
  `StoryAgentFreshness`; a raw `PARTIALLY_FRESH` state or a `guidance` field
  would be rejected (confirming D3).
- Full `./backend/mvnw -pl backend -am clean verify -B`: BUILD SUCCESS, 1931 tests, 0 failures, 0 errors (Surefire logged its usual forked-JVM shutdown warning).
- Not done: a live check against the running stack (the Docker services were
  not rebuilt).
