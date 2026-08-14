# Story 0064 — Confidence Thresholds And Assessment Filtering — Repository Analysis

## Current State

The assessment layer is fully operational:

* `MaintenanceAssessment` model (Story 0060)
* `DuplicateAmbiguityResolutionAgent` produces assessments for duplicate findings (Story 0061)
* `CrossSurfacePatternDetectionAgent` produces assessments for cross-surface patterns (Story 0062)
* Assessments are integrated into the maintenance lifecycle (Story 0063)

However, agents can produce low-confidence assessments that add noise rather than value.

## Gap Analysis

1. **No confidence filtering**: Assessments are persisted regardless of confidence level.
2. **No configurable thresholds**: The minimum confidence level is hardcoded or absent.
3. **No audit trail for suppressed assessments**: When assessments are suppressed (e.g., by the agent's internal logic), there's no system-level visibility.

## Files Requiring Changes

| Layer | File | Change |
|-------|------|--------|
| Backend Config | `MaintenanceAgentProperties.java` | New — configurable confidence threshold |
| Backend Properties | `application.properties` | Add threshold configuration property |
| Backend Service | `MaintenanceEvaluationServiceImpl.java` | Filter assessments before persistence |
| Backend Tests | `MaintenanceEvaluationServiceTest.java` | Update constructor, add threshold tests |
| Backend Tests | `MaintenanceAgentPropertiesTest.java` | New — threshold logic tests |
