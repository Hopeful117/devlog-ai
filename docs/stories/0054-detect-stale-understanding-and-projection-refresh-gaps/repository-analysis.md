# Story 0054 — Detect Stale Understanding And Projection Refresh Gaps — Repository Analysis

## Purpose

Analyze how DevLog should produce the first deterministic maintenance findings
for context freshness without:

* duplicating the existing `projectfreshness` model;
* inventing an opaque global “context health” score;
* claiming projection staleness for surfaces that are only computed on demand;
* blurring the boundary between deterministic maintenance signals and broader AI
  reasoning.

This Story is the first **finding producer** slice on top of the maintenance
domain introduced by Story `0052` and exposed by Story `0053`.

It is not:

* a remediation workflow Story;
* a scheduler Story;
* a broad autonomous maintenance Story;
* a full cross-surface maintenance engine.

## Repository Context

### Current Git state

Repository branch at analysis time:

* `main`

Observed local nuance:

* the merged Story `0053` implementation is already present on local `main`;
* story directories `0054` through `0059` remain local untracked planning
  inputs;
* no implementation artifact exists yet for Story `0054` besides `story.md`.

Impact:

* the repository is ready for a first maintenance-detector slice;
* Story `0054` can plan directly against the persisted `contextmaintenance`
  domain and the existing frontend read surface from `0053`.

### DevLog lifecycle

Story registration succeeded:

* DevLog story id: `f5309925-2d38-4761-b769-a910700da937`

DevLog engineering-story context preparation fell back cleanly:

* `DEVLOG_CONTEXT_ERROR: DevLog RepositoryContext contains no usable evidence. Repository Analysis continues without DevLog.`

Impact:

* this analysis proceeds from direct repository inspection;
* DevLog context unavailability is not a blocker for Story `0054`;
* the Story should continue to keep its rules deterministic and explainable even
  when richer repository evidence is unavailable.

### Vault context

Vault was not consulted for this analysis.

Reason:

* the relevant architectural constraints and implementation seams already exist
  in the repository;
* the Story is a local bounded-detector design problem rather than a
  cross-project research question.

## Story Understanding

Story `0054` asks DevLog to produce maintenance findings for two related but
distinct problems:

* project understanding that is stale relative to known repository freshness;
* at least one projection surface whose refresh is missing or outdated.

The Story text strongly constrains the solution:

* rules should be deterministic;
* explanations should be explicit;
* output should be maintenance findings rather than hidden flags;
* policy should be bounded and documented.

This means the Story should primarily assemble existing repository signals into a
maintenance-evaluation flow, not invent a new inference-heavy subsystem.

## Business Ownership

The affected capability remains **internal context maintenance**, with backend
ownership in Java Core.

Repository evidence indicates the owning layers are:

* Java Core for detection rules, finding creation, and explanation payloads;
* existing `contextmaintenance` API for persistence and read-back;
* existing Angular cockpit/workspace surface from Story `0053` for visibility.

The AI Engine is not the natural owner of this Story because:

* ADR-053 explicitly prefers deterministic evaluation where practical;
* the needed signals already exist in backend-owned freshness and projection
  packages;
* the Story acceptance criteria emphasize explainability and bounded policy.

## Relevant Existing Architecture

### `docs/decisions/ADR-053.md`

This ADR is the primary architectural source for the Story.

It establishes that:

* context maintenance is a distinct capability;
* deterministic evaluation should be the primary layer;
* maintenance findings must be first-class and reviewable;
* the capability spans multiple context surfaces including project
  understanding and timeline projections;
* human authority remains explicit for ambiguous or destructive actions.

Important implication:

Story `0054` should produce findings that are operational and explainable, not
semantic project truths.

### `contextmaintenance/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingService.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceFindingIssueType.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceContextSurface.java`

Current behavior:

* findings can already be created, listed, and status-updated;
* the model already contains the issue types needed by this Story:
  * `STALE_PROJECT_UNDERSTANDING`
  * `PROJECTION_REFRESH_GAP`
  * `MISSING_PROJECTION_REFRESH`
* the model already distinguishes affected surfaces:
  * `PROJECT_UNDERSTANDING`
  * `PROJECT_PROJECTION`

Important implication:

Story `0054` does not need new finding primitives to start. It needs a bounded
producer/orchestrator that maps deterministic signals into the existing
maintenance-finding domain.

### `projectfreshness/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/projectfreshness/ProjectFreshnessService.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectfreshness/ProjectFreshnessPersistenceService.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectfreshness/ProjectFreshnessClassifier.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectfreshness/ProjectFreshnessSummary.java`

Current behavior:

* DevLog already supports explicit freshness checks per active Git source;
* persisted checks already classify sources into deterministic states:
  * `CURRENT`
  * `STALE`
  * `NO_BASELINE`
  * `UNKNOWN`
* guidance is already explicit:
  * `REFRESH_NOT_NEEDED`
  * `REFRESH_RECOMMENDED`
  * `ESTABLISH_BASELINE`
  * `VERIFY_BASELINE`
* `ProjectFreshnessService.summary(projectId)` already aggregates latest checks
  for active sources.

Important implication:

`projectfreshness` is the strongest existing basis for stale project
understanding detection. Reimplementing similar rules inside
`contextmaintenance` would create duplicate policy and drift risk.

### `projectcontext/EngineeringStoryContextServiceImpl`

Relevant file:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceImpl.java`

Current behavior:

* engineering-story context already includes `freshnessService.summary(projectId)`
  alongside snapshot and repository context;
* freshness is therefore already treated as part of the operational context
  consumed by future agents.

Important implication:

the repository already acknowledges that “current understanding” depends in part
on freshness metadata. Story `0054` should formalize that relationship into
maintenance findings rather than create a parallel interpretation path.

### `projectstate/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/projectstate/service/ProjectStateProjectionServiceImpl.java`

Current behavior:

* project state is built on demand from multiple repositories;
* no persisted refresh timestamp or last-generated baseline is stored for the
  projection itself;
* the service is a live projection, not a cached artifact with a refresh
  lifecycle.

Important implication:

`projectstate` is a weak candidate for “projection refresh gap” detection in
this Story because there is no explicit projection-refresh state to compare.

### `timeline/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/timeline/service/TimelineProjectionServiceImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/timeline/service/TimelineProjectionServiceTest.java`

Current behavior:

* timeline is also generated on demand;
* it aggregates recent completed stories, engineering events, knowledge events,
  decisions, and completed milestones;
* entries are fetched with bounded per-source and global limits and then sorted
  deterministically.

Important nuance:

timeline is not persisted as a refreshed artifact either. However, unlike the
broader `projectstate`, it is a narrow cross-source projection with explicit,
bounded source inputs and stable ordering semantics.

Architectural implication:

if Story `0054` needs one initial projection-surface signal, timeline is the
most credible candidate, but the rule should be framed as a **gap between
available recent source activity and timeline-visible projection evidence**, not
as a fictional “last refresh timestamp” that does not exist today.

## Architectural Constraints

### Do not duplicate `projectfreshness` policy

The repository already has one authoritative freshness classifier.

Therefore Story `0054` should avoid:

* redefining staleness thresholds in a second package;
* storing parallel freshness states inside maintenance findings;
* claiming project understanding is stale for reasons unrelated to existing
  explicit freshness evidence.

Preferred approach:

derive stale-understanding findings from persisted freshness outcomes and expose
that derivation clearly in finding details.

### Do not infer refresh gaps for purely live projections without evidence

Because `projectstate` and `timeline` are built on demand, Story `0054` should
not pretend there is a background refresh job or persisted generation timestamp
unless the implementation introduces one explicitly.

Preferred approach:

keep the first projection-gap rule bounded to observable repository facts such
as:

* recent project activity exists in source repositories;
* the relevant projection exposes no corresponding recent entries;
* or no refresh evidence exists for the chosen bounded surface.

### Findings must stay explainable and reviewable

The `contextmaintenance` model and ADR-053 both push toward explicit
explanation.

Therefore the producer should likely emit details covering:

* which deterministic rule triggered;
* which source records or freshness states were considered;
* why the issue type is `STALE_PROJECT_UNDERSTANDING`,
  `PROJECTION_REFRESH_GAP`, or `MISSING_PROJECTION_REFRESH`;
* what next action is suggested.

## Likely Design Direction

### 1. Add a dedicated maintenance evaluation service

The repository currently has the finding store, but not the detector.

The cleanest seam appears to be a new backend service that:

* loads bounded freshness and projection inputs for a project;
* evaluates deterministic rules;
* creates maintenance findings through `MaintenanceFindingService`.

This keeps detection separate from:

* storage concerns in `MaintenanceFindingServiceImpl`;
* live projection concerns in `projectstate` and `timeline`;
* frontend presentation.

### 2. Treat stale understanding as a projection of freshness, not a second classifier

The first stale-understanding rule should likely be based on persisted
freshness-state evidence such as:

* at least one active source has latest status `STALE`;
* or a source has no baseline / unverifiable baseline according to existing
  policy, if the Story wants to treat those as maintenance-worthy.

This would align AC-1 and AC-4 with existing semantics while avoiding policy
duplication.

### 3. Start with one bounded projection surface, most likely timeline

`projectstate` lacks an explicit refresh lifecycle.

`timeline` is still live, but narrower and easier to reason about. A first rule
can remain deterministic if it compares bounded source activity against bounded
projection visibility.

Examples of acceptable first-slice framing:

* a recent engineering/knowledge/decision/milestone/story signal exists but the
  timeline projection exposes no recent corresponding entry;
* a project has meaningful recent activity yet the timeline remains empty;
* the chosen bounded projection lacks evidence despite recent eligible source
  data.

The exact rule should stay narrow and document its limits clearly.

## Testing Implications

The repository already favors deterministic service tests plus WebMvc coverage
for exposed endpoints.

Story `0054` should likely add:

* service tests for stale-understanding rule outcomes;
* service tests for the no-finding path;
* service tests for missing/outdated projection-gap outcomes on the chosen
  surface;
* controller tests only if the Story adds a new trigger endpoint rather than
  piggybacking on an existing flow.

The existing `TimelineProjectionServiceTest` and
`ProjectFreshnessControllerWebMvcTest` already show the expected repository
style: bounded fixtures, explicit rule assertions, and deterministic ordering.

## Risks And Open Questions

### Risk: duplicate finding churn

If evaluation is rerun repeatedly, the repository currently has no obvious
deduplication or “open finding already exists” guard in
`MaintenanceFindingServiceImpl`.

Impact:

* naive producer logic could create repeated identical findings on every run.

This should be resolved in implementation planning, either through bounded
idempotency rules or explicit existing-open-finding checks.

### Risk: overreaching on projection semantics

Because no persisted projection-refresh artifact exists yet, the first rule can
easily become hand-wavy.

Impact:

* weak rules would undermine trust in maintenance findings.

Implementation should therefore prefer a narrow timeline-based or similarly
observable rule over a broad “project projection is stale” heuristic.

### Open question: trigger model

The repository currently shows:

* a freshness check endpoint;
* a maintenance findings read endpoint.

It does not yet show an obvious maintenance-evaluation trigger.

The next phase must decide whether Story `0054` should:

* add a dedicated evaluation endpoint;
* piggyback on an existing maintenance/freshness workflow;
* or run inside an internal service path used elsewhere.

## Recommended Planning Direction

Repository evidence supports the following plan for Story `0054`:

* reuse `projectfreshness` as the authoritative stale-understanding signal;
* introduce a bounded maintenance-evaluation service that produces findings via
  the existing `contextmaintenance` domain;
* select one narrow projection surface, most plausibly timeline, for the first
  refresh-gap rule;
* document explicit limits so DevLog does not overclaim projection staleness on
  live computed views.

That direction is consistent with ADR-053, minimizes duplicated policy, and
keeps the first maintenance-detector slice deterministic and explainable.
