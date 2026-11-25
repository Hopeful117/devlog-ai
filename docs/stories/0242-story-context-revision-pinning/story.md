# Story 0242 - Story Context Revision Pinning

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Requested by the human on 2026-11-25 ("Revision pinning first") after the
[Campaign Q1 Gate 1 report](../../evaluation/devlog-ai-campaign-q1-gate-1-report.md).

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)

## Problem

A Story Context execution cannot be pinned to a revision. `RepositoryContextAdapter.resolveRevisionScope`:
- resolves a revision scope only when the execution has a Story;
- uses the Story `targetCommit`, else the target revision of the latest
  project-profile analysis, else the source's current revision.

Consequences observed in the gate:
- The campaign is frozen at `0673d1d3`, but A and C ran on `7ecc1680`, because
  the baseline refresh made `7ecc1680` the latest analysis target revision.
- C failed closed on `Q1_SOURCE_REVISION_MISMATCH`.
- A drifted silently: its harness adapter has no revision check.
- Investigation actions (`InvestigationCapabilityExecutionService`) rebuild
  their context without the pinned revision. They also reject actions without
  a Story, so C cannot investigate cases that have no Story (Q1-Q4, Q6, Q7).

## Design

1. **Requested revision**, optional, carried end to end:
   - `devlog_story_agent` (MCP) gains `revision`;
   - it flows through `DevLogStoryAgentRequest`, `AnalyzeStoryContextUseCase`,
     `StoryContextPreparationService` and the `EngineeringContextFacade` to
     `RepositoryContextAdapter`.
2. **Resolution priority:** requested revision, then Story target commit, then
   analysis target revision, then current revision.
   - With a requested revision, a scope is resolved even without a Story.
   - `revisionSource=REQUESTED_REVISION`.
   - The workspace must resolve to exactly the requested commit, otherwise the
     request fails closed.
3. **Submission identity** includes the requested revision, so the same request
   at another revision never reuses an earlier task.
4. **Investigation actions** rebuild context at the task's pinned revision
   (`InvestigationScope.revision`) and accept executions without a Story.
5. **Harness:**
   - A and C send the authorized source revision;
   - A gets the same revision check as C;
   - live B always reserves a fresh credential binding per authorization and
     ignores any static `DEVLOG_LIVE_B_TASK_ID`.

## Out of Scope

- Evidence content delivery (next Story).
- Re-freezing the campaign or oracles.

## Acceptance Criteria

1. A requested revision pins the canonical context and the projection, and
   `executionScope.resolvedRevision` equals it.
2. An unresolvable or mismatching revision fails closed before any task is created.
3. No revision requested keeps the existing behaviour.
4. The same request at two revisions yields two distinct submissions.
5. Investigation actions use the pinned revision and work without a Story.
6. The MCP tool forwards `revision`; the harness sends it, checks it for A and
   C, and never reuses a stale B binding.

## Implementation Report

### Files Changed
- Core:
  - `projectcontext/RepositoryContextAdapter.java`: revision-aware overload;
    the requested revision has priority, is resolved even without a Story and
    must match exactly (`REQUESTED_REVISION`);
  - `repositorycontext/RepositoryRevisionScope.java`: `SOURCE_REQUESTED`;
  - `engineeringcontext/EngineeringContextFacade(Impl).java`: revision-aware canonical context;
  - `storycontextanalysis/usecase/StoryContextPreparationService.java`,
    `PreparedStoryContext.java` (`revision`), `AnalyzeStoryContextUseCase.java`
    (prepare and execute overloads; the submission identity includes the
    revision only when pinned), `DevLogStoryAgentService.java`,
    `DevLogStoryAgentRequest.java` (`revision`, full commit only, normalized);
  - `storycontextanalysis/controller/StoryContextAgentProtocolController.java`
    (`SubmitRequest.revision`) and `DevLogStoryAgentController.java` (`AgentRequest.revision`);
  - `storycontextanalysis/investigation/InvestigationCapabilityExecutionService.java`
    and `usecase/StoryContextInvestigationActionService.java`: actions read the
    pinned scope revision; a Story is optional.
- MCP: `devlog_story_agent` gains `revision`; `DevlogProjectContextClient.SubmitRequest.revision`.
- AI Engine: `StoryAgentFreshness` accepts Core's `revisionMismatch`. Java
  already emitted it, but the Python contract (`extra="forbid"`) rejected it,
  so every pinned execution on an older revision failed prompt construction.
  Found by the live check.
- Harness (`live_execution.py`):
  - A and C send the authorized revision;
  - A and C completed executions must have run at it;
  - live B always reserves a fresh binding.
- Tests:
  - `RepositoryContextAdapterStoryAwareCandidateTest`: +4 (pin without Story,
    priority, fail-closed mismatch, unpinned unchanged);
  - `DevLogStoryAgentRequestRevisionTest` (new, 3);
  - `InvestigationCapabilityExecutionServiceTest` (stubs now carry the scope revision);
  - `DevLogStoryAgentToolTest`: +1 (revision forwarding);
  - harness: +2 (A revision drift fails closed, stale B task id ignored);
  - existing A adapter tests now pin.

### Behaviour notes
- The harness revision check applies to COMPLETED executions only. A task Core
  reports as FAILED keeps its FAILED status instead of being relabelled.
- Without a requested revision, behaviour is unchanged (same calls, same
  submission identities).

### Validation
- Backend `clean verify`: BUILD SUCCESS, 1963 tests, 0 failures.
- MCP server `verify`: BUILD SUCCESS, 171 tests, 0 failures.
- AI Engine: 945 passed, 2 skipped. ABC harness: 170 passed, 2 skipped.
- Live, through MCP (stack rebuilt from the worktree):
  - First pinned run (`ceab89b4`): Core pinned the scope to `0673d1d3`
    (`REQUESTED_REVISION`), but the AI Engine rejected the projection on
    `freshness.revisionMismatch`, which led to the contract fix above.
  - Second pinned run (`s0242-pin-check-2`): COMPLETED, with
    `executionScope.resolvedRevision=0673d1d3`, `revisionSource=REQUESTED_REVISION`
    and freshness `revisionConsistency=MISMATCH` (evidence `0673d1d3`, baseline
    `7ecc1680`), reported honestly.
  - The answer still abstains (`NOT_ESTABLISHED`, LOW), citing missing
    implementation bodies. This is the evidence-content defect, out of scope here.

### Pre-existing defect observed (not fixed)
- Callback race: a failure callback that arrives before Core commits the
  submitting transaction makes `ai_task_callback_rejections` violate its
  foreign key. Core answers 500, and the task ends `TIMED_OUT`
  (`AI_TASK_DEADLINE_EXCEEDED`) instead of FAILED with its real cause.
  Observed on `ceab89b4` (callback 150 ms after dispatch).
