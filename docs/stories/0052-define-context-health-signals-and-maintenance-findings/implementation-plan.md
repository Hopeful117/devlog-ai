# Story 0052 — Define Context Health Signals And Maintenance Findings — Implementation Plan

## Overview

Implement Story `0052` as a **backend-first domain foundation** for context
maintenance.

The goal is not to deliver full maintenance behavior yet.

The goal is to establish a first-class persisted model that later Stories can
reuse for:

* visibility (`0053`);
* freshness / projection-gap detection (`0054`);
* trusted-knowledge duplicate-debt findings (`0055`);
* human-context maintenance (`0057`).

The implementation should therefore:

* introduce a dedicated maintenance-finding domain in the Java Core;
* keep findings separate from trusted knowledge, proposal history, and human
  context entities;
* define a narrow initial taxonomy centered on the first high-value surfaces;
* provide enough lifecycle and action semantics for later producers and readers
  without encoding later remediation workflows too early.

## Final Implementation Strategy

The preferred implementation is:

1. create a dedicated `contextmaintenance` backend package;
2. add a persisted `MaintenanceFinding` entity with bounded enum-based
   classification;
3. introduce repository and service behavior for project-scoped creation and
   retrieval;
4. keep the first surface set narrow:
   * `PROJECT_UNDERSTANDING`
   * `PROJECT_PROJECTION`
5. add focused tests for persistence, lifecycle semantics, and project scoping;
6. update canonical documentation so the new domain is explicit.

This Story should stop at durable model readiness.

It should not yet implement:

* detector jobs or rules;
* cockpit rendering;
* remediation workflows;
* automatic mutation of project memory.

## Step 1 — Introduce a dedicated backend package for context maintenance

Targets:

* new package under
  `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/`
* matching test package under
  `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/`

Goals:

* give maintenance findings a clear architectural home;
* avoid embedding the model inside `insight`, `validation`,
  `projectcontextinput`, or projection packages;
* preserve future extensibility for later detector and remediation Stories.

Implementation direction:

* define a dedicated package structure around:
  * entity
  * enums
  * repository
  * service
  * optionally DTO / mapper only if needed by this Story
* follow the repository’s recent package-by-capability style.

Rationale:

This keeps ownership explicit and avoids blurring maintenance with any existing
knowledge lifecycle.

## Step 2 — Add a first-class persisted `MaintenanceFinding` entity

Targets:

* new entity file under `contextmaintenance/entity/`
* Flyway migration under `backend/src/main/resources/db/migration/`

Goals:

* satisfy AC-1 by persisting a first-class maintenance-finding model in Core;
* establish a stable record shape for future detectors and readers;
* preserve explicit project scoping and audit timestamps.

Implementation direction:

Create an entity with fields along these lines:

* `id`
* `project`
* `contextSurface`
* `issueType`
* `severity`
* `status`
* `suggestedAction`
* `humanReviewRequired`
* `summary`
* `details`
* `createdAt`
* `updatedAt`

Recommended initial modeling choices:

* `project` as required `@ManyToOne`
* string-backed enums via `@Enumerated(EnumType.STRING)`
* `summary` as short required text
* `details` as optional bounded explanatory text or `TEXT`
* auditing timestamps consistent with patterns already used in
  `ProjectHumanContextInput`

Important non-goals:

* do not add remediation actor fields yet;
* do not add approval / validation linkage;
* do not encode detector-specific payloads too early unless a small generic
  metadata field becomes clearly necessary.

## Step 3 — Define a narrow first-slice taxonomy

Targets:

* enum files under `contextmaintenance/entity/` or adjacent package

Goals:

* satisfy AC-2 and AC-3 with a bounded but reusable classification model;
* keep the taxonomy narrow enough to avoid speculative overreach;
* preserve clean paths for future Stories.

Implementation direction:

Introduce the minimal enum set needed by the Story:

* `ContextSurface`
  * `PROJECT_UNDERSTANDING`
  * `PROJECT_PROJECTION`
* `MaintenanceFindingSeverity`
  * small bounded set such as `LOW`, `MEDIUM`, `HIGH`
* `MaintenanceFindingStatus`
  * bounded review-oriented states such as:
    * `OPEN`
    * `RESOLVED`
    * `DISMISSED`
* `SuggestedActionCategory`
  * coarse categories such as:
    * `MONITOR`
    * `REFRESH`
    * `INVESTIGATE`
    * `REVIEW`
* `IssueType`
  * narrow initial issue vocabulary aligned with the first planned producers,
    for example:
    * stale understanding
    * projection refresh gap
    * missing projection refresh

Recommended rule:

* keep `IssueType` broad enough to support `0054`;
* do not encode trusted-knowledge duplicate debt or human-context hygiene in
  the first shipped issue set unless implementation proves they are required by
  the accepted Story.

Rationale:

The taxonomy should model the first real slice, not the entire future of
maintenance.

## Step 4 — Add repository and minimal service behavior

Targets:

* `contextmaintenance/repository/`
* `contextmaintenance/service/`

Goals:

* make findings usable by later detector and API Stories;
* provide a clean project-scoped write/read boundary;
* keep the first service intentionally small.

Implementation direction:

Repository methods should at least support:

* project-scoped retrieval with stable ordering
* filtering by status when useful
* persistence of new findings

Service behavior should at least support:

* creating a new maintenance finding from structured input
* listing project findings in a stable deterministic order
* optionally updating lifecycle status if that is needed to prove basic
  lifecycle behavior in tests

Preferred ordering:

* newest first by `createdAt`, then stable ID

Important non-goals:

* no controller or public API is required unless implementation ergonomics make
  a tiny internal contract worthwhile;
* do not add detector orchestration yet.

Rationale:

A minimal service boundary is enough to support AC-5 and prepares the model for
later producer / reader Stories.

## Step 5 — Add database migration for the first findings table

Targets:

* new Flyway migration after the current latest migration

Goals:

* create durable storage for maintenance findings;
* keep schema explicit and auditable;
* preserve backward compatibility with existing domains.

Implementation direction:

Migration should create a table with:

* UUID primary key
* required `project_id` foreign key
* required enum-backed string columns for:
  * surface
  * issue type
  * severity
  * status
  * suggested action
* required boolean for human-review requirement
* required summary
* optional details
* required timestamps

Recommended schema constraints:

* foreign key to `projects`
* non-null constraints on required classification fields
* practical varchar lengths for enum-backed columns

Important non-goal:

* no polymorphic association to findings’ target records yet.

Rationale:

The first slice should persist the maintenance concept cleanly before adding
deeper cross-entity references.

## Step 6 — Keep API surface internal in this Story unless a tiny read path is unavoidable

Targets:

* likely none in `controller/` for this Story

Goals:

* preserve the dependency boundary with Story `0053`;
* avoid partially implementing the visibility Story inside the foundation Story.

Implementation direction:

Preferred plan:

* no public REST endpoint in Story `0052`

Acceptable exception:

* if a tiny internal or test-facing DTO improves service tests or future
  integration readiness without effectively delivering Story `0053`, keep it
  narrowly scoped and do not present it as the public maintenance API.

Rationale:

Story `0053` is explicitly the API/cockpit visibility slice, so `0052` should
not quietly absorb that work.

## Step 7 — Reconcile canonical documentation

Targets:

* `docs/knowledge-model.md`
* possibly `docs/architecture.md`
* optionally a small package README if that matches repository conventions

Goals:

* satisfy AC-6 by documenting the first-slice maintenance boundaries;
* record that findings are first-class but not trusted knowledge;
* describe the intentionally narrow first surface coverage.

Implementation direction:

Update only the relevant sections to explain:

* maintenance findings are internal reviewable context-health records;
* they remain distinct from trusted knowledge, proposal history, and
  human-authored context;
* the first slice covers only bounded project-understanding /
  project-projection concerns.

Avoid:

* broad ADR rewrites;
* future workflow promises not yet implemented.

## Step 8 — Validate with focused backend tests

Targets:

* new service tests
* new persistence/repository tests if justified
* possible controller tests only if a controller is introduced

Goals:

* satisfy AC-5 with meaningful backend coverage;
* prove lifecycle semantics without requiring future detector logic;
* guard the separation from other knowledge models.

Recommended test cases:

* creating a maintenance finding persists all required classifications
* findings are scoped to one project and never leak across projects
* stable ordering is deterministic
* lifecycle status transitions allowed by the service behave as intended
* enum-backed persistence round-trips correctly
* a finding can be marked human-review-required without affecting trusted
  knowledge or validation state

Validation commands:

* `./mvnw test`
* targeted backend tests for the new maintenance package if needed during
  iteration

## Expected File Impact

Likely new production files:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceFinding.java`
* enum files for surface, issue type, severity, status, suggested action
* repository and service files under `contextmaintenance/`
* Flyway migration for the new table

Likely updated documentation:

* `docs/knowledge-model.md`
* possibly `docs/architecture.md`

Likely new tests:

* service tests under `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/`
* persistence-oriented tests if repository behavior needs direct coverage

## Risks And Controls

### Risk 1: Taxonomy grows too fast

If too many surfaces or issue types are added now, the foundation becomes
speculative and hard to evolve.

Control:

* ship only the narrow first surface set and only the issue categories justified
  by the accepted Story and immediate follow-up Stories.

### Risk 2: Findings become a second trusted-knowledge path

If the model resembles `Insight` or validation too closely, DevLog’s knowledge
boundaries blur.

Control:

* use a dedicated package, dedicated table, and dedicated lifecycle states;
* avoid any implication that findings are authoritative project truth.

### Risk 3: Foundation accidentally absorbs Story 0053

If a public API and visibility layer are implemented here, sequencing becomes
unclear and the Story grows too large.

Control:

* keep this Story backend-foundation-first;
* defer public read exposure to Story `0053`.

### Risk 4: Lifecycle states pre-encode future remediation workflow

If statuses assume merge/archive/delete orchestration now, later Stories will
inherit premature workflow semantics.

Control:

* keep states minimal and review-oriented;
* let `0056` and `0058` own richer remediation behavior later.

## Planned Outcome

At the end of Story `0052`, DevLog should have:

* a first-class persisted maintenance-finding model in Core;
* bounded classification across surface, issue type, severity, status, and
  suggested action;
* focused tests proving persistence and basic lifecycle behavior;
* canonical documentation explaining the first-slice maintenance boundary.

It should not yet have:

* visible cockpit maintenance UX;
* public maintenance remediation workflow;
* broad multi-surface detector logic;
* automatic mutation of project memory.

Implementation Plan ready for human review.
