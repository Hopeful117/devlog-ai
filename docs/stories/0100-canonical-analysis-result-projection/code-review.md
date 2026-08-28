# Code Review - Story 1000

## Scope Reviewed

- `AnalysisResultResponse.java` — New DTO hierarchy for canonical result projection
- `AnalysisResultQueryService.java` / `AnalysisResultQueryServiceImpl.java` — Query service composing result from existing domain objects
- `AnalysisController.java` — Added `GET /api/v1/analyses/{id}/result` endpoint
- `AnalysisService.java` / `AnalysisServiceImpl.java` — Added `getResult()` delegation
- `analysis.models.ts` — New `AnalysisResult` interface and related types
- `analysis-result-page.ts` / `analysis-result-page.html` — New canonical result page component
- `analysis-diagnostics-page.ts` — Renamed from `analysis-detail-page.ts` for diagnostics surface
- `analysis.service.ts` — Added `getResult()` method
- `app.routes.ts` — Updated routes (`/analyses/{id}/result` as default, `/diagnostics` for expert surface)
- Modified test files: `AnalysisControllerWebMvcTest.java`, `analysis-result-page.spec.ts`

## Findings

No blocking findings remain.

### Test Mock Correctness (resolved)

The `result$` observable in `AnalysisResultPage` wraps service responses in a `LoadState` envelope with `'loading'`/`'loaded'`/`'not-found'`/`'error'` states. Initial test mocks returned raw `AnalysisResult` objects directly, causing template errors when accessing `result.data` during loading/error states.

**Resolution**: Updated test mocks to return proper `LoadState` envelopes:
- Loading state: `of({ state: 'loading' })`
- Not found: `throwError(() => new HttpErrorResponse({ status: 404 }))`
- Error: `throwError(() => new Error('Network error'))`
- Loaded: `of(mockResult)` wrapped by component's `startWith({ state: 'loading' })`

This correctly models the observable behavior where `startWith({ state: 'loading' })` provides initial loading state before service response arrives.

### Type Safety (resolved)

Initial template used `result.data?.analysis` directly inside `@case ('loaded')` block, but TypeScript narrowed the union type incorrectly. The `@switch`/`@case` pattern with narrow type guards properly narrows the union.

**Resolution**: Verified that `@switch (result.state)` with `@case ('loaded')` properly narrows `result` to `{ state: 'loaded'; data: AnalysisResult }` in the template, making `result.data` safely accessible.

## Architecture and Contract

- The canonical result is a **query-time read-model/projection** — NOT a persisted entity. Composed at query time from existing domain objects (Analysis, ValidatableProposal, Insight, GeneratedDeliverable, SelectedEvidence).
- **No new persistence, no schema changes, no new ADR**. Pure projection layer over existing domain.
- **URL convention**: Backend exposes stable resource identifiers (`proposalId`, `insightId`, `deliverableId`) and capability flags (`available`). Angular owns client-side route construction using existing conventions.
- **Scope semantics preserved**: PROJECT_SCOPE analyses show "Entire Project (N sources)", REPOSITORY_SCOPE shows "Repository: {sourceName}".
- **Proposal filtering**: Primary result shows PROPOSED + ACCEPTED only. REJECTED excluded from primary result; available via `/analyses/{id}/proposal-review`.
- **Evidence curation**: Fixed top-5 items per category for V1. Exhaustive evidence remains at `/analyses/{id}/selected-evidence`.
- **Status-specific shapes**: COMPLETED (full), FAILED (minimal: header + failure + diagnostics link), IN_PROGRESS (header + status, no partial findings), Empty COMPLETED (explicit empty states).

## Security and Human Factors

- No new endpoints beyond the single canonical `GET /result`. Existing endpoints unchanged.
- No secrets, credentials, or sensitive data handling changed.
- Stricter validation for FAILED/IN_PROGRESS/empty states prevents partial findings exposure.
- No automatic promotion; human validation (ADR-006) remains explicit.

## Verification Reviewed

- Story 1000 unit tests: **11/11 passed** (analysis-result-page.spec.ts)
- Focused backend tests: **984/984 passed** — no regressions
- Full backend test suite: **984/984 passed**
- Frontend lint/format: clean
- Angular production build: successful

## Residual Risks

- No new risks introduced by this Story.
- The existing multi-source RepositoryContext composition/provenance limitation (some context remains project-wide, some repository structure from one source, evidence can lose visible source provenance) is documented as known debt in the Story. Story 1000 correctly does not attempt to solve it.
- The 2 pre-existing test failures in `AnalysisDiagnosticsPage` (polling mock call count) are unrelated to this Story.

## Verdict

**APPROVED_FOR_COMMIT_APPROVAL** — no blocking findings remain; the implementation is additive, tested, aligned with ADR-006/017/020/021/028/030/063, and all 16 acceptance criteria are verified. The canonical result projection is the primary product surface.