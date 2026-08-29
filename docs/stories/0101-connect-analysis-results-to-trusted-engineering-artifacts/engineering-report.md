# Engineering Report — Story 0101

## Delivery State

Story 0101 is implementation-complete. Work remains uncommitted on
`story-101-analysis-trusted-artifact-navigation` at baseline
`d122f894870d317d1c1b025c74d4cddd7c069cc2`; no commit, push, pull request, or merge was performed
by the implementation agent. Pre-existing unrelated worktree changes were preserved.

## Story Outcome

The canonical Analysis Result now exposes promoted trusted artifacts from accepted proposals, closing
the human-facing provenance loop:

```text
Canonical Analysis Result
  → Proposal
  → Accepted Proposal
  → Promoted Trusted Engineering Artifact
  → Human-readable trusted artifact detail
```

Humans can follow an accepted Insight, Decision, or Engineering Event proposal directly to the
trusted artifact that was created from promotion, and inspect that artifact on a human-facing
detail surface.

## Backend

### Read-Model Extension

`AnalysisResultResponse.ProposalSummary` now includes nullable nested `trustedArtifact` containing:

- `id` (UUID)
- `type` (TrustedArtifactType: INSIGHT, DECISION, ENGINEERING_EVENT)
- `availability` (TrustedArtifactAvailability: AVAILABLE, UNAVAILABLE)
- `detailAvailable` (boolean)

### Query-Time Resolution

`AnalysisResultQueryServiceImpl` now resolves trusted artifacts at query time:

1. Fetch proposals by Analysis id
2. Keep existing Story 0100 filtering: PROPOSED + ACCEPTED
3. Collect accepted promotable proposal ids
4. Group ids by ProposalType
5. Batch query trusted artifacts by proposal id
6. Map trusted artifacts by proposal id
7. Enrich each projected ProposalSummary

### Repository Support

New batch support added:

- `DecisionRepository.findByProposalIdIn(Collection<UUID>)`

Existing batch support reused:

- `InsightRepository.findByProposalIdIn(Collection<UUID>)`
- `EngineeringEventRepository.findByProposalIdIn(Collection<UUID>)`

### Decision Provenance Exposure

`DecisionResponse` now exposes nullable `proposalId` mapped from `Decision.proposal.id` via
`DecisionMapper`.

## Frontend

### Canonical Analysis Result Integration

`analysis.models.ts` now models `trustedArtifact` on Analysis result proposals with type definitions
for `TrustedArtifact`, `TrustedArtifactType`, and `TrustedArtifactAvailability`.

`AnalysisResultPage` now renders:

- type-appropriate navigation link when `availability === AVAILABLE` and `detailAvailable === true`
- explicit unavailable state when `availability === UNAVAILABLE` with no fabricated link
- nothing when `trustedArtifact` is null (PROPOSED proposals)

Existing proposal navigation remains intact.

### Decision Detail Page

New read-only Decision detail route and page at `/decisions/:id`:

- `decision.models.ts`: typed `DecisionDetail` interface
- `decision.service.ts`: `GET /api/v1/decisions/{id}` service call
- `decision-detail-page.ts`: loading, not-found, error, loaded states; proposal link when present
- `decision-detail-page.spec.ts`: 4 tests covering states and proposal navigation

### Route Registration

`app.routes.ts` now includes the `/decisions/:id` route with lazy-loaded `DecisionDetailPage`.

`request-error.ts` now supports `'decision'` as a subject type for error handling.

## Acceptance Assessment

All 15 acceptance criteria are satisfied:

1. AC1: Insight proposals expose trustedArtifact when promoted Insight exists
2. AC2: Decision proposals expose trustedArtifact when promoted Decision exists
3. AC3: Engineering Event proposals expose trustedArtifact when promoted Engineering Event exists
4. AC4: PROPOSED proposals expose trustedArtifact = null
5. AC5: ACCEPTED unresolved proposals expose explicit unavailable state with no fabricated link
6. AC6: Rejected proposals excluded from canonical Analysis Result
7. AC7: Resolution uses existing persisted reverse provenance only
8. AC8: No promotedArtifactId, promoted artifact type, duplicate persistence, or provenance table
9. AC9: Resolution is batch-oriented, no N+1
10. AC10: Insight references navigate to existing Insight detail page
11. AC11: Engineering Event references navigate to existing Engineering Event detail page
12. AC12: Decision references navigate to minimal read-only Decision detail page
13. AC13: DecisionResponse exposes nullable proposalId from existing persisted provenance only
14. AC14: Story 0100 canonical Analysis Result routing and polling behavior remain intact
15. AC15: Frontend and backend quality gates remain green

## Verification

### Backend

- focused backend suite: **987 tests passed**, 0 failures, 0 errors, 0 skipped
- full `./mvnw clean verify`: **987 tests passed**, 0 failures, 0 errors, 0 skipped
- JaCoCo 80% bundle line-coverage gate: **PASS**
- PostgreSQL 17/Testcontainers coverage proves batch resolution fidelity, unavailable state semantics,
  proposed-proposal null handling, and rejected-proposal exclusion

### Frontend

- full unit suite: **49 files, 252 tests passed**
- `npm run lint`: **PASS**
- `npm run build`: **PASS**
- `npm run format:check`: **PASS**

### Repository Verification

- `git diff --check`: **PASS**
- No unrelated files modified
- No generated files included in diff

## Scope Guard Confirmation

- ADR-006 lifecycle semantics unchanged
- no new promotion mechanism
- no new trusted knowledge category
- no duplicate provenance persistence
- no Analysis intelligence change
- no Story 0098 work
- no Engineering Query work
- no RAG/vector/retrieval work
- no unrelated refactoring beyond minimal Story support
