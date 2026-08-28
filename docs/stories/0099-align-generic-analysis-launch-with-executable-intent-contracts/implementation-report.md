# Implementation Report — Story 0099

## Summary

Aligned the generic Analysis launch contract with executable intent contracts by replacing raw internal launch choices (AnalysisType, Intent ID/version, prompt template) with four human-facing engineering objectives that derive fixed scope and execution metadata. Introduced objective-specific fixed scope policy: three objectives use PROJECT_SCOPE (entire project), one objective (README) uses REPOSITORY_SCOPE (single repository). Removed editable AnalysisType from launch UI; Core derives ARCHITECTURE_REVIEW for all four generic V1 objectives. Added conditional sourceId and targetRevision validation based on derived scope.

## Files Changed

### Production Code (10 files)

#### Backend (2 files)

1. **`CreateAnalysisRequest.java`** — Made `type` optional (legacy compatibility); added optional `sourceId` field; kept `targetRevision` optional.

2. **`AnalysisServiceImpl.java`** — Centralized V1 generic launch policy in `create()`:
   - Resolves intent, enforces `GENERIC` execution mode.
   - Maps four intent IDs to fixed scopes:
     - PROJECT_SCOPE: `describe-project-v1`, `architecture-overview-v1`, `analyze-engineering-decision-v1`
     - REPOSITORY_SCOPE: `generate-readme-v1`
   - Validates scope-specific fields:
     - PROJECT_SCOPE: rejects `sourceId` and `targetRevision` (400)
     - REPOSITORY_SCOPE: requires `sourceId`, validates ownership/active/Git type
   - Legacy `type` must match derived `ARCHITECTURE_REVIEW` if provided.
   - Repository scope: synchronizes source to get resolved revision; persists selected Source + immutable snapshot (id + name); stores requested revision.
   - Project scope: keeps `selectedSource=null`, `selectedSourceSnapshot=null`, `targetRevision=null`; collection resolves each active source independently.

#### Frontend (8 files)

3. **`analysis.models.ts`** — Updated `CreateAnalysisRequest` (removed `type`, added optional `sourceId`); added `executionMode` to `IntentDefinition`; added `Source` interface.

4. **`analysis-form.ts` / `analysis-form.html`** — Replaced raw AnalysisType + Intent selector with four objective cards. Scope-derived UI:
   - Project-scoped objectives: read-only "Entire Project" badge, hidden repository/revision controls.
   - Repository-scoped objective: repository dropdown (auto-selects sole active Git source, requires choice when multiple) + optional revision field.

5. **`project-analyses-section.ts` / `project-analyses-section.html`** — Fetches active Git sources via `SourceService`; maps generic intents to objectives with fixed scope; passes objectives and sources to form component.

6. **Test files updated:**
   - `analysis-form.spec.ts` — Updated to new objective-based API with scope logic.
   - `analysis.service.spec.ts` — Removed legacy `type` from request payload.
   - `project-analyses-section.spec.ts` — Added mock `SourceService`, updated intent/launch payload.

### Test Code (3 files modified, 0 new)

All existing tests updated to match new contracts; no new test files created.

## Test Results

- **Backend**: 984/984 tests pass (unit + integration)
- **Frontend**: 219/219 tests pass (component + service)
- **Lint**: Frontend ESLint clean, Prettier clean
- **Build**: Frontend `ng build` successful, Backend `mvn compile` successful

## Architecture Invariants Preserved

- No new ADR created
- ADR-006: proposals remain untrusted until individual human validation — unchanged
- ADR-017: Analysis and AiTask remain separate; snapshots immutable — unchanged
- ADR-020: provider callback and proposal persistence — unchanged
- ADR-021: Project remains knowledge boundary; Analysis may target complete Project or one Source — now correctly applied per objective
- ADR-028: IntentDefinition owns execution semantics — preserved; Angular does not derive ProposalType/AiTaskType/prompt/schema/context
- ADR-030: UserGuidance optional, bounded, subordinate — unchanged
- ADR-063: context retrieval/composition ownership and budgets — unchanged
- Single bounded envelope: 60-item `maximumEvidenceItems` in `ContextBudget` — unchanged
- No ranking/floor/budget changes
- No RAG/vector search, no prompt redesign, no AI Engine change
- No database migration required
- No API version change (validation stricter but additive)

## Known Residual Issues

**Existing multi-source RepositoryContext composition/provenance limitation** (identified in design phase):
- Some context remains project-wide
- Some repository structure may come from a single source
- Selected evidence can lose visible source provenance

This is known technical/product-quality debt. Story 0099 does not address it; it will be handled separately before multi-repository Analysis V1 is declared fully reliable.