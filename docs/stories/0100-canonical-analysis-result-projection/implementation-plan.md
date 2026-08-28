# Story 1000 — Design Implementation Plan

## Status

**DESIGNED — HUMAN_REVIEW_PENDING**

This is a design artifact only. It does not authorize implementation.

## Purpose

Implement one bounded P0-B slice after human approval: create a canonical human-facing Analysis result projection (`AnalysisResult`) as the primary product surface, composable at query time from existing domain objects, with a dedicated API endpoint and Angular result page.

## Governing Decisions

- ADR-006: proposals remain untrusted until individual human validation
- ADR-017: Analysis and AiTask remain separate; snapshots remain immutable
- ADR-020: provider callback and proposal persistence remain unchanged
- ADR-021: Project remains the knowledge boundary; Analysis may target the complete Project or one Source
- ADR-028: IntentDefinition remains owner of objective execution semantics
- ADR-030: UserGuidance remains optional, bounded, subordinate input
- ADR-063: context retrieval/composition ownership and budgets remain unchanged
- Human Context Supremacy: evidence inspectability preserved

## Contract Decisions

### 1. Canonical Result

The canonical human-facing Analysis result is an `AnalysisResult` **read-model/projection** (not a persisted entity), composed at query time from:
- Analysis (execution record)
- ValidatableProposal[] (AI output awaiting validation)
- Insight[] (validated trusted knowledge)
- GeneratedDeliverable[] (human-authored outputs)
- Supporting Evidence (curated from SelectedEvidence)

### 2. API Contract

New endpoint: `GET /api/v1/analyses/{id}/result` → `AnalysisResultResponse`

Response sections:
- `analysis`: objective, scope, status, duration, sources analyzed, repositoryName (for REPOSITORY_SCOPE)
- `execution`: success/failure, error details
- `proposals`: PROPOSED and ACCEPTED proposals grouped by status/type, with evidence preview; REJECTED excluded
- `insights`: ACTIVE insights with severity, rationale, evidence refs
- `deliverables`: all deliverables with type, audience, status
- `evidence`: curated top-5 per category (facts, observations, repo evidence, etc.)
- `nextActions`: contextual capability flags (review proposals if PROPOSED exist, generate deliverable if insights exist and domain rules permit)

**URL Convention**: Backend exposes stable resource identifiers (`proposalId`, `insightId`, `deliverableId`) and capability flags (`available`). Angular owns client-side route construction using existing conventions.

**Status-Specific Shapes**:
- COMPLETED: full response with all sections
- FAILED: minimal (header + failure + diagnostics navigation)
- IN_PROGRESS: product-level state (header + status, no partial findings)
- Empty COMPLETED: explicit empty states with explanatory messages

### 3. Angular Result Page

New route: `/analyses/{id}/result` (becomes default; `/analyses/{id}` redirects)
Sections: Header (scope shows "Repository: {sourceName}" for REPOSITORY_SCOPE), Proposals, Insights, Deliverables, Evidence, Next Actions, Diagnostics link
Existing `/diagnostics`, `/proposal-review`, `/selected-evidence`, insight/deliverable detail unchanged.

### 4. Existing Contracts Preserved

- All JPA entities (Analysis, AiTask, ValidatableProposal, Insight, GeneratedDeliverable) unchanged
- AnalysisWorkflowService, KnowledgeSelectionService, ProposalReviewService, ProposalPromotionService unchanged
- GET /api/v1/analyses/{id}, /diagnostics, /selected-evidence, /proposal-review unchanged
- Proposal review, insight detail, deliverable, evidence Angular pages unchanged

### 5. No New ADR Required

Projection layer only; no domain boundary changes.

## Expected Change Surface

### Backend

**New files:**
- `backend/src/main/java/com/hopeful117/devlogai/analysis/result/dto/AnalysisResultResponse.java` (and nested records with status-specific shapes)
- `backend/src/main/java/com/hopeful117/devlogai/analysis/result/service/AnalysisResultQueryService.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/result/service/AnalysisResultQueryServiceImpl.java`

**Modified files:**
- `backend/src/main/java/com/hopeful117/devlogai/analysis/controller/AnalysisController.java` (add GET /result endpoint)

**Expected design outcomes:**
- Query-time composition from existing repositories
- Curated evidence projection (fixed top-5 per category for V1)
- Status-specific response shapes (COMPLETED/FAILED/IN_PROGRESS/Empty)
- Proposal filtering: PROPOSED + ACCEPTED only in primary result
- No new persistence, no schema changes

### Frontend

**New files:**
- `frontend/src/app/features/analyses/result/analysis-result-page.ts`
- `frontend/src/app/features/analyses/result/analysis-result-page.html`
- `frontend/src/app/features/analyses/result/analysis-result-page.scss`
- `frontend/src/app/features/analyses/result/analysis-result-page.spec.ts`
- Section components: `proposals-section`, `insights-section`, `deliverables-section`, `evidence-section`, `next-actions-section`

**Modified files:**
- `frontend/src/app/features/analyses/analysis.routes.ts` (add /result route, redirect)
- `frontend/src/app/features/analyses/analysis-detail-page.ts` → rename to `analysis-diagnostics-page.ts` (move to /diagnostics)
- `frontend/src/app/features/analyses/analysis.service.ts` (add getResult method)

**Expected design outcomes:**
- Single-page result with all sections
- Section components reusable
- Header scope display: "Entire Project (N sources)" or "Repository: {sourceName}"
- Proper navigation to existing drill-down pages (Angular constructs routes from resource IDs)
- Next actions use capability flags, not backend URLs

### Tests

**Backend:**
- `AnalysisResultQueryServiceTest.java` (projection logic, curation rules, status-specific shapes, proposal filtering)
- `AnalysisControllerResultTest.java` (endpoint contract)

**Frontend:**
- `analysis-result-page.spec.ts` (rendering, navigation, status-specific rendering)
- Section component specs

## Compatibility Strategy

- **Existing persisted data**: No migration; projection is query-time composition
- **Existing API**: `GET /api/v1/analyses/{id}` unchanged; new endpoint additive
- **Existing Angular**: `/analyses/{id}` redirects to `/result`; `/diagnostics` preserves old detail page
- **Domain entities**: Zero changes to JPA entities
- **Workflows**: Zero changes to Analysis execution, proposal validation, promotion

## Verification Contract

Implementation must demonstrate:

### COMPLETED Analysis
- `GET /api/v1/analyses/{id}/result` returns complete `AnalysisResultResponse` for COMPLETED analysis
- Proposals section shows PROPOSED and ACCEPTED proposals with correct status/type grouping; REJECTED excluded
- Insights section shows all ACTIVE insights from the analysis
- Deliverables section shows all deliverables for the analysis
- Evidence section shows top-5 per category with "View all" link to `/selected-evidence`
- Next actions appear contextually (review proposals if PROPOSED exist, generate deliverable if insights exist and domain rules permit)
- Header shows "Repository: {sourceName}" for REPOSITORY_SCOPE analyses

### FAILED Analysis
- `GET /api/v1/analyses/{id}/result` returns minimal result (header + failure state/message + diagnostics navigation)
- No proposals/insights/deliverables/evidence exposed

### IN_PROGRESS Analysis
- `GET /api/v1/analyses/{id}/result` returns product-level state (header + status, no partial findings)
- No diagnostics polling; if UX needs polling, poll `/result` endpoint for status transitions

### Empty COMPLETED Analysis
- Explicit empty states with explanatory messages for each section ("No proposals generated", "No validated insights", etc.)
- Sections not silently hidden

### Angular
- `/analyses/{id}/result` renders all sections with proper navigation
- `/analyses/{id}` redirects to `/result`
- `/analyses/{id}/diagnostics` renders existing diagnostics page
- Angular constructs all routes from resource IDs (no backend URLs in response)

### Regression
- All existing endpoints return identical responses
- All existing tests pass (984 backend, 219 frontend)

## Explicit Exclusions

- P0-C validation/result navigation (separate Story)
- CATEGORY_SELECTION / Story 0098
- Benchmark harness or execution
- Engineering Query
- New IntentDefinitions or Analysis types
- Prompt/AI Engine/output-contract changes
- Retrieval, ranking, budgets, floors, ceilings
- RAG, vectors, embeddings, ContextPack improvements
- Analysis intelligence redesign

## Human Approval Gate

Implementation must not begin until human approves:

1. Story number and priority (P0-B)
2. Canonical result = `AnalysisResult` projection (read-model, not entity)
3. API contract: `GET /api/v1/analyses/{id}/result` with 7 sections and status-specific shapes
4. Angular route: `/analyses/{id}/result` as default; `/diagnostics` for old page
5. Evidence curation: fixed top-5 per category, "View all" to selected-evidence
6. Proposal filtering: PROPOSED + ACCEPTED in primary result; REJECTED via proposal-review
7. Failed/in-progress/empty result states as specified
8. No domain entity changes, no workflow changes, no ADR changes
9. URL convention: backend exposes IDs/capabilities; Angular constructs routes

## Resolved Questions (Previously Unresolved)

- **Q1: Proposal granularity** → Option B: PROPOSED + ACCEPTED in primary result; REJECTED via proposal-review/history
- **Q2: Evidence curation** → Fixed top-5 per category for V1
- **Q3: Failed analysis** → Minimal result (header + failure + diagnostics link)
- **Q4: In-progress analysis** → Product-level state, no diagnostics polling; poll `/result` if needed
- **Q5: Empty state** → Explicit empty messages, no silent hiding
- **Q6: Cross-analysis comparison** → Out of scope for P0-B
- **Q7: Deliverable generation** → Expose when domain rules permit; no rule changes
- **Q8: Repository-scope header** → "Repository: {sourceName}"

ANALYSIS_RESULT_PROJECTION_STORY_DESIGNED_AWAITING_HUMAN_REVIEW