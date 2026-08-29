# Story 0102 — Implementation Report

## Status

**IMPLEMENTATION_COMPLETED_AWAITING_HUMAN_REVIEW**

## Summary

Fixed two P0 regressions that prevented the Analysis product from functioning:

1. **Regression B (backend)**: Replaced unsafe generic `List<?>` → `List<EvidenceItem>` cast with eight type-safe mapping methods in `AnalysisResultQueryServiceImpl.curateCategory()`
2. **Regression A (frontend)**: Selectively restored Story 0099 objective-based launch contract, removing the accidentally reverted AnalysisType selector

## Regression B — Evidence Composition Fix

### RED Results (Observed)

All 8 category tests failed with `ClassCastException`:
- `FactItem` cannot be cast to `EvidenceItem`
- `ObservationItem` cannot be cast to `EvidenceItem`
- `PriorInsightItem` cannot be cast to `EvidenceItem`
- `ArchitectureKnowledgeItem` cannot be cast to `EvidenceItem`
- `EngineeringEventItem` cannot be cast to `EvidenceItem`
- `HumanContextItem` cannot be cast to `EvidenceItem`
- `EvolutionContextItem` cannot be cast to `EvidenceItem`
- `RepositoryEvidenceItem` cannot be cast to `EvidenceItem`

JSON serialization test failed with `JsonMappingException: object is not an instance of declaring class` — Jackson tried to access `layer()` on `FactItem` which doesn't have that method.

### Implementation Performed

Replaced terminal `curateCategory(Availability, int, List<?>)` overload with eight dedicated mapping methods:
- `curateCategory(FactsSection)` — maps `FactItem` → `EvidenceItem`
- `curateCategory(ObservationsSection)` — maps `ObservationItem` → `EvidenceItem`
- `curateCategory(PriorInsightsSection)` — maps `PriorInsightItem` → `EvidenceItem`
- `curateCategory(ArchitectureKnowledgeSection)` — maps `ArchitectureKnowledgeItem` → `EvidenceItem`
- `curateCategory(EngineeringEventsSection)` — maps `EngineeringEventItem` → `EvidenceItem`
- `curateCategory(HumanContextSection)` — maps `HumanContextItem` → `EvidenceItem`
- `curateCategory(EvolutionContextSection)` — maps `EvolutionContextItem` → `EvidenceItem`
- `curateCategory(RepositoryEvidenceSection)` — maps `RepositoryEvidenceItem` → `EvidenceItem`

Each method applies the approved semantic mapping:
- `layer`: provenance domain (e.g., `"FACT"`, `"OBSERVATION"`, `"VALIDATED_INSIGHT"`)
- `kind`: artifact type label (e.g., `fact.type`, `obs.ruleId`)
- `reference`: structured identity key (e.g., `"fact:" + id`)
- `summary`: human-readable content
- `occurredAt`: event timestamp
- `relatedReferences`: raw provenance links

### GREEN Results

All 12 tests pass (9 new + 3 existing):
- 8 category mapping tests: GREEN
- 1 JSON serialization test: GREEN
- 1 evidence preview limit test: GREEN
- 1 empty evidence test: GREEN (existing)
- 1 proposal resolution test: GREEN (existing)
- 1 in-progress analysis test: GREEN (existing)

## Regression A — Frontend Launch Contract Restoration

### RED Results (Observed)

4 tests failed proving current form violates Story 0099:
- `type` control exists (should not)
- `intentKey` control exists (should be `objective`)
- `type` emitted in request payload (should not)
- `intents` input exists (should be `objectives`)

### Implementation Performed

Selectively restored Story 0099 frontend:
- `analysis.models.ts`: Removed `LaunchableAnalysisType`, added `Source` interface, added `executionMode` to `IntentDefinition`, removed `type` from `CreateAnalysisRequest`, added `sourceId`
- `analysis-form.ts`: Restored objective-based form with `Objective` interface, scope logic, source selection
- `analysis-form.html`: Restored objective dropdown, scope badge, repository controls
- `project-analyses-section.ts`: Added `SourceService` injection, `objectives$`/`sources$`/`combined$` observables
- `project-analyses-section.html`: Restored `combined$` observable, objective/source loading states

### GREEN Results

All 256 frontend tests pass (including 4 new contract tests).

## Backend Launch Contract Protection Tests

Added 3 tests (GREEN immediately):
- `shouldRejectMismatchedTypeOnCreate` — `PROJECT_EVOLUTION` rejected
- `shouldDeriveArchitectureReviewWhenTypeIsOmitted` — null type derives `ARCHITECTURE_REVIEW`
- `shouldAcceptExplicitArchitectureReviewTypeOnCreate` — explicit `ARCHITECTURE_REVIEW` accepted

## Cross-Story Regression Verification

### Story 0099
- Objective-driven generic launch: RESTORED
- No editable AnalysisType: CONFIRMED
- Objective-fixed scope: CONFIRMED
- Executable Intent mapping: CONFIRMED
- Legacy type consistency: CONFIRMED

### Story 0100
- Canonical `/analyses/{id}/result` endpoint: UNCHANGED
- Result projection: UNCHANGED (except evidence mapping fix)
- Result states: UNCHANGED
- Evidence curation (top-5 limit): PRESERVED
- Angular canonical result page: UNCHANGED
- Polling behavior: UNCHANGED

### Story 0101
- Trusted artifact resolution: UNCHANGED
- AVAILABLE / UNAVAILABLE semantics: UNCHANGED
- Trusted-artifact navigation: UNCHANGED
- Decision provenance: UNCHANGED

## Quality Gates

### Backend
- Targeted RED/GREEN tests: PASS
- Complete backend test suite (1000 tests): PASS
- `mvn clean verify`: PASS
- JaCoCo gate: PASS

### Frontend
- Targeted RED/GREEN tests: PASS
- Complete frontend tests (256 tests): PASS
- Lint: PASS
- Production build: PASS
- Format check: PASS

### Repository
- `git diff --check`: CLEAN
- `git status --short`: Only expected files modified
- No unrelated modifications: CONFIRMED
- No unrelated staged files: CONFIRMED

## Files Modified

| File | Change |
|---|---|
| `backend/src/main/java/.../AnalysisResultQueryServiceImpl.java` | 8 type-safe mapping methods |
| `backend/src/test/java/.../AnalysisResultQueryServiceImplTest.java` | 10 new tests |
| `backend/src/test/java/.../AnalysisServiceTest.java` | 3 new tests |
| `frontend/src/app/features/analyses/analysis-form.ts` | Story 0099 restoration |
| `frontend/src/app/features/analyses/analysis-form.html` | Story 0099 restoration |
| `frontend/src/app/features/analyses/analysis-form.spec.ts` | 4 new contract tests |
| `frontend/src/app/features/analyses/analysis.models.ts` | Story 0099 models |
| `frontend/src/app/features/analyses/analysis.service.spec.ts` | Remove `type` from test |
| `frontend/src/app/features/analyses/project-analyses-section.ts` | Story 0099 restoration |
| `frontend/src/app/features/analyses/project-analyses-section.html` | Story 0099 restoration |
| `frontend/src/app/features/analyses/project-analyses-section.spec.ts` | Add `SourceService` mock |
