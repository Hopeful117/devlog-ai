# Repository Analysis — Story 0021

## Status

Ready for Human Approval Gate 1.

## Executive conclusion

Story 0021 addresses a real and now measurable gap. DevLog persists immutable revision provenance
for every Project Understanding and can synchronize an active Git Source deterministically, but no
domain service compares those two authorities. The Project cockpit consequently substitutes
presence heuristics for freshness and currently emits two unsupported claims: “Workspace is up to
date” when Sources, Analyses, and Deliverables merely exist, and “monitoring configured sources”
although ADR-041 passive monitoring is not implemented.

The capability can be added without AI, background jobs, semantic commit analysis, or automatic
refresh. The smallest coherent design is an explicitly invoked, Source-scoped Core application
service that resolves the current default commit through the existing Git infrastructure, selects
the latest comparable completed Project Understanding, extracts its immutable resolved revision,
and returns a versioned operational projection. Angular renders that result and routes the user to
the existing refresh action. Engineering Story Context can derive a bounded freshness warning from
the same classifier when it already builds current Repository Context; it must not silently launch
Project Understanding.

No third-party component is needed. The repository already owns Git command execution, workspace
confinement, synchronization locking, Analysis provenance, proposal counts, compact projection,
and standard API errors. A new library or monitoring platform would add more surface than value.

## DevLog context outcome

The configured DevLog adapter completed successfully with the full Story and returned 32 selected
evidence items plus seven warnings. It prioritized API errors, repository-context internals,
historical context work, and older Git evidence. Its newest directly selected Story evidence was
mostly from Stories 0016–0017; it did not surface the current Project Understanding, proposal-review,
or Project cockpit implementation needed for exact design.

This is useful evidence for the Story itself: the current live DevLog knowledge and Repository
Context can lag the repository while providing no explicit freshness status. The context guided
navigation, but all conclusions below were verified against current `main` at `f67344c`.

## Measured live gap

The real DevLog Project is a safe read-only representative:

* Project ID: `f3d56247-aada-4a76-982b-e6802c0b309c`;
* active Source ID: `7819103b-37e7-4e15-95ec-fff9a12d21e4`;
* Source default branch: `main`;
* latest persisted Project Understanding Analysis:
  `bd71ca14-88fa-4028-b3f9-91365d931b44`;
* Analysis diagnostic resolved revision:
  `b2f2c8881bf8b7b89331c5161ca4f8cad16cd3f4` (Story 0017);
* current repository `HEAD`:
  `f67344c0f2f49fa1d938a0c0e68f496e1f85c69e` (Story 0020);
* live review state: six proposals pending, zero accepted, zero rejected.

DevLog is therefore demonstrably stale by commit identity while its cockpit is capable of showing
an up-to-date message. The six pending proposals are a separate validation condition: they do not
make the analyzed commit stale, and accepting them would not make the repository revision current.

## Existing backend ownership

### Git synchronization

`GitWorkspaceManager` is the only current Git workspace authority. For each persisted Source it:

1. acquires an in-process `ReentrantLock` keyed by Source UUID;
2. confines the workspace below `collection.workspace-root`;
3. clones or repairs the workspace;
4. updates `origin`, fetches with prune, cleans the worktree;
5. resolves the explicit revision, configured default branch, or `origin/HEAD`;
6. checks out and hard-resets a detached revision;
7. returns `SynchronizedWorkspace(sourceId, path, resolvedRevision)`.

This already supplies deterministic SHA resolution, network/error behavior, safe path ownership,
and equivalent-operation serialization inside one application instance. It is preferable to a new
JGit dependency or a second shell executor.

The current `synchronize` operation does more than a freshness check needs: it cleans, checks out,
and resets the shared workspace. Calling it directly is functionally valid under its lock, but
would make a read-like check unnecessarily invasive and could surprise future callers. The
implementation plan should choose between:

* a focused current-revision operation added to `WorkspaceManager`/`GitWorkspaceManager`, sharing
  the same lock, remote configuration, fetch/recovery, and revision-resolution code; or
* explicit reuse of full synchronization with the mutation and concurrency semantics documented.

The first option is recommended. It keeps a single Git owner while avoiding checkout/clean when a
commit comparison is sufficient.

The lock is process-local, not a distributed lock. That is consistent with the present local
Compose architecture, but Story 0021 must not claim multi-instance serialization.

### Source synchronization metadata

`Source.lastSynchronizedAt` exists and is exposed to Angular. It is updated by
`KnowledgeCollectionServiceImpl` only after collection iterates the Source; preparation's earlier
workspace synchronization does not update it. The field stores a timestamp, not a commit SHA, and
cannot prove freshness.

It should not be reused as the comparison authority. A lightweight check that fetched a commit but
did not collect knowledge also should not silently redefine “last synchronized” unless the
canonical meaning is deliberately changed and documented. Commit identity must come from the check
result and immutable Analysis provenance.

### Project Understanding identity and provenance

Story 0018 added exactly the fields required to identify comparable baselines:

* `Analysis.selectedSource` and immutable `selectedSourceSnapshot`;
* `understandingExecutionKey` for active-run deduplication;
* persisted normalized `intentId`/`intentVersion`;
* optional `targetRevision`;
* `status`, `completedAt`, and creation timestamps.

`ProjectUnderstandingPreparationService` resolves catalog key `describe-project-v1`, whose
persisted identity is `intentId=describe-project`, `intentVersion=v1`. It validates Project/active
Source ownership, synchronizes the Source, imports history, and then claims/starts an Analysis.
`ProjectUnderstandingClaimService` snapshots Source identity and stores an explicit target revision
when supplied.

Baseline selection should therefore be an explicit repository query, not “latest Analysis” and
not “latest Project Profile” alone. For a default-branch freshness check it should require:

* matching Project and selected Source;
* `status=COMPLETED`;
* `intentId=describe-project` and the supported version policy;
* `targetRevision IS NULL` so a deliberate historical tag/SHA does not become the default-branch
  baseline;
* deterministic ordering by `completedAt DESC`, then `createdAt DESC`, then `id DESC`.

Older generic Analyses created before Source ownership existed cannot safely become Source-scoped
baselines. They should yield `NO_BASELINE`, not be guessed from Project identity.

### Immutable analyzed revision

The actual analyzed SHA is already persisted twice as immutable execution output:

* `AnalysisExecutionDiagnostic.resolvedRevisions`;
* `ProjectProfileSnapshot.resolvedRevisions`.

Both maps are keyed by Source UUID string. The profile is produced from the diagnostic and includes
the Analysis link, generated time, requested revision, completeness, and deterministic summary.
The diagnostic is closer to collection execution; the profile is the Project Understanding output
already consumed by Project Context.

The implementation should establish one canonical extraction owner and treat all of these as
`UNKNOWN`, never `CURRENT`:

* missing diagnostic/profile;
* missing Source key;
* null, blank, malformed, or non-commit value;
* conflicting revision values if both stores are cross-checked.

No database migration is required merely to compare these existing immutable values. Persisting a
new freshness-check table is not justified for the first explicit-check slice unless the approved
plan identifies a concrete cross-request audit requirement that cannot be met by structured logs
and the returned `checkedAt`.

### Proposal review state

Story 0020 added Analysis-level total and status counts to
`ValidatableProposalRepository`. These can support review-state separation with fixed aggregate
queries. Freshness must not copy proposal lifecycle state into an independent table or infer
Trusted Knowledge completeness from accepted/rejected totals.

## Existing API and error conventions

Project Understanding uses:

`POST /api/v1/projects/{projectId}/understanding-executions`

with `sourceId`, optional `targetRevision`, and optional guidance. Standard errors use the shared
`EntityNotFoundException`, `InvalidParameterException`, conflict, validation, and correlation-ID
contracts. Source ownership is already expressed by
`findByIdAndProject_IdAndActiveTrue(sourceId, projectId)`.

A freshness check contacts Git and may update the managed fetch state, so a command-style `POST` is
more honest than a cacheable `GET`. A cohesive candidate is:

`POST /api/v1/projects/{projectId}/freshness-checks`

with a bounded body containing only `sourceId`. Exact naming and failure serialization belong in
the Implementation Plan. The endpoint should return a normal stable error for Git resolution
failure unless product requirements demand persisted failed-check resources. Returning a synthetic
successful `CHECK_FAILED` state would blur HTTP success and operational failure; the existing
request-error flow already supports retaining prior UI state and retry. Repository evidence favors
standard error propagation plus a client-visible non-current failure state.

## Existing Angular ownership and defects

`ProjectDetailPage` currently joins five independent resources: Project, Sources, Analyses,
Deliverables, and Insights. Failed secondary reads are converted to empty arrays, so the cockpit
already trades diagnostic precision for availability.

Its template contains the exact unsupported heuristics:

* “Last synchronized” displays `project.updatedAt`, not `Source.lastSynchronizedAt`;
* “Workspace is up to date” appears when Sources, Analyses, and Deliverables are merely non-empty;
* “Repository Agent — Idle · monitoring configured sources” implies ADR-041 behavior that does not
  exist;
* “Last analysis” selects any latest Analysis, not the latest comparable Project Understanding;
* Project Understanding shows “Refresh” if any `describe-project/v1` Analysis exists, regardless
  of status, Source, target revision, or baseline validity.

The existing `ProjectUnderstandingSection` is the correct refresh authority and already prevents
duplicate clicks with `exhaustMap`. Freshness presentation should be a focused child component or
view-model resource rather than further expanding template heuristics. A stale result can preselect
the checked Source and direct the user to the existing form; it must not call `execute` itself.

Accessibility patterns already exist: semantic forms/buttons, `role=status`, `role=alert`, disabled
pending actions, and responsive SCSS. The new states need textual labels and live announcements;
color cannot be the only signal.

## Engineering Story Context impact

`EngineeringStoryContextServiceImpl` builds Project Context first, then current Repository Context,
then the compact agent projection. `ProjectContextSnapshot` contains the latest Project Profile and
recent Analyses but no freshness field. `AgentEngineeringStoryContext` currently mirrors the full
Project Context plus compact Repository Context.

The Repository Context already carries resolved revisions from repository evidence. Therefore an
Engineering Story Context request has current revision material as part of its explicit context
build and need not launch Project Understanding or a separate hidden freshness request. A focused
classifier can compare those current revisions with the latest comparable profile and add a tiny
versioned freshness summary before canonical byte accounting and projection digest calculation.

Important constraints:

* freshness metadata must participate in canonical byte/token accounting and the projection
  digest;
* both compact and `detail=full` contracts need an intentional compatibility decision;
* the installed adapter only requires `repositoryContext.evidence`, so adding a top-level optional
  freshness field is transport-compatible, but backend contract tests must prove this;
* multi-Source context may contain several current revisions. The plan must bound per-Source
  summaries and avoid collapsing mixed states into a misleading project-wide `CURRENT`;
* building Engineering Story Context is itself an explicit user/agent request, so deriving
  freshness from revisions already collected during that request does not violate the “no check on
  Project open” rule.

The current `ProjectContextProviderImpl` declares `MAX_RELATED_ANALYSES=10` but loads all Project
Analyses without a Pageable. That unrelated pre-existing bound defect should be recorded, not
silently expanded into Story 0021 unless it directly blocks the compact freshness contract.

## Architecture and ADR alignment

The Story is aligned with:

* ADR-023: persistent synchronized workspaces and explicit resolved revisions;
* ADR-025/027: diagnostics and Project Profile snapshots are revision-traceable identities;
* ADR-037–040, ADR-044–046: repository evidence remains bounded navigation context and the current
  repository remains authoritative;
* ADR-041: repository freshness is a future passive-monitoring projection, but manual refresh is a
  valid trigger source;
* ADR-043: stale-analysis indicators are operational projections, not Trusted Knowledge, and
  authoritative autonomous changes remain forbidden.

Story 0021 implements the deterministic operational primitive only. It does not authorize the
AgentJob, scheduler, notification, permission, budget, or significance machinery described by the
long-term ADRs.

## Recommended domain boundary

A cohesive backend package such as `projectfreshness` should own:

* Source-scoped check request/response DTOs;
* versioned `FreshnessStatus` and `RefreshGuidance` enums;
* baseline query and revision extraction;
* pure commit comparison/classification;
* orchestration of current-revision resolution;
* bounded proposal-review summary where required;
* controller and Engineering Story Context adapter/projection integration.

The pure classifier should accept explicit nullable outcomes and be exhaustively unit tested. Git
resolution remains in collection/workspace infrastructure; Analysis, profile/diagnostic, and
proposal repositories remain persistence owners. Angular receives the projection and does not
recompute status from timestamps or SHAs.

## Key design decisions for Implementation Planning

1. **Failure contract:** prefer standard non-2xx Git/workspace error plus retained UI state over a
   successful synthetic `CHECK_FAILED` resource, unless persisted check history is introduced.
2. **Persistence:** prefer an ephemeral versioned result with `checkedAt`; add no table initially.
3. **Git operation:** add a lightweight revision-resolution operation under the same Source lock,
   reusing fetch/recovery and validation without checkout/clean/reset.
4. **Baseline:** latest completed default-revision `describe-project/v1` for the exact Source;
   explicit historical analyses are not substitutes.
5. **Unknown provenance:** missing or invalid SHA yields `UNKNOWN`; equality is the sole path to
   `CURRENT`.
6. **Context integration:** derive bounded per-Source freshness from Repository Context revisions
   already built by the explicit Engineering Story Context request and include it in canonical
   projection accounting/digest.
7. **UI ownership:** a focused freshness component drives check state and refresh guidance; the
   existing Project Understanding component remains the only launcher.

## Risks and mitigations

### Git check mutates or races with collection

Risk: a second Git path could fetch or change the same workspace while collection reads it.

Mitigation: keep resolution inside `GitWorkspaceManager` and share its Source lock. Avoid a new Git
executor or independent lock map.

### Incorrect baseline selection

Risk: latest generic, failed, cross-Source, or historical Analysis is treated as current baseline.

Mitigation: one explicit deterministic repository query plus PostgreSQL integration coverage.

### False `CURRENT`

Risk: timestamps, missing provenance, shortened SHA, or fallback Source metadata creates a green
state.

Mitigation: validate full commit identifiers and permit `CURRENT` only for two equal authoritative
SHAs. Everything incomplete becomes `UNKNOWN`/error.

### Hidden scope expansion into passive monitoring

Risk: scheduled checks, notifications, background retry, or automatic refresh enter the slice.

Mitigation: no scheduler, webhook, AgentJob, notification, or AI call; explicit POST only.

### Compact context regression

Risk: adding operational metadata breaks the Story 0019 byte/token guarantee or digest semantics.

Mitigation: tiny bounded DTO, canonical accounting inclusion, projection-size tests, adapter
compatibility tests, and live payload measurement.

### Real-project validation mutates knowledge

Risk: proving freshness by refreshing the real project generates new proposals and operational
records.

Mitigation: the stale check is safe on the real Project; use a disposable local Git Project for the
stale→refresh→current full workflow unless Ludovic separately authorizes a real refresh. Never
accept/reject proposals automatically.

## Test and validation implications

Backend coverage should include:

* pure state/guidance matrix;
* Project/Source ownership and inactive/non-Git cases;
* deterministic latest-baseline ordering and default-revision filtering;
* missing, malformed, mismatched, and equal revisions;
* fetch/resolve failure propagation without knowledge mutation;
* same-Source concurrency through the existing workspace lock;
* proposal counts scoped to the baseline Analysis;
* controller serialization/error contracts;
* full and compact Engineering Story Context compatibility, bounds, and digests;
* real PostgreSQL repository query behavior.

Frontend coverage should include unchecked, pending, current, stale, no-baseline, unknown, and
error states; explicit retry; Source selection; refresh guidance without automatic execution;
truthful cockpit copy; proposal-state separation; accessibility semantics; and responsive layout.

Repository validation must include complete backend/frontend/adapter suites, JaCoCo, Angular
production build and formatting, authenticated SonarQube with Quality Gate wait, Docker rebuild,
API/UI deep links, stale→current disposable workflow, live read-only DevLog stale evidence, and
`git diff --check`/status review.

## Documentation impact

Implementation will materially affect canonical documentation:

* `README.md`: API and user workflow;
* `docs/architecture.md`: explicit freshness projection versus passive monitoring;
* `docs/ui-ux.md`: truthful cockpit states and actions;
* `docs/roadmap.md`: close the manual short-term freshness primitive while retaining ADR-041 as
  future work;
* relevant backend/frontend package READMEs if their current contracts are documented;
* a focused ADR only if planning changes the meaning of Source synchronization metadata or adds
  persisted freshness state. The recommended ephemeral design does not require an ADR merely for
  implementation detail.

## Open questions

No blocking product question remains for planning. The Implementation Plan must make the API
failure shape, Git resolver refactor, multi-Source context bound, and persistence decision exact.
If implementation evidence shows that current revision resolution cannot safely share the existing
workspace lock without checkout, planning must return to the human rather than introduce an
independent Git path silently.

## Recommendation

Approve Repository Analysis and proceed to Implementation Planning. Story 0021 is the correct final
short-term slice: it turns existing revision provenance into honest, explicit guidance while
preserving all human-validation and future-autonomy boundaries.
