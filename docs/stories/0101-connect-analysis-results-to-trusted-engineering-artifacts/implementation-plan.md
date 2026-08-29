# Story 0101 — Connect Analysis Results to Trusted Engineering Artifacts — Implementation Plan

## Status

**APPROVED**

Human Implementation Plan Review: **APPROVED**

## Purpose

Implement Story 0101 by extending the canonical Analysis Result read model with query-time trusted
artifact references and wiring the canonical frontend result page to those references, while
preserving ADR-006 lifecycle semantics and avoiding duplicate provenance persistence.

## Governing Constraints

* `AnalysisResult` remains a query-time read model.
* Persisted provenance source of truth remains on trusted artifacts only.
* No `promotedArtifactId` or promoted artifact type is added to `ValidatableProposal`.
* No provenance table is introduced.
* Angular constructs SPA routes.

## Backend Strategy

### Read-model extension

Extend `AnalysisResultResponse.ProposalSummary` with nullable nested `trustedArtifact` containing:

* `id`
* `type`
* `availability`
* `detailAvailable`

### Query-time resolution

Inside `AnalysisResultQueryServiceImpl`:

1. fetch proposals by Analysis id
2. keep existing Story 0100 filtering: `PROPOSED` + `ACCEPTED`
3. collect accepted promotable proposal ids
4. group ids by `ProposalType`
5. batch query trusted artifacts by proposal id
6. map trusted artifacts by proposal id
7. enrich each projected `ProposalSummary`

### Repository support

Use existing batch support:

* `InsightRepository.findByProposalIdIn(...)`
* `EngineeringEventRepository.findByProposalIdIn(...)`

Add missing batch support:

* `DecisionRepository.findByProposalIdIn(Collection<UUID>)`

### Decision provenance exposure

Extend `DecisionResponse` with nullable `proposalId` mapped from `Decision.proposal.id`.

## Frontend Strategy

### Canonical Analysis Result integration

Extend `frontend/src/app/features/analyses/analysis.models.ts` proposal projection with
`trustedArtifact`.

Update `AnalysisResultPage` so that:

* PROPOSED proposals keep only proposal navigation
* ACCEPTED + AVAILABLE trusted artifact shows type-appropriate navigation
* ACCEPTED + UNAVAILABLE shows explicit unavailable state
* existing Story 0100 polling and routing remain unchanged

### Decision detail page

Add minimal read-only Decision frontend functionality:

* route: `/decisions/:id`
* service: `GET /api/v1/decisions/{id}`
* page states: loading, not found, error, loaded
* content: title, context, choice, rationale, consequences, project metadata, proposal link when
  present

## Files Involved

### Backend

* `backend/.../analysis/result/dto/AnalysisResultResponse.java`
* `backend/.../analysis/result/service/AnalysisResultQueryServiceImpl.java`
* `backend/.../decision/repository/DecisionRepository.java`
* `backend/.../decision/dto/response/DecisionResponse.java`
* `backend/.../decision/mapper/DecisionMapper.java`

### Frontend

* `frontend/src/app/features/analyses/analysis.models.ts`
* `frontend/src/app/features/analyses/result/analysis-result-page.ts`
* `frontend/src/app/features/analyses/result/analysis-result-page.html`
* `frontend/src/app/features/analyses/result/analysis-result-page.scss`
* `frontend/src/app/app.routes.ts`
* `frontend/src/app/features/decisions/decision.models.ts`
* `frontend/src/app/features/decisions/decision.service.ts`
* `frontend/src/app/features/decisions/decision-detail-page.ts`

## Test Strategy

### Backend

* accepted Insight proposal resolves available trusted artifact
* accepted Decision proposal resolves available trusted artifact
* accepted Engineering Event proposal resolves available trusted artifact
* proposed proposal has `trustedArtifact = null`
* accepted unresolved proposal exposes unavailable state
* mixed proposal types batch resolve correctly
* rejected proposal behavior remains unchanged
* Decision batch repository method resolves results
* `DecisionResponse` exposes existing proposal provenance

### Frontend

* Insight trusted-artifact navigation
* Engineering Event trusted-artifact navigation
* Decision trusted-artifact navigation
* no trusted-artifact action for PROPOSED
* unavailable trusted artifact renders without fabricated link
* existing proposal navigation remains functional
* Decision detail success, not-found, error, and proposal-link behavior
* existing Story 0100 routing and polling remain green

## Quality Gates

Frontend:

* full tests
* lint
* production build
* format check

Backend:

* full Maven reactor test suite

## Expected Outcome

The canonical Analysis Result becomes the human-facing provenance entry point to trusted artifacts
without changing proposal lifecycle semantics or introducing a second persisted provenance model.
