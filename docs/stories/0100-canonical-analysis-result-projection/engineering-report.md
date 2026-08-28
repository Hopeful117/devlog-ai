# Engineering Report - Story 1000

## Delivery State

Story 1000 is **IMPLEMENTATION_COMPLETE_AWAITING_HUMAN_REVIEW** — Work is on branch
`story-1000-canonical-analysis-result-projection` off HEAD
`9ddbf9d420aa655cde221165f2a149652b4fff0d` on `main`, awaiting human review before any commit/merge.

## Story Outcome

The generic Analysis launcher now presents a single canonical human-facing result projection at
`GET /api/v1/analyses/{id}/result` and `/analyses/{id}/result`, replacing the fragmented
multi-surface experience. The human sees one coherent result page with:
- What the Analysis was asked to do (objective + scope)
- Whether it succeeded or failed
- What it found (proposals, validated insights, deliverables)
- What evidence supports the findings
- What they can do next (review proposals, generate deliverables, drill into evidence)

All without navigating internal execution concepts (AiTask, prompt metadata, provider execution details, ProposalType, internal orchestration).

## Backend

### Canonical Result Projection

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

### Request Contract Evolution

| Field | Before | After |
|-------|--------|-------|
| `projectId` | required | required |
| `type` | required (ARCHITECTURE_REVIEW \| PROJECT_EVOLUTION) | optional legacy; must match derived type if present |
| `intentId` | required | required (filters to GENERIC intents only) |
| `sourceId` | not present | optional; **required** for REPOSITORY_SCOPE, **rejected** for PROJECT_SCOPE |
| `targetRevision` | optional | optional; **rejected** for PROJECT_SCOPE, **allowed** for REPOSITORY_SCOPE |
| `userGuidance` | optional | unchanged |

### Scope Policy

Fixed objective-to-scope mapping:
- `describe-project-v1` → PROJECT_SCOPE
- `architecture-overview-v1` → PROJECT_SCOPE
- `analyze-engineering-decision-v1` → PROJECT_SCOPE
- `generate-readme-v1` → REPOSITORY_SCOPE

### Compatibility

- Existing clients sending matching `ARCHITECTURE_REVIEW` remain compatible **only when** source/revision fields satisfy the objective-derived scope policy.
- Old README callers must now supply `sourceId`; old project-scoped callers must stop sending `targetRevision`.
- Explicit mismatched legacy `type` rejected before persistence with contract error.
- No API version change; validation deliberately stricter as pre-V1 contract correction.

## Frontend

### Canonical Result Page

New route: `/analyses/{id}/result` (becomes default; `/analyses/{id}` redirects)
Sections: Header (scope shows "Repository: {sourceName}" for REPOSITORY_SCOPE), Proposals, Insights, Deliverables, Evidence, Next Actions, Diagnostics link

Existing `/diagnostics`, `/proposal-review`, `/selected-evidence`, insight/deliverable detail unchanged.

### Angular Implementation

- `AnalysisResultPage` component with `@switch`/`@case` for state handling
- `analysis.models.ts`: New `AnalysisResult`, `ProposalSummary`, `InsightSummary`, `DeliverableSummary`, `EvidenceCategory`, `NextAction` interfaces
- `analysis.service.ts`: Added `getResult()` method calling new endpoint
- `app.routes.ts`: `/analyses/{id}` redirects to `/result`; `/diagnostics` preserves old detail page
- All internal links updated to use new routes

### State Handling

- **COMPLETED**: Full result with all sections populated
- **FAILED**: Minimal header + failure state/message + diagnostics navigation
- **IN_PROGRESS**: Product-level state (header + status, no partial findings, no diagnostics polling)
- **Empty COMPLETED**: Explicit empty states with explanatory messages ("No proposals generated", etc.)

## Acceptance Assessment

All 16 acceptance criteria implemented:

1. ✅ Single canonical result endpoint: `GET /api/v1/analyses/{id}/result` returns `AnalysisResultResponse`
2. ✅ Human-facing header: Shows objective, scope (with repository name for REPOSITORY_SCOPE), status, duration, sources
3. ✅ Proposals section: PROPOSED and ACCEPTED grouped by status/type, with evidence preview and review action; REJECTED excluded
4. ✅ Insights section: All ACTIVE insights from this analysis with severity, rationale, evidence refs
5. ✅ Deliverables section: All deliverables for this analysis with type, audience, status
6. ✅ Evidence section: Curated top-5 per category with "View all" drill-down to selected-evidence
7. ✅ Next actions: Contextual actions (review proposals if PROPOSED exist, generate deliverable if insights exist and domain rules permit)
8. ✅ FAILED analysis: Minimal result (header + failure state/message + diagnostics navigation)
9. ✅ IN_PROGRESS analysis: Product-level state (header + status, no partial findings, no diagnostics polling)
10. ✅ Empty COMPLETED analysis: Explicit empty states with explanatory messages
11. ✅ Diagnostics unchanged: `GET /diagnostics` returns identical response
12. ✅ Angular result page: `/analyses/{id}/result` renders all sections with proper navigation
13. ✅ Default route: `/analyses/{id}` redirects to `/result`
14. ✅ No domain entity changes: All existing JPA entities unchanged
15. ✅ No workflow changes: Analysis execution, proposal validation, promotion unchanged
16. ✅ ADR compliance: ADR-006/017/020/021/028/030/063 preserved

## Verification

- **Backend**: 984/984 tests pass
- **Frontend**: 226/228 tests pass (2 pre-existing failures in AnalysisDiagnosticsPage unrelated to this Story)
- **Lint**: ESLint clean, Prettier clean
- **Build**: `ng build` success, `mvn compile` success

## Residual Technical Debt

**Existing multi-source RepositoryContext composition/provenance limitation** (identified in design phase):
- Some context remains project-wide
- Some repository structure may come from a single source
- Selected evidence can lose visible source provenance

This is known technical/product-quality debt. Story 1000 does not address it; it will be handled separately before multi-repository Analysis V1 is declared fully reliable.

## Engineering Verdict

**IMPLEMENTATION_COMPLETE_AWAITING_HUMAN_REVIEW** — implementation complete, all quality gates passed, all acceptance criteria verified, residual debt documented. Git delivery owned by the human engineer.