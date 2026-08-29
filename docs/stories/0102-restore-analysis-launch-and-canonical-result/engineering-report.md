# Story 0102 — Engineering Report

## Status

**ENGINEERING_REPORT_AWAITING_HUMAN_REVIEW**

## Context

Two P0 regressions prevented the Analysis product from functioning:

1. **Regression B**: `AnalysisResultQueryServiceImpl.curateCategory()` used an unsafe generic cast `(List<EvidenceItem>) items` where the actual items were typed evidence items (`FactItem`, `ObservationItem`, etc.). This caused `ClassCastException` when the result was consumed and `JsonMappingException` when serialized.

2. **Regression A**: Story 0100 accidentally reverted the Story 0099 Angular launch contract, reintroducing an AnalysisType selector that Story 0099 intentionally removed.

## Root Cause Analysis

### Regression B

The terminal `curateCategory(Availability, int, List<?>)` method relied on Java's erased generics to cast `List<?>` to `List<EvidenceItem>`. The cast succeeded at the Java level but the elements remained typed items (`FactItem`, etc.) that don't have `EvidenceItem` accessor methods (`layer()`, `kind()`, etc.).

**Why it wasn't caught earlier**: The existing `AnalysisResultQueryServiceImplTest` never returned `State.AVAILABLE` from the evidence mock, so `buildEvidence()` returned `emptyEvidence()` and `curateCategory()` was never reached.

### Regression A

Story 0100 introduced canonical result functionality that included frontend changes to the analysis form. These changes inadvertently reverted the Story 0099 objective-based form to the pre-Story-0099 type/intent form.

**Why it wasn't caught earlier**: Story 0100 tests verified canonical result functionality but didn't verify that the launch form contract was preserved.

## Solution

### Regression B

Replaced the unsafe generic cast with eight dedicated type-safe mapping methods. Each method:
1. Takes the typed section (e.g., `FactsSection`)
2. Streams over the items
3. Maps each item to `EvidenceItem` using the approved semantic mapping
4. Returns `EvidenceCategorySection` with correctly mapped items

**Trade-offs**:
- More code (8 methods vs 1), but each method is self-contained and independently testable
- Explicit field-by-field mapping eliminates the class of defects caused by type erasure
- The `EVIDENCE_PREVIEW_LIMIT = 5` is preserved in each method

### Regression A

Selectively restored Story 0099 frontend:
- Restored objective-based form (not a blind git revert)
- Preserved Story 0100/0101 canonical result and trusted-artifact functionality
- Added `SourceService` integration for repository scope objectives

## Testing Strategy

### Test-First Execution

1. **RED**: Added tests against current broken implementation
2. **Observed**: Recorded actual failure behavior (`ClassCastException`, `JsonMappingException`)
3. **GREEN**: Implemented production fix
4. **Verified**: All tests pass

### Coverage

- 8 category mapping tests (one per evidence type)
- 1 JSON serialization regression test (exercises full composition path)
- 1 evidence preview limit test
- 3 backend launch contract protection tests
- 4 frontend contract tests

### Regression Prevention

- Backend tests now exercise `curateCategory()` through `getResult()` — the composition path was previously untested
- Frontend tests now verify the Story 0099 contract — preventing future accidental reverts

## Risks

- **Low**: The eight mapping methods share structural similarity. A future refactor could introduce a generic dispatch mechanism, but the current explicit approach is clearer.
- **Low**: The `SourceService` integration in `ProjectAnalysesSection` adds a new dependency. The mock in tests is minimal.

## Recommendations

1. Consider adding an integration test that exercises the full `POST /api/v1/analyses` → `GET /api/v1/analyses/{id}/result` flow end-to-end
2. Consider adding a contract test that verifies the frontend `CreateAnalysisRequest` matches the backend `CreateAnalysisRequest` schema
