# Code Review — Story 0101

## Scope Reviewed

- Backend `AnalysisResultResponse` DTO extension with `TrustedArtifact`, `TrustedArtifactType`,
  `TrustedArtifactAvailability`
- Backend `AnalysisResultQueryServiceImpl` batch resolution logic
- Backend `DecisionRepository.findByProposalIdIn` batch query method
- Backend `DecisionResponse` nullable `proposalId` extension
- Backend `DecisionMapper` proposal.id mapping
- Backend unit test: `AnalysisResultQueryServiceImplTest`
- Backend integration test: `DecisionPromotionProvenancePostgresIntegrationTest`
- Backend WebMvc test: `DecisionControllerWebMvcTest`
- Backend unit test: `DecisionServiceTest`
- Frontend `analysis.models.ts` trusted artifact types
- Frontend `AnalysisResultPage` trusted artifact navigation rendering
- Frontend `AnalysisResultPage` trusted artifact styles
- Frontend `AnalysisResultPage` trusted artifact tests
- Frontend `app.routes.ts` Decision route registration
- Frontend `app.routes.spec.ts` Decision route test
- Frontend `request-error.ts` decision subject type
- Frontend new Decision detail page, service, models, and tests
- Story 0101 lifecycle documentation

Unrelated dirty and generated worktree files were excluded from the review.

## Findings

No blocking findings remain.

## Architecture and Contract

- `AnalysisResultResponse.ProposalSummary.trustedArtifact` is nullable. When null, no trusted
  artifact is implied (PROPOSED proposals). When present, it carries AVAILABLE or UNAVAILABLE
  semantics with an explicit `detailAvailable` flag.
- Resolution is batch-oriented: proposal ids are grouped by ProposalType and resolved in at most
  three batch queries. No per-proposal query is introduced.
- The `TrustedArtifactLookup` inner record caches the resolved map. The `trustedArtifactFor` method
  falls back to UNAVAILABLE when an accepted proposal has no matching trusted artifact. This is an
  explicit integrity state, not a fabrication.
- `DecisionRepository.findByProposalIdIn` accepts `Collection<UUID>` and returns `List<Decision>`.
  Spring Data JPA generates the `IN` clause. The existing `findByProposalId(UUID)` method is
  unchanged and available for single-lookups elsewhere.
- `DecisionResponse.proposalId` is nullable. When null, no proposal provenance exists. When present,
  it maps from `Decision.proposal.id` via `@Mapping(target="proposalId", source="proposal.id")`.
- Angular route construction follows existing patterns: `DecisionDetailPage` is lazy-loaded via
  `loadComponent`. The backend emits stable IDs and types; Angular constructs the SPA URLs.

## Persistence and Provenance Rules

- No `promotedArtifactId` or promoted artifact type is added to `ValidatableProposal`.
- No provenance table is introduced.
- The only source of truth for trusted artifact provenance remains the reverse reference on the
  trusted artifact entity (`Insight.proposal`, `Decision.proposal`, `EngineeringEvent.proposal`).
- The `Decision.proposal` field is `@OneToOne(LAZY)` with `@JoinColumn(name="proposal_id",
  updatable=false, unique=true)`, nullable. This was established by Story 0077 and is unchanged.

## Query Behavior and N+1 Risk

- One proposal query fetches all proposals for the Analysis.
- Accepted promotable proposals are grouped by ProposalType in memory.
- At most three batch queries are executed (one per trusted artifact type), each using
  `findByProposalIdIn(Collection<UUID>)`.
- No N+1 trusted-artifact query is introduced.
- The unit test `resolvesAcceptedArtifactsAndPreservesProposalStatesWithoutNPlusOneQueries` verifies
  the exact repository calls and batch sizes.

## Error / Empty / Unavailable States

- PROPOSED proposals: `trustedArtifact = null`. No artifact is shown.
- ACCEPTED + resolved: `AVAILABLE` with id, type, and `detailAvailable = true`. Navigation link
  rendered.
- ACCEPTED + unresolved: `UNAVAILABLE` with id = null, expected type, `detailAvailable = false`.
  Explicit "unavailable" label rendered. No link fabricated.
- REJECTED: excluded from the canonical Analysis Result entirely (existing Story 0100 behavior).
- Malformed/contradictory snapshots: not applicable to this Story (no snapshot projection involved).

## Test Coverage

### Backend

- `AnalysisResultQueryServiceImplTest`: 2 tests
  - batch resolution with mixed proposal types (PROPOSED, ACCEPTED resolved, ACCEPTED unresolved,
    REJECTED), verifying exact repository calls, null/unavailable/available states, and no N+1
  - in-progress Analysis skips proposal and trusted artifact resolution entirely

- `DecisionPromotionProvenancePostgresIntegrationTest`: 1 new test
  - `findByProposalIdInReturnsPromotedDecisionsInBatch`: PostgreSQL integration proof that batch
    query returns correct decisions for multiple proposal ids

- `DecisionControllerWebMvcTest`: updated assertions
  - Verifies `proposalId` is present in GET response JSON

- `DecisionServiceTest`: updated constructors
  - Null `proposalId` added to existing test data to match new `DecisionResponse` record shape

### Frontend

- `analysis-result-page.spec.ts`: 5 new tests
  - PROPOSED proposal has no trusted-artifact action
  - Insight trusted-artifact navigation renders
  - Decision trusted-artifact navigation renders
  - Engineering Event trusted-artifact navigation renders
  - UNAVAILABLE trusted artifact renders without fabricated link

- `decision-detail-page.spec.ts`: 4 tests
  - Renders Decision with proposal navigation
  - Omits proposal section when proposalId is null
  - Shows loading, not-found, and error states
  - Does not manually subscribe

- `app.routes.spec.ts`: 1 new test
  - Verifies `/decisions/:id` routes to `DecisionDetailPage`

## Security and Human Factors

- No `innerHTML` or sanitizer bypass is introduced.
- All trusted artifact data is rendered through Angular interpolation.
- Failure messages remain sanitized through the existing `toRequestError` path.
- The `'decision'` subject type is added to `request-error.ts` for consistent error handling.

## Residual Risks

- The application still has no authentication or authorization boundary.
- The Decision detail page does not yet have Playwright E2E coverage; component tests cover semantic
  structure, states, and error behavior.
- The `DecisionRepository.findByProposalIdIn` method is unbounded; however, the caller controls the
  input set (accepted promotable proposals for a single Analysis), so the batch size is naturally
  bounded.

## Verdict

**APPROVED_FOR_COMMIT_APPROVAL** — no blocking findings remain; the implementation is additive,
fail-closed, tested, and aligned with all 15 acceptance criteria and the ADR-006 boundary.
