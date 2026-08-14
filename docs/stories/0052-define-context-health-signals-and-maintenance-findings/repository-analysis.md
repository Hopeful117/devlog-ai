# Story 0052 — Define Context Health Signals And Maintenance Findings — Repository Analysis

## Purpose

Analyze how DevLog should introduce a first-class context-maintenance foundation
without:

* collapsing maintenance findings into trusted knowledge;
* weakening ADR-004 / ADR-006 human-review boundaries;
* over-modeling future maintenance surfaces before the first slice proves
  useful;
* coupling the model to one detector, one UI, or one remediation workflow.

This Story is a backend-foundation Story.

It is not yet:

* a remediation workflow Story;
* a broad autonomous maintenance Story;
* a UI-first Story;
* a duplicate-cleanup Story by itself.

## Repository Context

### Current Git state

Repository branch at analysis time:

* `main`

Observed working-tree nuance:

* `docs/decisions/ADR-053.md` is currently untracked;
* stories `0052` through `0059` are currently untracked;
* no implementation artifact exists yet for Story `0052`.

Impact:

* the workflow is at the first allowed stage: Repository Analysis;
* implementation planning must treat `ADR-053` and the `0052+` story set as
  in-repository design input even though they are not yet committed.

### DevLog lifecycle

Story registration succeeded:

* DevLog story id: `8aee44a1-a861-4aaa-8987-9bb8a970ce30`

DevLog repository-context preparation did not provide usable evidence:

* `DEVLOG_CONTEXT_ERROR: DevLog RepositoryContext contains no usable evidence.`

Impact:

* this analysis proceeds from direct repository inspection;
* DevLog unavailability is not a blocker for this Story.

### Vault context

Vault was not consulted for this analysis.

Reason:

* the repository already contains the decisive ADR set and recent related
  Stories for this capability;
* the design question is repository-local and well constrained by current
  source material.

## Story Understanding

Story `0052` asks DevLog to create the **first bounded domain model** for
context maintenance.

The requested result is not a detector yet.

It is the durable internal model answering:

* what kind of context-health issue was found;
* which context surface is affected;
* whether the finding is informational, actionable, or requires human review;
* what lifecycle state the finding is in;
* what action category is suggested.

The key intent is to create a reusable foundation for later Stories:

* `0053` exposes findings through API and cockpit;
* `0054` generates freshness / staleness findings;
* `0055` generates trusted-knowledge duplicate-debt findings;
* `0057` extends maintenance to internal human context inputs.

That future sequence is an important architectural signal:

Story `0052` should define the foundation broadly enough to support those next
slices, while still keeping the **initial persisted signal set intentionally
narrow**.

## Business Ownership

The affected capability is **internal context maintenance** for project-scoped
DevLog memory.

Repository evidence shows that this capability belongs in the Java Core backend,
not in the AI Engine and not in the frontend:

* ADR-053 defines maintenance as an internal DevLog capability;
* ADR-004 / ADR-006 keep human review and trusted-knowledge promotion inside
  Core-owned lifecycle boundaries;
* existing project-scoped domains such as `projectfreshness`,
  `projectcontextinput`, `insight`, and `analysis` are all backend-owned;
* Story `0053` treats API and cockpit exposure as a later read path on top of
  the backend model.

Ownership therefore appears to be:

* capability owner: Java Core
* future read surface: backend API, then Angular cockpit
* future advisory interpretation: optionally AI-assisted, but not in this Story

## Relevant Existing Architecture

### `docs/decisions/ADR-053.md`

This is the primary architectural source for the Story.

It establishes that:

* context maintenance is a distinct capability;
* maintenance findings must be first-class and reviewable;
* deterministic evaluation should be primary where practical;
* human approval remains explicit for destructive or ambiguous actions;
* the capability spans multiple context surfaces, including:
  * project understanding projections;
  * timeline projections;
  * internal human context inputs;
  * trusted knowledge.

Important implication:

Story `0052` does not need to decide whether maintenance findings should exist.

It needs to decide:

* what the first bounded model looks like;
* which first one or two surfaces are represented now;
* how the model stays distinct from trusted knowledge and proposal history.

### `projectfreshness/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/projectfreshness/ProjectFreshnessService.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectfreshness/ProjectFreshnessSummary.java`
* `backend/src/test/java/com/hopeful117/devlogai/projectfreshness/ProjectFreshnessServiceTest.java`

This package already models **operational freshness** as a deterministic,
project-scoped concern.

It is explicitly:

* Source-scoped;
* operational;
* not trusted knowledge;
* bounded by explainable states and timestamps.

Architectural implication:

`projectfreshness` is the clearest existing precedent for one of the first
maintenance surfaces. It demonstrates that DevLog already accepts explicit,
non-trusted operational state about context quality.

### `projectcontextinput/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/entity/ProjectHumanContextInput.java`
* `backend/src/main/resources/db/migration/V38__create_project_human_context_inputs.sql`
* Story `0050` Repository Analysis and implementation artifacts

This package already models project-owned human context as a dedicated entity
with its own type and lifecycle status.

Architectural implication:

the repository already has a strong pattern for adding a new project-scoped
context domain without confusing it with trusted knowledge.

It also proves that future maintenance findings for human context should remain
**about** these records, not **inside** them.

### `insight/*` and `TrustedKnowledgeDuplicateAuditService`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/insight/service/TrustedKnowledgeDuplicateAuditService.java`
* Story `0040` Repository Analysis

The repository already contains deterministic duplicate-audit logic for trusted
knowledge clusters.

Architectural implication:

Story `0052` does not need to invent duplicate-remediation semantics from
scratch. It can define a findings model that later Stories may use to surface
the output of this class of deterministic audit.

### Existing project-scoped domain pattern

Recent repository additions consistently use:

* a dedicated package by capability;
* a dedicated entity or persisted state model;
* repository + service + controller or projection service;
* Flyway migration;
* service tests and WebMvc tests when the capability is exposed.

That pattern is visible in:

* `projectcontextinput`
* `projectfreshness`
* `analysis/diagnostics`
* `insight`

Architectural implication:

the maintenance-finding model should likely become its own package and own
tables rather than being embedded inside `Insight`, `Validation`,
`ProjectState`, or `ProjectHumanContextInput`.

## Existing Boundaries That Must Remain Intact

### Findings are not trusted knowledge

Repository evidence strongly rejects treating findings as trusted knowledge:

* ADR-006 keeps proposals and trusted knowledge distinct;
* ADR-049 keeps promotion into trusted knowledge explicit and controlled;
* ADR-052 keeps internal human context distinct from trusted knowledge;
* ADR-053 describes findings as reviewable maintenance artifacts, not as
  project truth.

Therefore Story `0052` must not model a maintenance finding as:

* an `Insight`;
* a `ValidatableProposal`;
* a `Validation`;
* a `ProjectHumanContextInput`;
* a generic documentation row.

### Findings are not proposal history

Proposal history already records AI-generated candidate interpretations and
validation outcomes.

Maintenance findings serve a different purpose:

* they represent context-health issues;
* they may be deterministic;
* they may remain advisory;
* they may point to remediation work without being promotion candidates.

Using proposal history for maintenance findings would blur ADR-006’s boundary
and make non-AI maintenance signals look like proposal lifecycle events.

### Findings should not be hidden inside projections

Story `0053` explicitly treats API/UI exposure as a next Story.

That means Story `0052` should define **authoritative persisted findings** first
and allow later read models or cockpit projections to consume them.

Modeling findings only as a `ProjectState` or cockpit projection would create
the wrong dependency direction.

## Recommended First-Slice Scope

The repository now contains enough evidence to recommend a narrow first slice.

### Recommended initial surfaces

Prefer only two high-value surface categories in the first model:

1. `PROJECT_UNDERSTANDING`
2. `PROJECT_PROJECTION`

Why:

* Story `0054` immediately targets stale project understanding and outdated
  projection refreshes;
* these are the least ambiguous and most deterministic signals described by
  ADR-053;
* they map to capabilities that already exist today (`analysis` /
  `projectfreshness` / project-facing projections);
* they avoid prematurely pulling trusted-knowledge duplicate debt and internal
  human context hygiene into the first persisted taxonomy.

This keeps the first slice intentionally narrow while still leaving a clean path
for future surfaces such as:

* `TRUSTED_KNOWLEDGE`
* `INTERNAL_HUMAN_CONTEXT`

### Recommended initial finding semantics

The first persisted model should minimally support:

* affected surface;
* issue type;
* severity or priority;
* status;
* suggested action category;
* human-review requirement;
* explanation / rationale text;
* timestamps.

That aligns directly with the Story ACs and future Story dependencies.

### Recommended action semantics

The action category should remain coarse and workflow-safe in this Story.

It should distinguish categories such as:

* informational / monitor;
* refresh / regenerate;
* review required;
* investigate.

The model should not yet imply:

* direct mutation of trusted knowledge;
* auto-merge semantics;
* destructive cleanup;
* validation approval semantics.

## Candidate Implementation Shape

The most natural repository fit appears to be a dedicated backend capability
package, for example a new `contextmaintenance` boundary containing:

* entity for persisted maintenance finding;
* enums for surface, issue type, severity/priority, status, suggested action;
* repository;
* service layer for creation / lifecycle updates;
* migration for the first findings table.

Important modeling guidance:

* findings should be project-scoped;
* findings should preserve creation and update timestamps;
* findings should remain traceable to a surface and issue classification;
* findings should allow advisory and reviewable outcomes without requiring a
  remediation workflow yet.

Important non-goal:

* Story `0052` does not need to build the full API/controller surface if the
  repository chooses to defer external read exposure to Story `0053`.

## Dependencies

Relevant repository dependencies for this Story are:

* `Project` ownership and project-scoped repository conventions;
* Flyway migrations for new persistence;
* Spring Data JPA entity + repository patterns;
* auditing conventions (`createdAt`, `updatedAt`) already used in
  `ProjectHumanContextInput`;
* future integration points with:
  * `projectfreshness`
  * `analysis`
  * `insight`
  * `projectcontextinput`

No external service dependency appears necessary for the core domain model.

## Tests

Relevant existing testing patterns:

* service-level classification / lifecycle tests in `projectfreshness`;
* service and controller tests in `projectcontextinput`;
* deterministic behavior tests around `TrustedKnowledgeDuplicateAuditService`.

Important likely test scope for Story `0052`:

* persistence of the maintenance-finding entity;
* enum / lifecycle invariants;
* project-scoped retrieval or update behavior if a service boundary is added;
* non-regression that findings remain distinct from trusted-knowledge models.

Validation commands likely relevant later:

* `./mvnw test`
* targeted backend tests for the new maintenance package

## Risks

### 1. Over-broad taxonomy in the first slice

If Story `0052` defines too many surfaces and issue types up front, the model
will encode speculative distinctions before the first real detectors exist.

Recommended mitigation:

* keep the initial surface set to the two immediately justified categories;
* add later categories when a real producing Story needs them.

### 2. Blurring findings with trusted knowledge or proposal lifecycle

If findings are modeled as `Insight`-like or `Proposal`-like records, the
repository will violate the current architectural separation.

Recommended mitigation:

* use a dedicated domain package and table;
* keep maintenance status independent from validation status.

### 3. Designing status around future remediation too early

If the first lifecycle assumes a full remediation engine, Story `0052` may
overfit future workflow details that belong to Stories `0056` and `0058`.

Recommended mitigation:

* keep status minimal and review-oriented;
* avoid encoding destructive-action states now.

### 4. Hiding findings in read models instead of persisting them

If findings only exist as transient cockpit or projection data, later review,
API, and remediation workflows will lack a stable authoritative object.

Recommended mitigation:

* persist findings first;
* let Story `0053` expose them.

## Conclusion

The repository is ready for Story `0052`.

The clearest implementation direction is:

1. introduce a dedicated project-scoped maintenance-finding domain in the Java
   Core;
2. keep it explicitly separate from trusted knowledge, proposal history, and
   human context entities;
3. scope the first signal vocabulary to `PROJECT_UNDERSTANDING` and
   `PROJECT_PROJECTION`;
4. provide enough lifecycle and action classification to support future
   freshness, duplicate-debt, and human-context maintenance Stories without
   overcommitting to those later workflows now.

Repository Analysis ready for human review.
