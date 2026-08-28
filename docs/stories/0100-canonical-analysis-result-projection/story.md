# Story 0100 — Canonical Human-Facing Analysis Results Projection

## Status

**DESIGNED**

HUMAN_REVIEW = **PENDING**

## Priority

**P0-B — CANONICAL ANALYSIS RESULT PROJECTION**

Follows Story 0099 (P0-A Launch Contract). This Story supersedes all other Analysis result work.

## Objective

Design one canonical human-facing Analysis result projection that becomes the primary product surface after an Analysis completes. The human should not need to understand or reconstruct internal execution concepts (AiTask, prompt metadata, provider execution details, ProposalType, internal orchestration) to understand the result.

## Human Story

As a human engineer who launched an Analysis,
I want to see a single, coherent result page that tells me:
- what the Analysis was asked to do (objective + scope)
- whether it succeeded or failed
- what it found (proposals, validated insights, deliverables)
- what evidence supports the findings
- what I can do next (review proposals, generate deliverables, drill into evidence)

So that I can act on the Analysis output without navigating internal execution concepts.

---

## Governed By

- ADR-006: proposals remain untrusted until individual human validation
- ADR-017: Analysis and AiTask remain separate; snapshots remain immutable
- ADR-020: provider callback and proposal persistence unchanged
- ADR-021: Project is the knowledge boundary; Analysis targets Project or Source
- ADR-028: IntentDefinition owns objective execution semantics
- ADR-030: UserGuidance optional, bounded, subordinate
- ADR-063: context retrieval/composition ownership and budgets unchanged
- Human Context Supremacy (ADR-063 amendment): evidence inspectability preserved

## Current State Investigation

Full investigation at: `docs/investigations/analysis-result-projection-gap.md`

**Key Finding**: No canonical human-facing Analysis result exists. Results are fragmented across:
- Analysis (execution metadata)
- AiTask (prompt/execution metadata, selected knowledge snapshot)
- ValidatableProposal (AI-generated proposals awaiting review)
- Insight (validated trusted knowledge)
- GeneratedDeliverable (human-authored documents)
- Diagnostics/SelectedEvidence (evidence provenance)
- Project Profile (deterministic summary)

Human must mentally compose result across 7+ API calls and 4+ Angular surfaces.

---

## Design Decisions

### 1. Canonical Human-Facing Result: `AnalysisResult`

**The canonical result of an Analysis is an `AnalysisResult` projection** — a read-only, human-oriented composite that answers "what did this Analysis produce?"

It is **not** a new persisted entity. It is a **projection/read model** composed at query time from:
- Analysis (execution record)
- ValidatableProposal[] (AI output awaiting validation)
- Insight[] (validated trusted knowledge)
- GeneratedDeliverable[] (human-authored outputs)
- Supporting Evidence (facts, observations, repository evidence, prior insights)
- Execution Summary (success/failure, duration, sources analyzed)

### 2. Ownership: New Read Model / Projection Layer

**No existing domain object owns the canonical result.** The design introduces:
- A new **read-model** `AnalysisResult` (DTO, not JPA entity)
- A **query service** `AnalysisResultQueryService` that composes the projection
- A **dedicated API endpoint** `GET /api/v1/analyses/{id}/result`

**Rationale**:
- Analysis is an orchestration aggregate, not a result container
- AiTask is an execution record, not a result
- ValidatableProposal/Insight/Deliverable are independent domain objects with their own lifecycles
- Composition at query time avoids dual-write problems and preserves domain boundaries

### 3. New Projection Required: YES

**A new read-model `AnalysisResult` is required.** It cannot be satisfied by extending existing DTOs because:
- `AnalysisResponse` is execution metadata only
- `AnalysisDiagnosticsResponse` is internal diagnostics
- `AiTaskSelectedEvidenceResponse` is evidence-only (500+ lines, no proposals/insights)
- `ProposalReviewResponse` is paginated review UI, not result summary
- `ProjectProfile` is deterministic-only

The projection must merge:
- Execution summary (from Analysis + Diagnostics)
- Proposals summary (from ValidatableProposal)
- Insights summary (from Insight)
- Deliverables summary (from GeneratedDeliverable)
- Supporting evidence (curated from SelectedEvidence)
- Provenance links (to proposal review, insight detail, deliverable detail, evidence drill-down)

### 4. Primary Result vs. Diagnostics Split

| **Primary Result (Human-Facing)** | **Diagnostics (Internal/Expert)** |
|---|---|
| Analysis objective + scope | Collection metrics (fact/observation counts) |
| Execution outcome (success/failed/duration) | Collector success/failure details |
| Proposals (grouped by type, validation status) | Pipeline stages, resource counts |
| Validated insights (with severity, rationale) | Context builder version, collector versions |
| Deliverables (with generation status) | Serialized context size, context digest |
| Supporting evidence (curated, human-readable) | Selection metadata, knowledge budget |
| Evidence provenance links (drill-down) | AiTask correlationId, prompt digests, selection digest |
| Next actions (review proposals, generate deliverables) | Raw analysis context JSON |

**Diagnostics remain at `GET /api/v1/analyses/{id}/diagnostics`** — unchanged.

### 5. Deliverables and ValidatableProposals in the Result

| Artifact | Placement in Result |
|---|---|
| **ValidatableProposal (PROPOSED)** | "Proposals Awaiting Review" section — grouped by type, with evidence preview, direct link to proposal review |
| **ValidatableProposal (ACCEPTED)** | "Reviewed Proposals" — shows decision, resulting Insight/Event/Decision link |
| **ValidatableProposal (REJECTED)** | Available through proposal-review/history surface; not shown in primary result |
| **Insight (ACTIVE)** | "Validated Insights" — title, severity, type, rationale, evidence references, link to insight detail |
| **GeneratedDeliverable** | "Deliverables" — title, type, audience, generation status, link to deliverable detail |

**Key principle**: The result shows **what exists**, not just what's pending. A completed Analysis with 3 accepted insights and 2 deliverables should display them prominently, not hide them behind "review proposals" navigation.

### 6. Supporting Engineering Evidence and Provenance

**Evidence in the primary result is CURATED, not exhaustive**:
- Show top-5 items per category (facts, observations, repository evidence, prior insights)
- Each item shows: type, human-readable content, source, evidence references
- "View all" links drill down to `SelectedEvidence` (existing `GET /selected-evidence`)
- Provenance chain: Proposal → supporting Fact/Observation IDs → EvidenceReference → Source

**Evidence categories exposed** (from existing `Categories` DTO):
- Facts (deterministic)
- Observations (rule-derived)
- Prior Insights (trusted knowledge)
- Architecture Knowledge (ADR/decision insights)
- Engineering Events (commit-scoped)
- Human Context (manual inputs)
- Evolution Context (commit diffs)
- Repository Evidence (raw collector output)

**Provenance preserved**: Every proposal/insight/deliverable links to its supporting evidence IDs. The result shows evidence references; drill-down goes to `SelectedEvidence`.

### 7. Link to Proposal Validation and Trusted Knowledge

The result **does not bypass** ADR-006 validation:
- Proposals are shown with current status (PROPOSED/ACCEPTED)
- PROPOSED proposals have "Review" action → navigates to existing `ProposalReviewPage`
- ACCEPTED proposals show resulting Insight/Event/Decision with link
- REJECTED proposals are not shown in primary result; available through proposal-review/history
- Insights are shown as trusted knowledge (ACTIVE/SUPERSEDED/ARCHIVED)
- Deliverables are shown as post-validation artifacts

**No automatic promotion**. The result reflects current state; human validation remains explicit.

### 8. Backend API Contract

#### New Endpoint

```
GET /api/v1/analyses/{id}/result
```

#### Response: `AnalysisResultResponse`

```json
{
  "analysis": {
    "id": "uuid",
    "projectId": "uuid",
    "objective": "Understand this project",
    "scope": "PROJECT_SCOPE",
    "intentId": "describe-project-v1",
    "intentVersion": "v1",
    "status": "COMPLETED",
    "startedAt": "2026-08-28T10:00:00Z",
    "completedAt": "2026-08-28T10:05:00Z",
    "durationSeconds": 300,
    "sourcesAnalyzed": ["repo-a", "repo-b"],
    "targetRevision": null,
    "repositoryName": null
  },
  "execution": {
    "success": true,
    "failureCode": null,
    "failureMessage": null
  },
  "proposals": {
    "total": 5,
    "byStatus": { "PROPOSED": 2, "ACCEPTED": 3, "REJECTED": 0 },
    "byType": { "INSIGHT": 4, "ENGINEERING_DECISION": 1 },
    "items": [
      {
        "id": "uuid",
        "type": "INSIGHT",
        "status": "PROPOSED",
        "confidence": 0.92,
        "title": "High coupling between modules",
        "summary": "Modules A and B share 15+ internal interfaces...",
        "evidencePreview": ["Fact#123", "Observation#45"],
        "proposalId": "uuid"
      }
    ]
  },
  "insights": {
    "total": 3,
    "items": [
      {
        "id": "uuid",
        "type": "ARCHITECTURE",
        "severity": "WARNING",
        "title": "Circular dependency detected",
        "content": "Module A imports B, B imports A...",
        "rationale": "Violates layered architecture...",
        "confidence": 0.95,
        "evidenceReferences": ["evidence-ref-1", "evidence-ref-2"],
        "insightId": "uuid"
      }
    ]
  },
  "deliverables": {
    "total": 2,
    "items": [
      {
        "id": "uuid",
        "type": "ARCHITECTURE_REVIEW",
        "title": "Architecture Review - Q3 2026",
        "audience": "engineering-leadership",
        "status": "GENERATED",
        "generatedAt": "2026-08-28T11:00:00Z",
        "sourceInsights": ["insight-id-1", "insight-id-2"],
        "deliverableId": "uuid"
      }
    ]
  },
  "evidence": {
    "facts": { "count": 142, "items": [...] },
    "observations": { "count": 38, "items": [...] },
    "priorInsights": { "count": 12, "items": [...] },
    "architectureKnowledge": { "count": 8, "items": [...] },
    "engineeringEvents": { "count": 5, "items": [...] },
    "humanContext": { "count": 3, "items": [...] },
    "evolutionContext": { "count": 2, "items": [...] },
    "repositoryEvidence": { "count": 67, "items": [...] }
  },
  "nextActions": [
    { "action": "REVIEW_PROPOSALS", "label": "Review 2 pending proposals", "available": true },
    { "action": "GENERATE_DELIVERABLE", "label": "Generate architecture review", "available": true }
  ]
}
```

**Key API Design Notes**:
- **No frontend URLs in response**. The backend exposes stable resource identifiers (`proposalId`, `insightId`, `deliverableId`) and capability flags (`available`). Angular owns client-side route construction using existing conventions:
  - Proposal review: `/analyses/{analysisId}/proposal-review?proposal={proposalId}`
  - Insight detail: `/insights/{insightId}`
  - Deliverable detail: `/deliverables/{deliverableId}`
  - Evidence drill-down: `/analyses/{analysisId}/selected-evidence`
  - Proposal review page: `/analyses/{analysisId}/proposal-review`
  - Deliverable generation: `/analyses/{analysisId}/deliverables`
- **Proposal filtering**: `proposals.items` contains only PROPOSED and ACCEPTED proposals. REJECTED proposals are excluded from primary result; available via `/analyses/{id}/proposal-review` with full history.
- **Evidence curation**: Fixed top-5 items per category by relevance/recency for V1. `evidence.items` contains at most 5 items per category; `evidence.count` shows total available.
- **Next actions**: Capability flags (`available`) indicate whether action is currently permitted based on domain state (e.g., `GENERATE_DELIVERABLE` available only when validated insights exist and deliverable eligibility rules pass).

#### Status-Specific Response Shapes

**FAILED Analysis** (minimal result):
```json
{
  "analysis": { "id": "...", "status": "FAILED", "objective": "...", "scope": "...", "durationSeconds": 45, "sourcesAnalyzed": [...] },
  "execution": { "success": false, "failureCode": "KNOWLEDGE_SELECTION_FAILED", "failureMessage": "Unable to select evidence within token budget" },
  "proposals": { "total": 0, "byStatus": {}, "byType": {}, "items": [] },
  "insights": { "total": 0, "items": [] },
  "deliverables": { "total": 0, "items": [] },
  "evidence": { "facts": {"count":0,"items":[]}, ... },
  "nextActions": [{ "action": "VIEW_DIAGNOSTICS", "label": "View diagnostics", "available": true }]
}
```

**IN_PROGRESS Analysis** (product-level state, no diagnostics polling):
```json
{
  "analysis": { "id": "...", "status": "IN_PROGRESS", "objective": "...", "scope": "...", "startedAt": "...", "sourcesAnalyzed": [...] },
  "execution": { "success": null, "failureCode": null, "failureMessage": null },
  "proposals": { "total": 0, "byStatus": {}, "byType": {}, "items": [] },
  "insights": { "total": 0, "items": [] },
  "deliverables": { "total": 0, "items": [] },
  "evidence": { "facts": {"count":0,"items":[]}, ... },
  "nextActions": []
}
```

**Empty COMPLETED Analysis** (explicit empty states):
```json
{
  "analysis": { "id": "...", "status": "COMPLETED", "objective": "...", "scope": "...", "durationSeconds": 120, "sourcesAnalyzed": [...] },
  "execution": { "success": true, "failureCode": null, "failureMessage": null },
  "proposals": { "total": 0, "byStatus": { "PROPOSED": 0, "ACCEPTED": 0, "REJECTED": 0 }, "byType": {}, "items": [] },
  "insights": { "total": 0, "items": [] },
  "deliverables": { "total": 0, "items": [] },
  "evidence": { "facts": {"count":142,"items":[]}, "observations": {"count":38,"items":[]}, ... },
  "nextActions": []
}
```

### 9. Angular Information Architecture

#### Primary Result Page: `/analyses/{id}/result`

**Replaces** current detail page as the default Analysis landing page.

**Layout**:
```
┌─────────────────────────────────────────────────────────────────┐
│ Analysis Result Header                                          │
│  Objective: "Understand this project"  Scope: Entire Project   │
│  Status: COMPLETED  Duration: 5m  Sources: 2 repos             │
│  [Review Proposals] [Generate Deliverable] [View Diagnostics]  │
├─────────────────────────────────────────────────────────────────┤
│ Proposals Awaiting Review (2)              │ Reviewed Proposals (3) │
│ ┌─────────────────────────────┐           │ ┌─────────────────────┐ │
│ │ 🔴 High coupling detected   │           │ │ ✅ Circular dep    │ │
│ │ Type: INSIGHT  Conf: 92%    │           │ │ Type: ARCHITECTURE │ │
│ │ [Review]                    │           │ │ Result: INSIGHT    │ │
│ └─────────────────────────────┘           │ │ [View Insight]     │ │
│ ┌─────────────────────────────┐           │ └─────────────────────┘ │
│ │ 🔴 Missing error handling   │           │ ┌─────────────────────┐ │
│ │ Type: ENG_DECISION Conf: 88%│           │ │ ✅ API decision    │ │
│ │ [Review]                    │           │ │ Type: ENG_DECISION │ │
│ └─────────────────────────────┘           │ │ Result: DECISION   │ │
│                                             │ │ [View Decision]    │ │
│ [View all proposals] → /proposal-review     │ └─────────────────────┘ │
│                                             │ [View all reviewed]     │
├─────────────────────────────────────────────────────────────────┤
│ Validated Insights (3)                                      │
│ ┌─────────────────────────────┐ ┌─────────────────────────┐   │
│ │ ⚠️ Circular dependency      │ │ ℹ️ Deprecated API      │   │
│ │ Type: ARCHITECTURE          │ │ Type: DOCUMENTATION    │   │
│ │ Severity: WARNING           │ │ Severity: INFO         │   │
│ │ [View Insight]              │ │ [View Insight]         │   │
│ └─────────────────────────────┘ └─────────────────────────┘   │
│ [View all insights]                                         │
├─────────────────────────────────────────────────────────────────┤
│ Deliverables (2)                                            │
│ ┌─────────────────────────────┐ ┌─────────────────────────┐   │
│ │ Architecture Review Q3 2026 │ │ README - Core Module    │   │
│ │ Type: ARCHITECTURE_REVIEW   │ │ Type: README            │   │
│ │ [View]                      │ │ [View]                  │   │
│ └─────────────────────────────┘ └─────────────────────────┘   │
│ [Generate Deliverable] (if insights exist & eligible)        │
├─────────────────────────────────────────────────────────────────┤
│ Supporting Evidence (curated)                                 │
│ Facts (5/142)  Observations (5/38)  Repo Evidence (5/67) ...  │
│ [View all evidence] → /analyses/{id}/selected-evidence       │
├─────────────────────────────────────────────────────────────────┤
│ Provenance & Diagnostics (collapsible)                        │
│ Execution: COMPLETED in 300s  Sources: core, api             │
│ Diagnostics → /analyses/{id}/diagnostics                      │
└─────────────────────────────────────────────────────────────────┘
```

**Header Scope Display**:
- `PROJECT_SCOPE`: "Scope: Entire Project (N sources)"
- `REPOSITORY_SCOPE`: "Scope: Repository: {sourceName}"

**Navigation**:
- `/analyses/{id}` → redirects to `/analyses/{id}/result` (new default)
- `/analyses/{id}/diagnostics` → existing diagnostics page (unchanged)
- `/analyses/{id}/proposal-review` → existing review page (unchanged)
- `/insights/{id}` → existing insight detail (unchanged)
- `/deliverables/{id}` → existing deliverable detail (unchanged)
- `/analyses/{id}/selected-evidence` → existing evidence page (unchanged)
- `/analyses/{id}/deliverables` → deliverable generation (unchanged)

### 10. Contracts That Remain Unchanged

| Contract | Status |
|---|---|
| `Analysis` entity | Unchanged (orchestration aggregate) |
| `AiTask` entity | Unchanged (execution record) |
| `ValidatableProposal` entity | Unchanged (proposal awaiting validation) |
| `Insight` entity | Unchanged (trusted knowledge) |
| `GeneratedDeliverable` entity | Unchanged (post-validation artifact) |
| `AnalysisWorkflowService` | Unchanged (orchestration) |
| `KnowledgeSelectionService` | Unchanged (evidence selection) |
| `ProposalReviewService` | Unchanged (human validation) |
| `ProposalPromotionService` | Unchanged (trusted knowledge promotion) |
| `GET /api/v1/analyses/{id}` | Unchanged (basic metadata) |
| `GET /api/v1/analyses/{id}/diagnostics` | Unchanged (internal diagnostics) |
| `GET /api/v1/analyses/{id}/selected-evidence` | Unchanged (evidence drill-down) |
| `GET /api/v1/analyses/{id}/proposal-review` | Unchanged (validation UI) |
| Proposal review Angular page | Unchanged |
| Insight detail Angular page | Unchanged |
| Deliverable Angular page | Unchanged |
| Evidence Angular page | Unchanged |

---

## Compatibility & Migration

### Existing Analyses
- All existing analyses automatically get a `GET /result` projection
- No data migration required (projection is query-time composition)
- Existing `GET /analyses/{id}` still returns `AnalysisResponse` for backward compatibility

### Angular
- New `AnalysisResultPage` component at `/analyses/{id}/result`
- `AnalysisDetailPage` renamed to `AnalysisDiagnosticsPage` at `/analyses/{id}/diagnostics`
- Router: `/analyses/{id}` → redirects to `/analyses/{id}/result`
- Existing detail page links updated to use new routes

### API Version
- No API version change (`/api/v1` remains)
- New endpoint is additive

---

## Acceptance Criteria

1. **Single canonical result endpoint**: `GET /api/v1/analyses/{id}/result` returns `AnalysisResultResponse` with all sections populated
2. **Human-facing header**: Shows objective, scope (with repository name for REPOSITORY_SCOPE), status, duration, sources analyzed
3. **Proposals section**: Shows PROPOSED and ACCEPTED proposals grouped by status/type, with evidence preview and review action; REJECTED proposals excluded from primary result
4. **Insights section**: Shows all ACTIVE insights from this analysis with severity, rationale, evidence refs
5. **Deliverables section**: Shows all deliverables for this analysis with type, audience, status
6. **Evidence section**: Curated top-5 per category with "View all" drill-down to selected-evidence
7. **Next actions**: Contextual actions (review proposals if PROPOSED exist, generate deliverable if insights exist and domain rules permit)
8. **FAILED analysis**: Returns minimal result (header + failure state/message + diagnostics navigation)
9. **IN_PROGRESS analysis**: Returns product-level state (header + status, no partial findings, no diagnostics polling)
10. **Empty COMPLETED analysis**: Explicit empty states with explanatory messages for each section
11. **Diagnostics unchanged**: `GET /diagnostics` returns identical response
12. **Angular result page**: `/analyses/{id}/result` renders all sections with proper navigation
13. **Default route**: `/analyses/{id}` redirects to `/result`
14. **No domain entity changes**: All existing JPA entities unchanged
15. **No workflow changes**: Analysis execution, proposal validation, promotion unchanged
16. **ADR compliance**: ADR-006/017/020/021/028/030/063 preserved

---

## Resolved Design Decisions (Previously Unresolved Questions)

### Q1: Proposal Summary Granularity → **Option B**
Primary result shows PROPOSED and ACCEPTED proposals only. REJECTED proposals remain available through the complete proposal-review/history surface (`/analyses/{id}/proposal-review`).

### Q2: Evidence Curation Policy → **Fixed top-5 per category for V1**
Simple, predictable curation matching existing `kindAllowance` philosophy. Not configurable.

### Q3: Failed Analysis Result → **Minimal result**
FAILED analyses return header + failure state/message + diagnostics navigation. No partial findings exposed as canonical results.

### Q4: In-Progress Analysis Result → **Product-level state, no diagnostics polling**
IN_PROGRESS returns minimal header + status. No live polling of diagnostics endpoint. If polling is needed for UX, poll the canonical `/result` endpoint itself for status transitions. Diagnostics remains optional expert drill-down.

### Q5: Empty Result State → **Explicit empty states**
Empty sections render with explanatory messages ("No proposals generated", "No validated insights", etc.). Sections are not silently hidden.

### Q6: Cross-Analysis Result Comparison → **Out of scope for P0-B**
Future work: separate "Analysis History" view.

### Q7: Deliverable Generation from Result → **Expose when domain rules permit**
"Generate Deliverable" action appears in result when validated insights exist and existing deliverable eligibility rules pass. No changes to deliverable business rules.

### Q8: Repository-Scope Display → **"Repository: {sourceName}"**
Header displays "Scope: Repository: {sourceName}" for REPOSITORY_SCOPE analyses (e.g., README objective).

---

## Implementation Plan

### Phase 1: Backend Projection (1-2 weeks)
1. Create `AnalysisResultResponse` DTO hierarchy (with status-specific shapes)
2. Implement `AnalysisResultQueryService` composing from repositories
3. Add `GET /api/v1/analyses/{id}/result` endpoint in `AnalysisController`
4. Unit/integration tests for projection logic, status-specific shapes, curation rules

### Phase 2: Angular Result Page (1-2 weeks)
1. Create `AnalysisResultPage` component with sections
2. Create reusable section components (ProposalsSection, InsightsSection, etc.)
3. Update router: `/analyses/{id}` → redirect to `/result`
4. Rename `AnalysisDetailPage` → `AnalysisDiagnosticsPage` at `/diagnostics`
5. Update all internal links

### Phase 3: Integration & Polish (1 week)
1. End-to-end testing
2. Empty/loading/error states
3. Accessibility review
4. Performance verification (projection query optimization)

---

## New ADR Required?

**NO**. This Story introduces a read-model/projection layer only. No domain boundaries change. All governing ADRs are preserved. The projection is a query-time composition over existing domain objects.

---

## Definition of Done

- [ ] Backend `AnalysisResultResponse` DTO and query service implemented (with status-specific shapes)
- [ ] `GET /api/v1/analyses/{id}/result` endpoint functional
- [ ] All 16 acceptance criteria verified
- [ ] Angular `AnalysisResultPage` at `/analyses/{id}/result` complete
- [ ] Router redirect `/analyses/{id}` → `/result` works
- [ ] Existing diagnostics, proposal review, insight, deliverable, evidence pages unchanged
- [ ] All backend tests pass (984+)
- [ ] All frontend tests pass (219+)
- [ ] Lint/format clean
- [ ] Build successful
- [ ] Human review completed

---

## Design Repository State

- Design branch: not created (design artifacts only)
- No production code, tests, commit, push, or merge is part of this design
- Investigation artifact: `docs/investigations/analysis-result-projection-gap.md`

ANALYSIS_RESULT_PROJECTION_STORY_DESIGNED_AWAITING_HUMAN_REVIEW