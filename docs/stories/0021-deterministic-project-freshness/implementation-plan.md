# Implementation Plan — Story 0021

## Status

Human-approved at Gate 2; implementation completed.

## Overview

Implement an explicit Source-scoped freshness-check resource and a small persisted latest-check
projection. A check will resolve the current default Git commit through the existing workspace
owner, select the latest comparable completed Project Understanding, classify the two immutable
commit identities, attach proposal-review counts, persist the successful operational result, and
return a versioned DTO. The Project cockpit will load the last successful result without contacting
Git, allow the user to check explicitly, and direct stale/no-baseline users to the existing Project
Understanding form without launching it automatically.

Engineering Story Context will transport the bounded latest explicit check, including `checkedAt`,
so agents can distinguish current, stale, absent, and unknown Project Understanding as of a stated
time. It will not perform a second hidden Git operation. Persisting one latest result per Source is
justified by this cross-request requirement and by the cockpit's need to retain “last checked” state
after reload; no unbounded check history will be stored.

No scheduler, webhook, AgentJob, notification, AI call, semantic commit analysis, automatic
understanding refresh, or automatic proposal decision will be introduced.

## Approved inputs

Implementation will use:

* Story 0021;
* the human-approved Repository Analysis;
* the current `main` repository and its existing Git workspace, Analysis, Project Profile,
  proposal-review, Project cockpit, Project Understanding, and agent-context contracts;
* ADR-023, ADR-025, ADR-027, ADR-037–041, ADR-043–046;
* the real read-only stale DevLog example (`b2f2c888…` analyzed versus `f67344c…` current) as a
  validation input, not as permission to refresh or decide its proposals.

No production implementation work may begin before explicit human approval of this plan.

## Contract decisions

### Freshness states

Add stable enums:

* `NO_BASELINE` — no completed default-revision `describe-project/v1` Analysis exists for the exact
  Source;
* `CURRENT` — validated current and baseline commit IDs are identical;
* `STALE` — validated current and baseline commit IDs differ;
* `UNKNOWN` — a comparable baseline exists but its persisted revision provenance is missing,
  malformed, ambiguous, or conflicting.

Git resolution failure is not a successful freshness state. It returns a standard non-2xx API
error; Angular exposes a local `check-failed` interaction state and retains the last successful
result. This avoids representing an unsuccessful current-revision resolution as a valid check.

Add guidance enum:

* `ESTABLISH_BASELINE` for `NO_BASELINE`;
* `REFRESH_NOT_NEEDED` for `CURRENT`;
* `REFRESH_RECOMMENDED` for `STALE`;
* `VERIFY_BASELINE` for `UNKNOWN`.

The classifier is a pure exhaustive mapping. Only equal valid authoritative commit IDs can produce
`CURRENT`.

### Commit identity policy

Normalize Git output to lowercase and accept complete hexadecimal object IDs of 40 characters
(SHA-1) or 64 characters (SHA-256). Do not accept abbreviated SHAs, branch names, timestamps, or
arbitrary strings as analyzed/current revisions. Equality is exact after normalization.

### APIs

Add:

`POST /api/v1/projects/{projectId}/freshness-checks`

Request:

```json
{ "sourceId": "<uuid>" }
```

This is the only operation that contacts Git and writes latest-check state.

Add:

`GET /api/v1/projects/{projectId}/freshness-checks/latest?sourceId=<uuid>`

This reads the last successful explicit check only. If no result exists for that exact
Project/Source, return `204 No Content`; do not contact Git and do not synthesize `CURRENT`.

The successful response version is `project-freshness-v1` and contains:

* version, check ID, Project ID, checked time;
* Source ID/name/default branch and the explicit resolved request label (`origin/<branch>` or
  `origin/HEAD`);
* complete current revision;
* freshness status and guidance;
* nullable baseline with Analysis ID, completion time, analyzed revision, and provenance status;
* review counts (`total`, `pending`, `accepted`, `rejected`) for the baseline Analysis, or zeroed
  counts when no baseline exists;
* an explicit `asOf`/latest-check meaning in contract documentation.

Do not expose repository credentials, local workspace paths, Git stderr, complete exception text,
or remote URLs in the response.

### Failure API

Add `SOURCE_REVISION_UNAVAILABLE` to the stable API error enum and a focused exception handled as
`503 Service Unavailable`. The message should identify the Source ID and state that the revision
could not be resolved, without leaking command output or credentials. Existing ownership and
validation failures retain their existing 404/400 contracts.

### Persistence policy

Persist exactly one mutable operational row per Source, not an append-only history:

`project_source_freshness`

Columns:

* `id UUID` primary key;
* `project_id UUID NOT NULL`;
* `source_id UUID NOT NULL UNIQUE`;
* nullable `baseline_analysis_id UUID`;
* `status VARCHAR NOT NULL`;
* `guidance VARCHAR NOT NULL`;
* `requested_revision VARCHAR NOT NULL`;
* `current_revision VARCHAR NOT NULL`;
* nullable `baseline_revision VARCHAR`;
* `checked_at TIMESTAMPTZ NOT NULL`.

Use foreign keys with Project/Source cascade deletion consistent with project-owned data. Keep the
baseline Analysis reference nullable/`ON DELETE SET NULL`; the immutable baseline revision remains
an as-of snapshot if its Analysis link disappears. Add a composite uniqueness constraint on
`(project_id, source_id)` even though Source UUID is globally unique, making ownership explicit.

Do not persist failed attempts and do not overwrite the last successful row when Git resolution or
classification orchestration fails. Structured request logs/correlation IDs remain failure audit.

## Planned changes

### 1. Capture pre-implementation evidence

Before modifying production code, record outside Git:

* current branch/worktree state;
* real DevLog Project/Source/Analysis IDs;
* analyzed and current revisions;
* current proposal counts;
* current cockpit copy that falsely implies freshness/monitoring;
* current Engineering Story Context response absence of freshness metadata;
* current Git commands and number of Source synchronizations during one context request where
  observable.

Do not refresh the real DevLog Project and do not mutate its six proposals.

Create a disposable local bare/origin repository and DevLog Project/Source for the later complete
stale→refresh→current validation. Record all disposable IDs so cleanup is deliberate and scoped.

### 2. Add a lightweight current-revision workspace operation

Extend `WorkspaceManager` with a focused method returning an immutable result such as:

```text
ResolvedSourceRevision(sourceId, requestedRevision, resolvedRevision)
```

Implement it in `GitWorkspaceManager` using the same Source validation, workspace confinement,
Source-keyed lock, origin update, fetch/prune, clone/recovery, default-branch resolution, and Git
executor as `synchronize`.

Refactor only enough private code to share:

* lock acquisition;
* workspace initialization/recovery;
* remote URL update and fetch;
* requested-revision derivation;
* full commit resolution.

The freshness operation must not run `clean`, `checkout`, or `reset`. It may update Git's remote
refs because the user explicitly requested a current-revision check. `synchronize` must retain its
existing behavior and tests.

Avoid nested acquisition of the same non-reentrant orchestration path. Both operations should use
one internal `withSourceLock` boundary. Preserve retry-on-corrupt-workspace behavior and reject
inactive/non-Git/unpersisted Sources before external commands.

Add focused filesystem/Git tests proving:

* default branch and `origin/HEAD` resolution;
* remote advancement becomes visible;
* no checkout/worktree content change during current-revision resolution;
* corrupted workspace recovery;
* inactive Source rejection;
* two concurrent operations for one Source serialize and return valid commits;
* different Source locks remain independent where deterministically testable.

### 3. Add deterministic baseline selection

Add one explicit query owned by `ProjectProfileSnapshotRepository` (or a focused freshness
repository) that joins Profile → Analysis and returns the latest comparable profile with:

* exact Project ID;
* exact selected Source ID;
* `AnalysisStatus.COMPLETED`;
* `intentId=describe-project`;
* `intentVersion=v1`;
* `targetRevision IS NULL`;
* order by `completedAt DESC`, `createdAt DESC`, `id DESC`;
* maximum one result.

Use an entity graph/fetch join for Analysis/Source as necessary. Do not load all Project Analyses
and filter in Java.

Extract the baseline revision by the exact Source UUID string from the immutable profile map. If a
diagnostic cross-check can be added with one fixed query, verify equality and classify conflicts as
`UNKNOWN`; do not introduce N+1 loading. The plan prefers the profile as canonical input because it
is the completed Project Understanding snapshot already carried by Project Context.

Validate query semantics on PostgreSQL. The existing `idx_analyses_selected_source` should cover
the small current volume; inspect `EXPLAIN` and add a new index only if measured evidence requires
it.

### 4. Implement the freshness domain and application service

Create a cohesive `projectfreshness` package containing:

* `ProjectFreshnessStatus`;
* `ProjectRefreshGuidance`;
* immutable request/response DTOs;
* `GitCommitIdentity` validator/normalizer;
* pure `ProjectFreshnessClassifier`;
* `ProjectFreshnessService`;
* JPA entity/repository for the latest operational row;
* controller and focused exception.

`check(projectId, sourceId)` will:

1. verify Project existence;
2. load the exact active Project-owned Git Source;
3. resolve its current default revision outside a database transaction;
4. open a short transaction to select the current baseline, validate provenance, count proposals,
   classify, and upsert the latest row;
5. return the saved response.

Do not hold a database transaction open during network/Git work. Recheck Source ownership/active
state inside the persistence transaction before saving so deactivation/deletion during fetch cannot
create an orphaned check.

For concurrent successful checks of one Source, use the database unique constraint as final
defense and a pessimistic lock or conflict-safe retry around the existing latest row. The result is
last-successful-check semantics, not an immutable business event. Never retry Git automatically
beyond the existing one-time corrupt-workspace recovery.

`latest(projectId, sourceId)` verifies ownership and returns the stored row or empty. It performs no
Git command and no status recomputation. The DTO's `checkedAt` makes its as-of nature explicit.

### 5. Add bounded review-state separation

For a selected baseline Analysis, use the existing fixed count methods on
`ValidatableProposalRepository` for total and each status. Return zeros and no Analysis ID for
`NO_BASELINE`; retain the counts for `UNKNOWN` because review progress and revision confidence are
orthogonal.

Do not query or count Insights/Deliverables to determine freshness. Do not create a “fully trusted”
boolean from proposal counts. Angular copy may say “repository revision current; N proposals still
await human review,” but Core returns factual dimensions only.

### 6. Add Flyway migration and deletion compatibility

Add the next immutable Flyway migration after V31 to create `project_source_freshness`, its
constraints, and minimal indexes. Extend the existing fresh PostgreSQL migration and Project
deletion integration tests to prove:

* V1→latest migration succeeds on PostgreSQL 17;
* deleting a Project removes its freshness row;
* deleting/deactivating behavior cannot leave cross-Project data;
* baseline Analysis link behavior matches the selected FK policy.

No existing Analysis, Source, Profile, proposal, or Trusted Knowledge row is rewritten.

### 7. Integrate bounded freshness into Engineering Story Context

Add a compact immutable `ProjectFreshnessSummary` to both full and agent Engineering Story Context
top-level contracts. It should contain at most:

* `version`;
* Project ID;
* one entry per active Source, bounded by a configured/default maximum matching the existing
  practical Source scope;
* Source ID;
* latest stored status/guidance;
* checked time;
* current and baseline revision;
* nullable baseline Analysis ID;
* review counts;
* `checked=false` when no successful explicit result exists.

This provider performs only bounded database reads. It does not contact Git. Mixed Source states
remain separate entries; there is no aggregate `CURRENT`.

Update `EngineeringStoryContextServiceImpl` to load the summary once and pass the same immutable
value to full and agent construction. Do not re-load Project Context. Include freshness in the
canonical projection serialized for byte/token accounting and projection digest; update
`AgentContextProjectionService` canonical records and fit checks accordingly.

Keep `repositoryContext.evidence` and its item contract unchanged so the installed adapter remains
compatible. Measure the additional bytes/tokens against Story 0019's bounds. Add contract tests for
checked/unchecked, stale/current/unknown, multi-Source bounding, canonical digest change, and exact
adapter extraction.

### 8. Add Angular freshness models and HTTP service

Create focused typed models for the versioned response and a `ProjectFreshnessService` with:

* `getLatest(projectId, sourceId)` handling `204` as no stored result;
* `check(projectId, sourceId)` issuing the exact POST body;
* existing centralized request-error propagation.

Add HTTP tests for encoded IDs, exact URLs/methods/body, `204`, all state payloads, 503 propagation,
and unchanged Project Understanding requests.

### 9. Implement a focused Project freshness component

Add a standalone child component to the Project cockpit with Project ID, compatible Sources, and
Analyses as inputs. It will own:

* deliberate Source selection (auto-select only when exactly one compatible active Git Source);
* loading the stored latest result after Source selection without checking Git;
* explicit `Check freshness` action using `exhaustMap`;
* states: unchecked/loading/checking/current/stale/no-baseline/unknown/check-failed;
* retention of the last successful result when a recheck fails;
* textual state, revisions, timestamps, baseline Analysis link, and proposal-review counts;
* a `Refresh understanding` affordance that focuses/preselects the existing
  `ProjectUnderstandingSection` rather than submitting it;
* retry action after failure.

No status is recomputed client-side. The component maps Core enums to copy and visual treatment.
Use full SHAs in accessible detail and abbreviated SHAs only as visible presentation with title or
associated full text.

Coordinate Source selection with `ProjectUnderstandingSection` through a parent-owned selected
Source input/output or a minimal explicit event. Do not add a global state library or persist form
state in browser storage.

### 10. Correct misleading cockpit projections

Replace:

* Project `updatedAt` masquerading as “Last synchronized” with selected Source
  `lastSynchronizedAt` or an honest unavailable value;
* the presence-based “Workspace is up to date” condition with factual independent next steps;
* “monitoring configured sources” with “ready for an explicit check/analysis” language;
* generic “Last analysis” where appropriate with the latest comparable Project Understanding.

Pending work should independently recommend:

* connect Source when absent;
* establish/refresh understanding based on freshness result;
* review pending proposals based on proposal counts;
* generate documentation only from validated knowledge where applicable.

Do not claim passive monitoring, full knowledge validation, or current repository state without the
Core projection.

### 11. Accessibility and responsive behavior

Use a semantic fieldset/form, explicit labels, normal buttons, `aria-live`/`role=status` for check
progress and result changes, and `role=alert` for failures. Focus the result heading after a
completed check and the Project Understanding heading after the refresh affordance. Prevent
duplicate in-flight checks without keyboard shortcuts or implicit actions.

At narrow widths, stack Source selection, status, revisions, review counts, and actions. Never rely
on color alone; render enum meaning in text.

### 12. Focused and complete tests

Backend unit/WebMvc tests will cover:

* complete classifier matrix and SHA normalization;
* exact Source ownership/type/activity;
* all baseline/provenance cases;
* review counts by baseline;
* no transaction across Git call (verified structurally/service separation);
* successful upsert and last-result reads;
* failed check preserving the previous row;
* concurrent same-Source checks;
* stable 200/204/400/404/503 contracts;
* Engineering Story Context accounting/digest and adapter compatibility;
* existing Project Understanding and workspace synchronization compatibility.

PostgreSQL tests will cover migration, latest-baseline ordering/filtering, uniqueness/upsert, and
project deletion.

Angular tests will cover all interaction states, Source selection, no implicit check on component
creation, duplicate suppression, stale/current/no-baseline/unknown copy, retained result after
failure, review-state separation, refresh focus/preselection without execution, truthful cockpit
copy, links, live regions, and narrow semantic structure.

Run complete backend and frontend suites after focused tests.

### 13. Documentation reconciliation

Update only affected canonical documentation:

* `README.md` — freshness APIs, explicit workflow, error and trust semantics;
* `docs/architecture.md` — operational latest-check projection, commit authority, and separation
  from ADR-041 passive monitoring;
* `docs/ui-ux.md` — cockpit states, explicit check, review-state separation, failure behavior;
* `docs/roadmap.md` — mark explicit deterministic freshness guidance implemented while retaining
  passive/incremental monitoring as future work;
* relevant package READMEs/manual MVP guide when they describe the changed flow.

Do not create a new ADR unless implementation requires a material departure from this approved
ephemeral/latest-operational projection boundary. Record the exact documentation outcome in the
Implementation Report before Code Review.

### 14. Quality and live validation

Run and record:

* focused Maven tests;
* complete `./mvnw clean verify` with JaCoCo;
* focused and complete Angular tests;
* adapter tests;
* Angular formatting check/write and production build;
* authenticated `sonar:sonar -Dsonar.qualitygate.wait=true` using the ignored environment token;
* Quality Gate project key/status, new bugs, vulnerabilities, hotspots, smells, new-code coverage,
  and duplication;
* Docker backend/frontend rebuild and health;
* exact API contracts and Angular deep link;
* `git diff --check`, status, and unrelated-change review.

Live workflow:

1. read-check real DevLog and demonstrate `STALE` for `b2f2c888…` versus the current commit without
   launching refresh or deciding proposals;
2. on the disposable Project, establish a baseline through the existing explicit understanding
   action;
3. check and demonstrate `CURRENT`;
4. add/push one disposable repository commit;
5. check and demonstrate `STALE` while baseline data remains intact;
6. explicitly refresh through the existing action;
7. check again and demonstrate `CURRENT`;
8. verify no automatic Validation/Insight/Deliverable was created;
9. verify compact Engineering Story Context transports the latest as-of summary within bounds.

Do not refresh or decide the real DevLog proposals without separate human authorization.

## Expected file areas

Backend likely changes:

* `collection/workspace` interface, implementation, result, and tests;
* new `projectfreshness` package and tests;
* Analysis/Profile/proposal repository queries;
* shared API error enum/handler;
* Project Context and agent projection contracts/services/tests;
* Flyway V32 and PostgreSQL integration tests;
* application properties only if a small Source-summary bound is configurable.

Frontend likely changes:

* Project detail page/template/SCSS/spec;
* Project Understanding input/focus coordination and tests;
* new freshness models/service/component/specs;
* centralized request-error tests only if the new 503 code needs explicit mapping.

Documentation changes:

* Story artifacts;
* `README.md`;
* `docs/architecture.md`;
* `docs/ui-ux.md`;
* `docs/roadmap.md`;
* manual/package documentation only when directly affected.

## Explicit non-goals during implementation

Implementation must not:

* add scheduled/background checks, webhook endpoints, AgentJobs, or notifications;
* launch Project Understanding from the freshness service or component;
* inspect local uncommitted working-tree changes;
* count commits or classify their significance;
* infer freshness from time elapsed;
* accept/reject proposals or create Trusted Knowledge;
* preserve an unbounded history of checks;
* introduce a second Git command owner or external monitoring dependency;
* fix the unrelated unbounded recent-Analyses load unless separately approved;
* commit, push, or merge automatically.

## Plan risks and fallback rules

* If lightweight resolution cannot safely share `GitWorkspaceManager`'s Source lock, stop and
  return to the human; do not add an independent Git path.
* If the baseline query cannot distinguish default and historical Project Understanding from
  persisted fields, classify as `UNKNOWN`/`NO_BASELINE`; do not infer from branch names.
* If latest-check persistence creates a race not solved by the unique constraint plus short
  transactional upsert, use a pessimistic Source/row lock; do not serialize network work inside a
  database transaction.
* If freshness metadata threatens compact-context bounds, retain checked/status/time/Source and
  remove optional review counts/revision display from the agent projection before removing
  repository evidence.
* If Sonar reports Story-local findings, correct them and rerun. Findings outside scope require
  human authorization.

## Completion conditions

Implementation is complete only when:

* every acceptance criterion is traced to implementation and tests;
* only equal complete commit identities yield `CURRENT`;
* no implicit Git check or Analysis launch occurs on Project open;
* stale/no-baseline guidance reaches the existing manual refresh action;
* proposal review state remains independent and human-controlled;
* Engineering Story Context remains bounded and adapter-compatible;
* all focused/complete tests, build, JaCoCo, Docker/live checks, and authenticated SonarQube
  Quality Gate pass;
* canonical documentation is reconciled;
* Implementation Report and Code Review are produced;
* Engineering Story stops at Gate 3 for explicit human approval.
