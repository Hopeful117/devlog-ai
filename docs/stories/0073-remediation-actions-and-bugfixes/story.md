# Story 0073 — Remediation Actions and Bugfixes

## Status

Done

## Priority

High

## Objective

Complete the remediation pipeline for all maintenance finding types by adding
one-click corrective actions, resolving the overlap review gap, and fixing
critical runtime bugs that prevented refresh-understanding and dismiss from
working correctly.

## Motivation

Stories 0068–0072 built the remediation infrastructure (services, endpoints,
frontend buttons) but several issues remained:

1. **No overlap review remediation** — `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`
   findings only offered Acknowledge/Dismiss/Resolve with no way to trigger the
   actual deduplication logic, leaving users stuck
2. **Refresh understanding loop** — `refreshProjectUnderstanding` threw
   `ConflictException("Freshness check failed")` when ANY source freshness
   check failed, causing a perceived infinite loop on every click
3. **Refresh understanding null sourceId** — the method passed `null` as
   `sourceId` to `understandingService.execute()`, but
   `ProjectUnderstandingRequest.sourceId` is `@NotNull`, causing a 409 error
4. **Dismiss requires comment** — `dismiss()` passed `requireComment = true`
   but the remediation path template had no textarea, making dismiss impossible
5. **No progress feedback** — users had no visual indication that a remediation
   action was running

## Scope

### In Scope

1. Add `POST /actions/resolve-overlap` endpoint with dedicated
   `resolveOverlapReview()` service method
2. Remove strict `allFresh` guard in `refreshProjectUnderstanding` — proceed
   with understanding refresh even when some freshness checks fail
3. Fix `refreshProjectUnderstanding` to iterate over all active sources
   instead of passing null sourceId
4. Remove `requireComment = true` from `dismiss()` so dismiss works without a
   comment
5. Add animated progress bar with descriptive label during remediation actions
6. Refactor `KnowledgeDeduplicationServiceImpl` to extract shared
   `mergeAndResolve()` private method (DRY)
7. Add comprehensive unit tests for all changes

### Out of Scope

* Side-by-side review UI for overlapping insights
* Real-time progress streaming from backend
* Undo deduplication
* Bulk remediation of multiple findings

## Constraints

* Must not break existing Acknowledge/Dismiss/Resolve workflow
* Must maintain backward compatibility with existing endpoints
* Must pass all existing tests

## Acceptance Criteria

* AC-1: User can click "Resolve overlap" on `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`
  findings and the deduplication logic executes
* AC-2: Clicking "Refresh understanding" succeeds even when some freshness
  checks fail
* AC-3: Clicking "Dismiss" works without requiring a comment
* AC-4: An animated progress bar with descriptive label appears during
  remediation actions
* AC-5: All backend and frontend tests pass

## Dependencies

* Story 0068–0072: Context Maintenance remediation infrastructure
* `KnowledgeDeduplicationService` — existing merge/resolve methods
* `ProjectUnderstandingService` — per-source understanding execution
