# Story 0091 — Code Review

Date: 2026-08-26 · Reviewer scope: all commits `0173b8c`, `f2d36ec`,
`e11e6dd`, `c6f027b` on `feature/story-0091-project-freshness-checkpoints`.

## Freshness correctness

- [x] **Post-0085 baselines work.** `findLatestComparable` no longer filters
  `targetRevision IS NULL`; pinned by
  `ProjectFreshnessBaselinePostgresIntegrationTest.
  post0085CompletedRevisionPinnedSnapshotBecomesTheFreshnessBaseline`
  (fails on the pre-fix query — verified during development).
- [x] **CURRENT/STALE correct.** Probe path classification untouched;
  checkpoint path records CURRENT only when knowledge was just built at the
  observed revision; runtime scenario reproduced STALE after a new commit.
- [x] **No false NO_BASELINE.** Legacy null-revision snapshots remain
  eligible (`newestComparableSnapshotWinsAndLegacyRowsRemainEligible`);
  IN_PROGRESS-with-snapshot eligible before AI completion; FAILED excluded
  (`failedRunsNeverAnchorFreshness`).
- [x] Baseline semantics stay ADR-061-compliant: persisted baseline is always
  an actually observed resolved revision, never inferred from timestamps or
  latest ProjectCommit.

## Read honesty

- [x] Stale context produces `PROJECT_CONTEXT_STALE`; mixed multi-source
  produces `PROJECT_CONTEXT_PARTIALLY_FRESH` (mapper tests).
- [x] `repositoryRevision` / `contextRevision` are explicit in
  `metadata.freshness`; single-source derivation only — multi-source projects
  get the breakdown instead of a fabricated scalar (no lying contract).
- [x] The hidden read-path divergence (structure served at HEAD while
  knowledge sits at baseline) now flips exposed status to STALE via the
  single-source live-divergence override (`shouldWarnWhenServedRevision-
  DivergesFromKnowledgeBaseline`).
- [x] No silent stale context: even “nothing recorded + repository observed”
  resolves to NO_BASELINE + warning instead of a clean-looking response
  (`shouldReportNoBaselineInsteadOfCleanStateWhenOnlyObservationExists`).

## Architecture

- [x] **No scheduler / polling / sync job / RepositoryChanged event /
  implicit AI** anywhere in the diff (verified by inspection; vocabulary
  absent).
- [x] Change detection ≠ synchronization ≠ understanding formalized in
  ADR-062 without premature lifecycle states (`SYNCING`/`FAILED` not added —
  nothing could produce them honestly yet).
- [x] Checkpoint writes have one ownership point
  (`ProjectFreshnessService.recordObservedBaseline` → package persistence);
  understanding pipeline never touches repositories directly and reuses the
  already-resolved revision (no duplicate HEAD probing).
- [x] Recording failure cannot fail a refresh (swallow-and-log verified by
  `keepsTheRefreshSuccessfulWhenCheckpointRecordingFails`).
- [x] MCP adapter thin: passthrough client method, no freshness computation,
  no Git access in mcp-server.

## Resource

- [x] `devlog://projects/{projectSlug}/freshness` is read-only (GET-backed),
  project-scoped through slug→id resolution plus the projectId-scoped backend
  endpoint; no cross-project path exists (`shouldOnlyQueryTheResolvedProject-
  ScopedFreshnessEndpoint` proves exactly one scoped call).
- [x] Reuses existing business logic (`summary()`); no second implementation.
- [x] URI template ⇄ factory anti-drift guards extended
  (`freshnessTemplateMatchesFactory`,
  `DevlogResourceUriFactoryTest.shouldBuildEveryArtifactUri`).
- [x] Unknown project → clean RESOURCE_NOT_FOUND; backend absence mapped per
  ResourceSupport conventions.

## Compatibility

- [x] Contract evolution additive: one nullable field + one new record;
  existing fields and warnings preserved; both direct constructor call-sites
  updated (backend WebMvc fixture, MCP tool unit test).
- [x] Maintenance non-regression: evaluation/remediation flows untouched and
  covered by the full `mvn verify` run; they now benefit from corrected
  baselines automatically.
- [x] Existing MCP resources/tools unaffected (full mcp-server suite green);
  discovery gains one template.
- [x] No migration needed (physical schema unchanged; semantic clarification
  of `current_revision` documented in ADR-062 §2).

## Observations / notes for reviewers

1. `requestedRevision` on recorded checkpoints is `"origin/<defaultBranch>"`;
   probes store the same shape — consistent display.
2. After a refresh whose analysis later FAILS (AI tail), the row remains
   CURRENT until the next probe/classification against a comparable baseline
   (FAILED analyses are excluded from lookup). Acceptable transient honesty
   gap; the future sync lifecycle owns FAILED semantics (ADR-062 §7 note).
3. The dead-end `PROJECTION_REFRESH_GAP` type and orphaned
   `TemporalAssessmentService` remain intentionally out of scope.
