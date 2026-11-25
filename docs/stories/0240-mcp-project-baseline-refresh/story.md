# Story 0240 - MCP Project Baseline Refresh

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Requested by the human on 2026-11-25: "the flow should be fully drivable
through MCP; if it is not, that is a gap to fill." No commit, push or merge
is implied by this Story.

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)

## Problem

The live evaluation flow (Stories 0228, 0229, 0236, 0237, 0239) is drivable
through MCP: preparation, authorization request and approval, consumption,
Story resolution, the Story Context projection and execution. One prerequisite
is not.

Refreshing the project baseline (the Project Understanding execution that
moves freshness from `STALE`/`PARTIALLY_FRESH` towards `CURRENT`) is only
reachable through `POST /api/v1/projects/{projectId}/understanding-executions`
and the frontend. An agent must therefore ask a human to sign in to the UI,
which breaks the MCP-only workflow observed on 2026-11-25.

## Goals

- Expose the baseline refresh as an MCP tool.
- Expose a bounded status read so an agent can follow the analysis it started
  and the resulting project freshness.
- Keep Core authoritative: MCP is a thin transport adapter (ADR-067).

## Design

- `refresh_project_baseline(projectSlug, targetRevision?)`:
  - resolves the project and its single active source through the existing
    `ResourceSupport` helpers;
  - calls the Core understanding execution with the caller's bearer token;
  - returns the Core response (analysis id, status, outcome `STARTED` or `REUSED`).
- `get_project_baseline_status(projectSlug, analysisId)`:
  - reads the analysis and rejects an analysis of another project;
  - returns a bounded projection (id, type, status, intent, timestamps,
    selected source revision);
  - adds the Core project freshness summary (Story 0238).
- Authentication: the same `McpAuthenticatedPrincipalProvider` bearer path as
  `sync_project_history`. An unauthenticated call is rejected. The Core
  endpoint keeps its existing authorization; this Story neither widens nor
  narrows it.

## Out of Scope

- Changing the Project Understanding workflow, its proposals or their human
  validation (ADR-006). The resulting proposals still require review through
  the proposal tools.
- Changing Core authorization rules.

## Acceptance Criteria

1. `refresh_project_baseline` starts (or reuses) a Project Understanding
   execution for the active source and forwards `targetRevision` when given.
2. `get_project_baseline_status` returns the bounded analysis projection and
   the freshness summary. It rejects an analysis that belongs to another project.
3. Both tools refuse unauthenticated calls and map Core errors through
   `CoreCallPolicy`.
4. Unit tests cover start, revision forwarding, the status projection, the
   cross-project rejection and unauthenticated refusal.
5. `docs/mcp-tools.md` documents both tools.

## Implementation Report

### Files Changed
- `mcp-server/.../client/DevlogAdminClient.java`: understanding execution, analysis read, freshness summary.
- `mcp-server/.../tool/ProjectBaselineTool.java` (new): the two tools.
- `mcp-server/.../tool/ProjectBaselineToolTest.java` (new, 6 tests).
- `mcp-server/.../ServerInfoResourceTest.java`: tool list.
- `mcp-server/.../tool/PrepareLiveEvaluationToolTest.java`: pre-existing
  compilation failure fixed. Since #244 the test passed a removed
  `McpHumanConfirmationSigner` argument, so the MCP test module did not compile.
- `docs/mcp-tools.md`.

### Validation
- MCP server `./mvnw -B verify`: 170 tests, 0 failures.
- Live, through MCP (harness MCP client with the rebuilt jar):
  `refresh_project_baseline` started analysis `743d9b72-4d2a-462d-8346-b4fca5ed6221`;
  `get_project_baseline_status` reported its terminal status.

### Findings from the live check (not fixed here)
1. The started analysis FAILED in 4 s. The AI Engine logged
   `AI_CREDENTIAL_BINDING_RESOLUTION_FAILED` (provider `chatgpt`) for the
   `INSIGHT_GENERATION` task, and embeddings returned 503. The analysis
   workflow started by an MCP agent principal has no ChatGPT credential
   binding. This is a second MCP-only gap.
2. Freshness integrity defect: `ProjectUnderstandingService` calls
   `recordObservedBaseline` right after starting the workflow, and
   `ProjectFreshnessPersistenceService.recordObservation` unconditionally
   stores `CURRENT` with that analysis as baseline. The failed analysis now
   makes freshness report `CURRENT` / `REFRESH_NOT_NEEDED` with
   `review.total=0`, which is false.
