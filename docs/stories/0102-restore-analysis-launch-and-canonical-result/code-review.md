# Story 0102 — Code Review

## Status

**CODE_REVIEW_AWAITING_HUMAN_REVIEW**

## Scope

11 files modified across backend and frontend to fix two P0 regressions.

## Backend Changes

### AnalysisResultQueryServiceImpl.java

**Change**: Replaced unsafe generic cast with eight type-safe mapping methods.

**Review Notes**:
- Each mapping method follows the same pattern: null/availability check → stream → map → collect
- `EVIDENCE_PREVIEW_LIMIT = 5` preserved in each method
- No reflection, no `ObjectMapper.convertValue`, no unchecked casts
- Each method is self-contained and independently testable
- The `EngineeringEventsSection` mapping constructs `relatedReferences` from `sourceId`/`baseCommit`/`targetCommit` — matches the format used by `GitHistoryContextCollector`
- The `EvolutionContextSection` mapping uses `commitDiff.evidenceReferences` for `relatedReferences` — consistent with the MCP contract

**Potential Concerns**:
- The eight methods share structural similarity but operate on different types. A generic approach with type-safe dispatch could reduce duplication, but the current explicit approach is clearer and avoids the original defect pattern.
- Each method creates a new `EvidenceItem` record per element — acceptable for preview-limited collections (max 5).

### AnalysisResultQueryServiceImplTest.java

**Change**: Added 10 new tests exercising the full `getResult()` → `buildEvidence()` → `curateCategory()` path.

**Review Notes**:
- All tests mock `AiTaskSelectedEvidenceService` to return `State.AVAILABLE` with realistic typed items
- Tests assert all six `EvidenceItem` fields per category
- JSON serialization test uses `ObjectMapper` with `JavaTimeModule` — matches production serialization setup
- Evidence preview limit test verifies only 5 items curated from 8-item section
- Helper methods (`stubMinimalCompletedAnalysis`, `minimalTaskIdentity`, `minimalSnapshotMetadata`) reduce test boilerplate

### AnalysisServiceTest.java

**Change**: Added 3 backend launch contract protection tests.

**Review Notes**:
- Tests are GREEN immediately — backend behavior is correct
- `shouldRejectMismatchedTypeOnCreate` verifies `PROJECT_EVOLUTION` rejection
- `shouldDeriveArchitectureReviewWhenTypeIsOmitted` verifies null type derivation
- `shouldAcceptExplicitArchitectureReviewTypeOnCreate` verifies explicit match acceptance
- Tests use `UserGuidance` to ensure `objectMapper.convertValue` path is exercised

## Frontend Changes

### analysis.models.ts

**Change**: Removed `LaunchableAnalysisType`, added `Source` interface, added `executionMode` to `IntentDefinition`, removed `type` from `CreateAnalysisRequest`, added `sourceId`.

**Review Notes**:
- `AnalysisType` preserved for persistence/display (used in `AnalysisSummary`, `AnalysisDiagnostics`)
- `LaunchableAnalysisType` correctly removed — no longer needed
- `Source` interface matches `SourceSummary` fields from `SourceService`
- `executionMode` added to `IntentDefinition` — required for filtering GENERIC intents
- `CreateAnalysisRequest` now emits `intentId` + optional `sourceId` instead of `type` + `intentId`

### analysis-form.ts

**Change**: Restored objective-based form from Story 0099.

**Review Notes**:
- `Objective` interface exported (used by `ProjectAnalysesSection`)
- Form controls: `objective`, `sourceId`, `targetRevision`, guidance fields
- `ngOnInit` subscribes to `objective.valueChanges` to auto-select sole source for repository scope
- `submit()` emits `intentId` (not `type`), with conditional `sourceId`
- Priority validation preserved

### analysis-form.html

**Change**: Restored objective dropdown, scope badge, repository controls.

**Review Notes**:
- Heading says "Engineering objective" (not "Intent and revision")
- Objective dropdown shows human-readable labels
- Scope badge shows "Entire Project" or "Single Repository"
- Repository scope shows repository dropdown + revision input
- Project scope shows "Entire Project" info message
- Guidance section unchanged

### project-analyses-section.ts

**Change**: Added `SourceService` injection, `objectives$`/`sources$`/`combined$` observables.

**Review Notes**:
- `SourceService` imported from `../projects/source.service`
- `objectives$` filters intents by `executionMode === 'GENERIC'` and maps to `Objective[]`
- `sources$` fetches active Git repositories via `SourceService`
- `combined$` combines objectives and sources for template binding
- `mapIntentsToObjectives` defines the 4 V1 objectives with labels, descriptions, and fixed scopes

### project-analyses-section.html

**Change**: Restored `combined$` observable, objective/source loading states.

**Review Notes**:
- Loading state shows "Loading objectives and repositories…"
- Error states for both objectives and sources
- Passes `[objectives]` and `[sources]` to `<app-analysis-form>`
- Human-owned formatting change preserved (2-space indent, no extra indentation for nested blocks)

## Pre-existing Human Modification

`frontend/src/app/features/analyses/project-analyses-section.html` had a pre-existing formatting change (indentation only). This was preserved in the restoration.

## Cross-Story Impact

- **Story 0099**: All invariants restored
- **Story 0100**: Canonical result page, routing, and models unchanged
- **Story 0101**: Trusted artifact resolution unchanged

## Recommendation

Approve for merge. All changes are minimal, targeted, and well-tested.
