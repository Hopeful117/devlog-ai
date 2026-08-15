# Story 0073 — Remediation Actions and Bugfixes — Engineering Report

## Status

Reported

## Story

| Field | Value |
|-------|-------|
| Number | 0073 |
| Title | Remediation Actions and Bugfixes |
| Status | Done |
| Acceptance Criteria | 5/5 satisfied |

## Scope Delivered

### Implemented

* `resolveOverlapReview()` — dedicated backend method for
  `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` findings
* `POST /actions/resolve-overlap` endpoint
* `resolveOverlapReview()` frontend service method
* `hasRemediation()` + `remediationLabel()` updated for overlap review
* `mergeAndResolve()` shared private method (DRY refactor)
* `refreshProjectUnderstanding` per-source iteration with proper sourceId
* `refreshProjectUnderstanding` resilience to freshness check failures
* `dismiss()` no longer requires comment
* Animated progress bar with `remediationProgressLabel()`
* 53 backend unit tests (across 3 test classes)
* 1 frontend unit test for overlap remediation

### Deferred

* Side-by-side review UI for overlapping insights
* Real-time progress streaming (SSE/WebSocket)
* Undo deduplication
* Bulk remediation

## Design Outcome

### Boundary Retained

The `KnowledgeDeduplicationService` remains the single entry point for all
deduplication operations. The `MaintenanceRemediationService` remains the
entry point for projection/understanding remediation. The controller maps
URL routes to service methods without business logic.

The `mergeAndResolve()` extraction keeps the shared deduplication logic in
one place without exposing it as a public API.

### Why This Matters

With this story, every maintenance finding type has a working remediation
action. The maintenance UI transitions from a read-only dashboard to an
actionable operational tool.

## Implementation Summary

### Added

| File | Change |
|------|--------|
| `KnowledgeDeduplicationService.java` | Added `resolveOverlapReview()` |
| `KnowledgeDeduplicationServiceImpl.java` | Implemented `resolveOverlapReview()`, extracted `mergeAndResolve()` |
| `MaintenanceFindingController.java` | Added `POST /actions/resolve-overlap` |
| `MaintenanceRemediationServiceImpl.java` | Per-source understanding iteration, removed allFresh guard |
| `project-maintenance-section.ts` | `remediationProgressLabel()`, updated `hasRemediation()`/`remediationLabel()`/`remediate()`/`dismiss()` |
| `project-maintenance-section.html` | Progress bar template |
| `project-maintenance-section.scss` | `.maintenance-progress` styles + animation |
| `maintenance-finding.service.ts` | `resolveOverlapReview()` |
| `KnowledgeDeduplicationServiceTest.java` | 5 new tests for `resolveOverlapReview` |
| `MaintenanceRemediationServiceTest.java` | Updated 3 tests for per-source iteration |
| `MaintenanceFindingControllerWebMvcTest.java` | 2 new tests for overlap endpoint |
| `project-maintenance-section.spec.ts` | 1 new test for overlap remediation |

## Current Dataset Outcome

Before this story, the devlog-ai project had 7 `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`
findings with no actionable remediation. After this story, clicking "Resolve
overlap" on any of these findings triggers the deduplication logic and resolves
the finding.

## Quality Gates

* backend tests: PASS (53 new/updated)
* backend verify: PASS
* frontend lint: PASS
* frontend format: PASS
* frontend tests: PASS (205)
* Docker startup: PASS (Flyway V42 applied)
* Manual smoke test: PASS

## Documentation Outcome

This story folder is the canonical documentation. No additional canonical
docs require updating.

## Limitations

1. Progress bar is indeterminate — no percentage or ETA
2. Overlap resolution cannot be undone
3. Each cluster must be resolved individually (no bulk action)
4. Understanding refresh per-source may be slow for projects with many sources

## Next Architectural Questions

1. Should remediation actions be async (queued) to avoid blocking the HTTP
   request?
2. Should there be a remediation audit log separate from finding action history?
3. Can the progress bar be driven by backend events (SSE) for real feedback?
