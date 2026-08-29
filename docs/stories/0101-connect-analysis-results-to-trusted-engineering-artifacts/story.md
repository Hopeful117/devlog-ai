# Story 0101 — Connect Analysis Results to Trusted Engineering Artifacts

## Status

**IMPLEMENTATION_COMPLETE**

**HUMAN_REVIEW_PENDING**

## Priority

**P0-C — ANALYSIS RESULT TO TRUSTED ARTIFACT NAVIGATION**

Follows Story 0100 (canonical Analysis Result) and closes the human-facing provenance loop from
Analysis output to promoted trusted engineering artifacts.

## Objective

Extend the canonical human-facing Analysis Result so a supervising human can follow an accepted
proposal to the trusted engineering artifact that was actually created from promotion, and inspect
 that artifact on a human-facing detail surface.

## Human Story

As a human engineer reviewing an Analysis result,
I want to move from the canonical result to the accepted proposal and then to the trusted artifact
that resulted from promotion,
so that I can verify what the Analysis produced without reconstructing provenance manually or using
diagnostics-only tooling.

## Context / Problem

Story 0100 established the canonical human-facing Analysis result at:

* `GET /api/v1/analyses/{analysisId}/result`
* `/analyses/{analysisId}/result`

That result shows the Analysis objective, execution outcome, proposals, validated insights,
deliverables, curated evidence, and next actions.

What it did not close was the last human-facing loop:

```text
Canonical Analysis Result
  → Proposal
  → Accepted Proposal
  → Promoted Trusted Engineering Artifact
  → Human-readable trusted artifact detail
```

Durable provenance already exists in the persisted model, but only as reverse references from the
trusted artifact back to the originating `ValidatableProposal`. The canonical result therefore needs
to compose that relationship at query time instead of persisting a duplicate inverse reference.

## Goal

Expose promoted trusted artifacts from accepted proposals in the canonical Analysis Result and make
them human-navigable without duplicating persisted provenance.

## Governed By

* ADR-006: proposals remain untrusted until individual human validation; validation and promotion
  remain atomic.
* Story 0100: `AnalysisResult` remains the primary human-facing Analysis surface and a query-time
  read model.
* Existing trusted artifact detail routes: Insights and Engineering Events remain authoritative;
  Decision requires a minimal read-only detail page.

## Architectural Constraints

* ADR-006 lifecycle semantics remain unchanged.
* Existing persisted reverse provenance remains the source of truth:
  * `Insight -> ValidatableProposal`
  * `Decision -> ValidatableProposal`
  * `EngineeringEvent -> ValidatableProposal`
* Do **not** add `promotedArtifactId` to `ValidatableProposal`.
* Do **not** add promoted artifact type persistence to `ValidatableProposal`.
* Do **not** add a provenance table.
* Trusted-artifact resolution occurs at query time in the Analysis Result read model.
* Backend exposes stable artifact identity/type/capability only.
* Angular owns SPA URL construction.

## Trusted Artifact Types

Supported trusted artifact types for Story 0101:

* `INSIGHT`
* `DECISION`
* `ENGINEERING_EVENT`

`CHALLENGE` and `DOCUMENTATION` remain out of scope because Story 0101 does not invent new
promotion semantics.

## Functional Behavior

### PROPOSED

`trustedArtifact = null`

No promoted trusted artifact is implied.

### ACCEPTED + resolved

Expose:

* actual artifact id
* artifact type
* `availability = AVAILABLE`
* `detailAvailable = true`

### ACCEPTED + unresolved

Expose:

* `id = null`
* expected artifact type derived from `ProposalType`
* `availability = UNAVAILABLE`
* `detailAvailable = false`

This is an explicit read-model integrity state. No artifact id or link is fabricated.

### REJECTED

Rejected proposals remain excluded from the canonical Analysis Result according to Story 0100
semantics.

## Navigation Contract

Trusted artifact navigation is a frontend responsibility:

* Insight → `/insights/:id`
* EngineeringEvent → `/engineering-events/:id`
* Decision → `/decisions/:id`

The backend does not emit Angular URLs.

## Decision Surface

Story 0101 adds the smallest coherent human-facing Decision detail surface needed to inspect a
trusted Decision reached from Analysis provenance:

* route: `/decisions/:id`
* source API: `GET /api/v1/decisions/{id}`
* behavior: read-only detail only

The Decision response may expose nullable `proposalId`, mapped from the existing persisted
`Decision.proposal.id`, to preserve human provenance navigation without introducing any new
persistence.

## Query / Performance Contract

Accepted promotable proposal ids are grouped by `ProposalType` and resolved in batch.

Expected query pattern for result composition:

* one proposal query for the Analysis
* at most one Insight batch lookup
* at most one Decision batch lookup
* at most one Engineering Event batch lookup

No N+1 trusted-artifact lookup is allowed.

## Acceptance Criteria

1. `GET /api/v1/analyses/{id}/result` exposes `trustedArtifact` for accepted Insight proposals when
   the promoted Insight exists.
2. `GET /api/v1/analyses/{id}/result` exposes `trustedArtifact` for accepted Decision proposals when
   the promoted Decision exists.
3. `GET /api/v1/analyses/{id}/result` exposes `trustedArtifact` for accepted Engineering Event
   proposals when the promoted Engineering Event exists.
4. `PROPOSED` proposals expose `trustedArtifact = null`.
5. `ACCEPTED` proposals with no resolvable trusted artifact expose the explicit unavailable state and
   never fabricate an id or link.
6. Rejected proposals remain excluded from the canonical Analysis Result.
7. Trusted-artifact resolution uses the existing persisted reverse provenance only.
8. No `promotedArtifactId`, promoted artifact type, duplicate persisted inverse relationship, or
   provenance table is introduced.
9. Resolution is batch-oriented and does not perform one trusted-artifact query per proposal.
10. Insight references from the canonical result navigate to the existing Insight detail page.
11. Engineering Event references from the canonical result navigate to the existing Engineering Event
    detail page.
12. Decision references from the canonical result navigate to a minimal read-only Decision detail
    page.
13. `DecisionResponse` exposes nullable `proposalId` from existing persisted provenance only.
14. Story 0100 canonical Analysis Result routing and polling behavior remain intact.
15. Frontend and backend quality gates remain green.

## Implementation Scope

### Backend

* Extend `AnalysisResultResponse.ProposalSummary` with nullable nested `trustedArtifact`.
* Add query-time resolution in `AnalysisResultQueryServiceImpl`.
* Reuse `InsightRepository.findByProposalIdIn(...)`.
* Reuse `EngineeringEventRepository.findByProposalIdIn(...)`.
* Add `DecisionRepository.findByProposalIdIn(Collection<UUID>)`.
* Expose nullable `proposalId` on `DecisionResponse`.
* Map `Decision.proposal.id` in `DecisionMapper`.

### Frontend

* Extend the Analysis Result proposal model with `trustedArtifact`.
* Extend `AnalysisResultPage` to render trusted-artifact navigation only when available.
* Preserve existing proposal navigation.
* Add minimal Decision detail route, service, model, and page at `/decisions/:id`.

## Explicit Non-Scope

* ADR-006 changes
* proposal acceptance changes
* proposal promotion changes
* new promotion mechanisms
* new trusted knowledge categories
* duplicate provenance persistence
* Analysis intelligence changes
* Story 0098 / category balancing
* Engineering Query
* RAG / vector / retrieval architecture work
* unrelated refactoring

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`

Human approval and merge remain pending.
