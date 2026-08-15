# Story 0073 — Remediation Actions and Bugfixes — Implementation Plan

## Phase 1: Overlap Review Remediation

### Backend
1. Add `resolveOverlapReview()` to `KnowledgeDeduplicationService` interface
2. Implement in `KnowledgeDeduplicationServiceImpl` with
   `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` issue type validation
3. Add `POST /actions/resolve-overlap` endpoint in controller
4. Refactor `KnowledgeDeduplicationServiceImpl`: extract `mergeAndResolve()`
   private method shared by `mergeExactDuplicate`, `resolveSemanticDuplicate`,
   and `resolveOverlapReview`

### Frontend
5. Add `resolveOverlapReview()` to `MaintenanceFindingService`
6. Add `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` to `hasRemediation()` and
   `remediationLabel()` in component
7. Add `resolveOverlapReview` call to `remediate()` callMap
8. Add mock and test for overlap remediation flow

## Phase 2: Bugfixes

### Bug 1: Refresh understanding loop
9. In `MaintenanceRemediationServiceImpl.refreshProjectUnderstanding`: remove
   `allFresh` boolean and `ConflictException` guard — log warnings for failed
   sources and proceed

### Bug 2: Refresh understanding null sourceId
10. Change single `understandingService.execute()` call to iterate over all
    active sources, calling execute per-source with
    `new ProjectUnderstandingRequest(source.getId(), null, null)`
11. Catch per-source failures gracefully (log + continue)

### Bug 3: Dismiss requires comment
12. In component `dismiss()` method: remove `requireComment = true` (default
    is false)

## Phase 3: Progress Indicator

13. Add `remediationProgressLabel()` method to component with descriptive
    labels per finding type
14. Add animated progress bar HTML in remediation path template
15. Add CSS animation (`maintenance-progress-slide` keyframes)
16. Add `role="status"` and `aria-label` for accessibility

## Phase 4: Tests

17. Backend: update `shouldAbortWhenFreshnessCheckFails` →
    `shouldContinueWhenFreshnessCheckFails`
18. Backend: update `shouldThrowWhenUnderstandingFails` →
    `shouldContinueWhenUnderstandingFailsForOneSource`
19. Backend: add 5 tests for `resolveOverlapReview` (success, wrong type,
    no UUIDs, supersede failure, not found)
20. Backend: add 1 controller test for `resolve-overlap` endpoint
21. Frontend: add test for overlap remediation button flow
22. Frontend: update mock provider with all remediation service methods

## Validation

* `./mvnw test -Dtest=KnowledgeDeduplicationServiceTest,MaintenanceRemediationServiceTest,MaintenanceFindingControllerWebMvcTest`
* `npm run lint && npm run format:check && npx ng test --watch=false`
* Docker compose up — verify Flyway migration and container health
* Manual: click "Resolve overlap", "Refresh understanding", "Dismiss" on live
  maintenance findings
