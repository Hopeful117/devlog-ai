# Story 0054 — Detect Stale Understanding And Projection Refresh Gaps — Implementation Plan

## Overview

Implement Story `0054` as the **first deterministic maintenance-evaluation
slice** on top of the findings domain introduced by Story `0052` and exposed by
Story `0053`.

The goal is to create explicit maintenance findings for:

* stale project understanding based on authoritative freshness evidence;
* one bounded projection-refresh gap on a project-facing surface;
* explainable, low-noise operational issues rather than hidden internal flags.

This Story should stay intentionally narrow.

It should not add:

* scheduler orchestration;
* semantic AI maintenance reasoning;
* trusted-knowledge mutation;
* broad cross-surface health scoring.

## Final Implementation Strategy

The preferred implementation is:

1. add a dedicated backend maintenance-evaluation service for project freshness
   and projection-gap rules;
2. derive stale-understanding findings from existing persisted
   `projectfreshness` results instead of creating a second classifier;
3. implement one bounded projection-gap rule against the timeline surface using
   observable recent activity and timeline-visible evidence;
4. create findings through the existing `MaintenanceFindingService`;
5. add idempotency guards so repeated evaluation does not create identical open
   findings on every run;
6. expose a bounded trigger path for the evaluation flow;
7. add service and API tests plus documentation updates describing the first
   deterministic policy and its limits.

## Step 1 — Introduce a dedicated maintenance evaluation backend seam

Targets:

* new backend package under
  `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/`

Goals:

* keep detection logic separate from finding persistence;
* keep freshness/projection rules explicit and testable;
* create a reusable seam for future maintenance Stories.

Implementation direction:

Add a dedicated service layer responsible for:

* loading the bounded project signals needed for evaluation;
* executing deterministic stale-understanding and projection-gap rules;
* creating findings through `MaintenanceFindingService`;
* returning a structured evaluation result suitable for tests and eventual API
  triggering.

Preferred design:

* keep the service orchestration in `contextmaintenance`;
* reuse supporting repositories/services from `projectfreshness`, `timeline`,
  and the source event domains rather than duplicating their logic.

Rationale:

The repository already has a finding store but no producer. Story `0054`
should add the producer without polluting existing persistence services.

## Step 2 — Reuse `projectfreshness` as the authoritative stale-understanding signal

Targets:

* `ProjectFreshnessService`
* `ProjectFreshnessSummary`
* new maintenance evaluation rule classes or helpers

Goals:

* satisfy AC-1 without duplicating freshness policy;
* keep stale-understanding detection deterministic and explainable;
* align maintenance findings with signals already visible to agents.

Implementation direction:

Base the first stale-understanding rule on persisted freshness outcomes already
produced by `projectfreshness`.

Recommended first-slice rule:

* create a `STALE_PROJECT_UNDERSTANDING` finding when at least one active
  checked source is classified `STALE`.

Optional bounded extensions, only if they remain easy to explain:

* treat `NO_BASELINE` as a missing-understanding freshness issue rather than a
  stale one;
* treat `UNKNOWN` as an investigation-oriented issue only if the Story can keep
  the wording and issue mapping unambiguous.

Important rule:

* do not reimplement commit-diff or baseline comparison logic in
  `contextmaintenance`;
* derive the finding from the existing persisted freshness result and explain
  that derivation in the finding details.

Rationale:

`ProjectFreshnessClassifier` already owns the repository’s freshness semantics.
Story `0054` should consume that policy, not fork it.

## Step 3 — Define one bounded projection-refresh gap rule on timeline

Targets:

* `timeline` projection behavior
* source repositories already used by `TimelineProjectionServiceImpl`
* new maintenance rule classes or helpers

Goals:

* satisfy AC-2 with a real project-facing projection surface;
* avoid pretending that DevLog has a persisted projection refresh timestamp when
  it does not;
* keep false positives low and the explanation concrete.

Implementation direction:

Use timeline as the first projection surface because it has:

* explicit eligible source inputs;
* deterministic ordering;
* a bounded entry model already exposed to users.

Recommended rule shape:

* detect a `MISSING_PROJECTION_REFRESH` or `PROJECTION_REFRESH_GAP` when recent
  eligible source activity exists but the timeline exposes no corresponding
  recent projection evidence.

Concrete bounded direction:

* compare bounded recent source activity from the same families used by
  `TimelineProjectionServiceImpl`;
* compare that activity to the bounded timeline projection result;
* require meaningful recent activity before raising a finding so unchanged
  projects do not look stale.

Important rule:

* frame the issue as a gap between observable source activity and projection
  evidence, not as a fictitious “last refresh timestamp”.

Rationale:

Timeline is narrower and easier to explain than `projectstate`, while still
being a visible projection surface named by ADR-053 and the Story text.

## Step 4 — Add explicit finding explanation and low-noise policy

Targets:

* finding summary/details generation
* maintenance evaluation result DTOs, if introduced

Goals:

* satisfy AC-3 and AC-4;
* keep findings actionable for humans and future agents;
* distinguish stale context from merely unchanged context.

Implementation direction:

Each created finding should explain:

* which deterministic rule triggered;
* which source or freshness records were considered;
* why the project is considered stale or lagging;
* what action is suggested next.

Recommended wording strategy:

* stale understanding findings should reference the checked source and its
  freshness status/guidance;
* projection-gap findings should reference the observed recent source activity
  and the absence or insufficiency of timeline-visible evidence.

Noise-control rule:

* do not create projection findings when the project simply has no recent
  eligible activity;
* do not create stale-understanding findings for `CURRENT` sources.

Rationale:

The Story’s value is operational trust. That requires concrete reasons, not
generic stale badges.

## Step 5 — Prevent duplicate open findings during repeated evaluation

Targets:

* maintenance evaluation orchestration
* `MaintenanceFindingRepository` and/or service usage

Goals:

* avoid repeated identical findings on every evaluation run;
* keep the first producer operationally safe;
* preserve the existing manual status workflow from Story `0053`.

Implementation direction:

Before creating a new finding, check whether an equivalent open finding already
exists for the same:

* project;
* context surface;
* issue type;
* bounded rule identity.

Preferred first-slice behavior:

* skip duplicate creation when an equivalent `OPEN` finding already exists;
* allow a new finding only when the previous one has been resolved or dismissed
  and the condition reappears.

Rationale:

Without an idempotency guard, the detector would quickly flood the new
maintenance UI with repeated copies of the same issue.

## Step 6 — Expose a bounded evaluation trigger

Targets:

* `contextmaintenance` controller layer
* or an adjacent existing project-scoped backend controller if that proves more
  natural

Goals:

* make the new detector executable in a controlled way;
* provide a stable path for tests and future manual operation;
* keep the initial API narrow.

Implementation direction:

Add one explicit trigger endpoint for maintenance evaluation, scoped by project.

Recommended shape:

* `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations`

Recommended response:

* a small evaluation summary describing how many findings were created or
  skipped, plus the resulting active findings when useful for callers.

Important non-goal:

* do not introduce background scheduling or bulk cross-project execution in this
  Story.

Rationale:

The repository already exposes freshness checks and findings reads separately.
A dedicated evaluation trigger keeps the maintenance producer explicit and
testable.

## Step 7 — Add focused backend tests for rule behavior and no-finding paths

Targets:

* new maintenance evaluation service tests
* new WebMvc tests for the trigger endpoint

Goals:

* satisfy AC-5;
* prove deterministic rule behavior;
* protect against regressions in no-activity and unchanged-project scenarios.

Implementation direction:

Add service tests covering at least:

* stale-understanding finding creation when latest freshness is `STALE`;
* no stale-understanding finding when latest freshness is `CURRENT`;
* projection-gap finding creation when bounded recent source activity exists but
  timeline-visible evidence does not;
* no projection-gap finding when recent activity is absent;
* duplicate-open-finding suppression on repeated evaluation.

Add WebMvc tests covering at least:

* successful project-scoped evaluation trigger;
* error behavior for unknown project ids;
* stable response serialization for the evaluation result.

Preferred style:

* mirror the repository’s existing deterministic service and controller testing
  patterns rather than introducing large integration fixtures.

## Step 8 — Update canonical documentation for the first staleness policy

Targets:

* relevant canonical documentation such as `README.md`, `docs/ui-ux.md`, or
  another already-authoritative maintenance/freshness doc depending on the final
  endpoint and user-facing behavior

Goals:

* satisfy AC-6;
* document what the first maintenance detector does and does not claim;
* keep future Stories anchored to an explicit initial policy.

Implementation direction:

Document:

* that stale-understanding detection currently derives from explicit
  `projectfreshness` results;
* which projection surface is evaluated first;
* that projection-gap detection is bounded and does not represent a universal
  “context health” score;
* that no trusted knowledge is mutated by this Story.

Rationale:

The repository has already documented freshness semantics and maintenance
findings separately. Story `0054` should connect those concepts with explicit
limits.

## Expected Implementation Shape

Repository evidence supports an implementation centered on:

* a new maintenance evaluation service in `contextmaintenance`;
* reuse of existing `projectfreshness` and timeline/source repositories;
* one explicit trigger endpoint;
* finding creation through the existing maintenance service;
* documentation and tests that make the first policy transparent.

## Validation Plan

Before requesting Code Review approval, validate with:

* targeted backend tests for evaluation service behavior;
* WebMvc tests for the new trigger endpoint;
* a full backend test run for the affected modules if execution time remains
  reasonable;
* manual endpoint verification if needed to confirm the end-to-end finding
  creation flow.

## Deferred Work

Explicitly defer to later Stories:

* broader cross-surface maintenance evaluation;
* semantic duplicate or overlap reasoning;
* automated remediation workflows;
* scheduling, polling, or background maintenance execution;
* richer UI mutation flows for resolving or dismissing findings.
