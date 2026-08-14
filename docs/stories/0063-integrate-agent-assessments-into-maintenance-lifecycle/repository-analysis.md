# Story 0063 — Integrate Agent Assessments Into Maintenance Lifecycle — Repository Analysis

## Current State

The maintenance lifecycle is fully operational:

* `MaintenanceFinding` entities with CRUD, status transitions, and audit trail (Stories 0053, 0056)
* `MaintenanceAssessment` model for agent assessments (Story 0060)
* `DuplicateAmbiguityResolutionAgent` (Story 0061)
* `CrossSurfacePatternDetectionAgent` (Story 0062)
* `MaintenanceEvaluationService` integrates both agents, attaching assessments to findings during evaluation

## Gap Analysis

Agent assessments exist in the database but are invisible to consumers:

1. **API gap**: `MaintenanceFindingResponse` does not include assessments — callers cannot see them.
2. **Frontend gap**: The maintenance section displays findings but has no assessment rendering.
3. **Service gap**: `MaintenanceFindingServiceImpl.getByProject()` fetches findings but does not enrich them with assessments from `MaintenanceAssessmentRepository`.

## Files Requiring Changes

| Layer | File | Change |
|-------|------|--------|
| Backend DTO | `MaintenanceFindingResponse.java` | Add `List<MaintenanceAssessmentResponse> assessments` field |
| Backend Service | `MaintenanceFindingServiceImpl.java` | Enrich findings with assessments in `getByProject()` |
| Backend Repository | `MaintenanceAssessmentRepository.java` | Add `findByFindingIdIn()` batch query |
| Backend Tests | `MaintenanceFindingServiceTest.java` | Update mocks and assertions for assessments |
| Backend Tests | `MaintenanceFindingControllerWebMvcTest.java` | Update DTO constructors |
| Frontend Models | `maintenance-finding.models.ts` | Add `MaintenanceAssessment` interface |
| Frontend Template | `project-maintenance-section.html` | Add assessment display block |
| Frontend Component | `project-maintenance-section.ts` | Add assessment helper methods |
| Frontend Styles | `project-maintenance-section.scss` | Add assessment CSS classes |
