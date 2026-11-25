# Story 0241 - Baseline Freshness Integrity and User-Bound Analysis Workflow

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Requested by the human on 2026-11-25 ("Fix both first"), after the Story 0240
live check. No commit, push or merge is implied.

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)

## Problem

The Story 0240 live check (analysis `743d9b72-4d2a-462d-8346-b4fca5ed6221`)
exposed two defects.

1. **Freshness integrity.**
   - `ProjectUnderstandingService` recorded a freshness checkpoint right after
     starting the workflow, using the in-memory claim entity.
   - `ProjectFreshnessPersistenceService.recordObservation` unconditionally
     stored `CURRENT` / `REFRESH_NOT_NEEDED` with that analysis as baseline.
   - The analysis had already FAILED, yet freshness reported `CURRENT` with
     zero reviewed knowledge.
   - This contradicts the epic objective of honest freshness. The normal
     baseline query (`findLatestComparable`) already excludes FAILED analyses.
2. **No user-bound credential for the analysis workflow.**
   - `AnalysisWorkflowServiceImpl.start` never issued a provider credential binding.
   - With the ChatGPT OAuth provider, the AI Engine resolves the binding by
     task id and failed with `AI_CREDENTIAL_BINDING_RESOLUTION_FAILED`.
   - The Story Context path already issues and registers bindings through
     `AiTaskExecutionAuthorizationService.authorizeAndIssue`.
   - Any project understanding started by an authenticated caller (an MCP
     agent or a UI user) could therefore not reach the provider.

## Design

### Freshness integrity
- `ProjectUnderstandingService` re-reads the analysis after the workflow
  starts and records the checkpoint only when it is not FAILED.
- `recordObservation` refuses a FAILED analysis as baseline and classifies
  from the latest comparable baseline instead (the existing `save` path).
- `latest` (read path, used by the freshness summary) reports a checkpoint
  whose baseline analysis failed afterwards from the latest comparable
  baseline. This is a read-only correction; the next freshness check corrects
  the stored row.

### User-bound analysis workflow
- `AnalysisWorkflowService.start(analysisId, principal)` is added;
  `start(analysisId)` delegates with no principal and keeps its current behaviour.
- With a principal, after the AI task is created and before submission, Core:
  1. calls `authorizeAndIssueBinding(principal, task)`, a binding-only variant
     of `authorizeAndIssue` that checks the principal's AI configuration and
     issues the binding without writing provider and model on the task
     (`ck_ai_task_prompt_execution_identity` requires those to be recorded
     atomically with the prompt identity at callback time);
  2. registers the binding with the AI Engine.
  This is the same mechanism as the Story Context path. It fails closed when
  user-bound execution is not configured or the principal's AI configuration
  is not READY, and the analysis is then marked FAILED.
- `ProjectUnderstandingService.execute(projectId, request, principal)` is
  added. The controller resolves the caller with `AuthenticatedPrincipalResolver`.
  The maintenance remediation path keeps the principal-less overload.

## Out of Scope

- Changing which analyses count as baseline (`findLatestComparable` still
  includes IN_PROGRESS analyses, as before).
- The generic `POST /api/v1/analyses/{id}/workflow` endpoint (still principal-less).
- Embedding service availability (HTTP 503 observed; semantic retrieval
  degrades without failing the analysis).

## Acceptance Criteria

1. A FAILED analysis is never recorded or reported as the freshness baseline.
2. A checkpoint whose baseline failed later is reported from the comparable
   baseline, without writing during the read.
3. An authenticated project understanding issues and registers the binding
   before AI Engine submission, and fails closed without configuration.
4. Existing principal-less callers behave as before.
5. Live: `refresh_project_baseline` through MCP reaches the provider, and the
   freshness summary is honest during and after the run.

## Implementation Report

### Files Changed
- `backend/.../analysis/workflow/AnalysisWorkflowService.java`, `AnalysisWorkflowServiceImpl.java`
- `backend/.../projectunderstanding/ProjectUnderstandingService.java`, `ProjectUnderstandingController.java`
- `backend/.../projectfreshness/ProjectFreshnessPersistenceService.java`
- `backend/.../aicredentialbinding/service/AiTaskExecutionAuthorizationService.java` (binding-only variant)
- Tests:
  - `ProjectUnderstandingServiceTest`: failed analysis not recorded, principal forwarded;
  - `ProjectFreshnessPersistenceServiceTest`: failed baseline refused, read reconciliation;
  - `AnalysisWorkflowServiceTest`: binding before submission, fail-closed;
  - `ProjectUnderstandingControllerWebMvcTest`: principal resolution.

### Validation
- Focused tests (understanding, freshness, workflow, task authorization): 71 tests, 0 failures.
- Full backend `./backend/mvnw -pl backend -am clean verify -B`: BUILD SUCCESS, 1956 tests, 0 failures.
- Live, through MCP only (backend rebuilt from the worktree):
  - The freshness summary no longer reports the failed analysis `743d9b72`.
    It shows `PARTIALLY_FRESH` / `REFRESH_RECOMMENDED`, with the 2026-09-22
    baseline (7 accepted proposals).
  - First user-bound attempt (`4d86f469`): the analysis failed closed on the
    execution-identity constraint; freshness was not advanced. This led to the
    binding-only variant.
  - `refresh_project_baseline` then started analysis
    `278163c1-0352-4ff9-b660-fd08ae61ed6f`, which reached the provider and
    COMPLETED in 37 s.
  - Freshness is `CURRENT` on `7ecc1680` with 5 proposals PENDING human review.

### Observations
- While an understanding analysis is IN_PROGRESS, freshness already reports
  `CURRENT` (unchanged semantics: `findLatestComparable` includes IN_PROGRESS
  analyses).
- `CURRENT` does not mean reviewed: the 5 new proposals are still PENDING
  (ADR-006). The `review` counts make this visible.
- Embedding calls still return HTTP 503; semantic retrieval degrades without
  failing the analysis.
