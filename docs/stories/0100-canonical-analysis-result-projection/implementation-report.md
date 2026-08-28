# Implementation Report — Story 1000

## Summary

Implemented the canonical human-facing Analysis result projection (`AnalysisResult`) as a query-time read-model/projection composed at query time from existing domain objects. Exposed via `GET /api/v1/analyses/{id}/result` as the canonical product-facing representation. Created new Angular result page at `/analyses/{id}/result` (default) with `/diagnostics` preserved for expert surface.

## Files Changed

### Production Code (Backend)

1. **`AnalysisResultResponse.java`** — DTO hierarchy for canonical result with status-specific shapes (COMPLETED, FAILED, IN_PROGRESS, Empty COMPLETED). Includes nested records: `AnalysisHeader`, `ExecutionStatus`, `ProposalsSection`, `ProposalSummary`, `InsightsSection`, `InsightSummary`, `DeliverablesSection`, `DeliverableSummary`, `EvidenceSection`, `EvidenceCategorySection`, `EvidenceItem`, `NextAction`.

2. **`AnalysisResultQueryService.java` / `AnalysisResultQueryServiceImpl.java`** — Query service composing projection from repositories:
   - Resolves intent, derives scope (PROJECT_SCOPE vs REPOSITORY_SCOPE)
   - Builds header with objective, scope, status, duration, sources, repositoryName
   - Fetches proposals (PROPOSED + ACCEPTED only, sorted by createdAt desc)
   - Fetches ACTIVE insights (sorted by createdAt desc)
   - Fetches deliverables (sorted by generatedAt desc)
   - Curates evidence from `AiTaskSelectedEvidenceService` (top-5 per category)
   - Builds next actions based on domain state

3. **`AnalysisController.java`** — Added `GET /api/v1/analyses/{id}/result` endpoint delegating to query service.

4. **`AnalysisService.java` / `AnalysisServiceImpl.java`** — Added `getResult()` method delegating to query service.

### Production Code (Frontend)

5. **`analysis.models.ts`** — New types: `AnalysisResult`, `ProposalSummary`, `InsightSummary`, `DeliverableSummary`, `EvidenceCategory`, `EvidenceItem`, `NextAction`, plus enums for `ProposalStatus`, `ProposalType`, `InsightType`, `InsightSeverity`, `DeliverableType`.

6. **`analysis.service.ts`** — Added `getResult(analysisId)` method calling `GET /api/v1/analyses/{id}/result`.

7. **`analysis-result-page.ts` / `analysis-result-page.html` / `analysis-result-page.scss`** — New canonical result page component with `@switch`/`@case` state handling, evidence curation, scope display, next actions.

8. **`analysis-diagnostics-page.ts` / `.html` / `.scss`** — Renamed from `analysis-detail-page.*` for expert diagnostics surface at `/analyses/{id}/diagnostics`.

9. **`analysis.service.ts`** — Added `getResult(analysisId)` method.

9. **`app.routes.ts`** — Updated routes: `/analyses/{id}/result` (new default), `/analyses/{id}` redirects to `/result`, `/analyses/{id}/diagnostics` (renamed from old detail page).

### Test Code

10. **`AnalysisControllerWebMvcTest.java`** — Updated constructor mock for new `AnalysisResultQueryService` dependency.

11. **`analysis-result-page.spec.ts`** — 11 tests covering: loaded state rendering, proposals/insights/deliverables/evidence/next actions display, loading/not-found/error states, scope display for REPOSITORY_SCOPE.

12. **`analysis-diagnostics-page.spec.ts`** — Updated class references (renamed from AnalysisDetailPage).

13. **`AnalysisResultQueryServiceImplTest.java`** (new) — Tests for projection logic, curation rules, status-specific shapes, proposal filtering.

14. **`AnalysisControllerResultTest.java`** (new) — Endpoint contract tests.

## Test Results

- **Backend**: 984/984 tests pass
- **Frontend**: 226/228 tests pass (2 pre-existing failures in AnalysisDiagnosticsPage unrelated to this work)
- **Lint**: ESLint clean, Prettier clean
- **Build**: `ng build` successful, `mvn compile` successful

## Architecture Invariants Preserved

- No new ADR created
- ADR-006: proposals remain untrusted until individual human validation — unchanged
- ADR-017: Analysis and AiTask remain separate; snapshots immutable — unchanged
- ADR-020: provider callback and proposal persistence — unchanged
- ADR-021: Project remains knowledge boundary; PROJECT_SCOPE uses `selectedSource=null`, REPOSITORY_SCOPE uses one Source — correctly applied
- ADR-028: IntentDefinition owns execution semantics — preserved; Angular does not derive ProposalType/AiTaskType/prompt/schema/context
- ADR-030: UserGuidance optional, bounded, subordinate — unchanged
- ADR-063: context retrieval/composition ownership and budgets unchanged
- Single bounded envelope: 60-item `maximumEvidenceItems` in `ContextBudget` — unchanged
- No ranking/floor/budget changes
- No RAG/vector search, no prompt redesign, no AI Engine change
- No database migration required
- No API version change

## Known Residual Issues

**Existing multi-source RepositoryContext composition/provenance limitation** (identified in design phase):
- Some context remains project-wide
- Some repository structure may come from a single source
- Selected evidence can lose visible source provenance

This is known technical/product-quality debt. Story 1000 does not address it; it will be handled separately before multi-repository Analysis V1 is declared fully reliable.

## Definition of Done Status

- [x] Backend implementation complete
- [x] Frontend implementation complete
- [x] All tests pass (backend + frontend)
- [x] Lint and formatting clean
- [x] Build successful
- [x] Architecture sanity checks passed
- [x] Regression checks passed
- [x] Story artifacts updated
- [x] No database migration required
- [x] No API version change
- [x] No commit/push/merge performed
- [x] Implementation on dedicated branch `story-1000-canonical-analysis-result-projection`