# Story 0056 — Human-Reviewed Remediation Workflow For Maintenance Findings — Repository Analysis

## Purpose

Analyze how DevLog should introduce a safe human-reviewed remediation workflow
for maintenance findings without:

* turning maintenance into a generic task-management subsystem;
* silently mutating trusted project memory;
* weakening ADR-053 human-control boundaries;
* conflating low-risk status handling with destructive remediation.

This Story is a **workflow and auditability** slice on top of the maintenance
domain.

It is not:

* a fully autonomous remediation Story;
* a broad work-management Story;
* a trusted-knowledge merge/delete Story;
* a new validation lifecycle for proposals or insights.

## Repository Context

### Current Git state

Repository branch at analysis time:

* `main`

Observed local nuance:

* Story `0055` is merged into local `main`;
* the local `0055` feature branch was cleaned up safely after PR merge;
* story directories `0056` through `0059` remain local untracked planning
  inputs;
* no implementation artifact exists yet for Story `0056` besides `story.md`.

Impact:

* the repository is ready for the next maintenance capability slice;
* Story `0056` can plan directly against the merged maintenance read path and
  the merged duplicate-debt/freshness producers.

### DevLog lifecycle

Story registration succeeded:

* DevLog story id: `bdb3eb97-076e-4203-bdf3-8818e170a665`

DevLog engineering-story context preparation fell back cleanly:

* `DEVLOG_CONTEXT_ERROR: DevLog request timed out. Repository Analysis continues without DevLog.`

Impact:

* this analysis proceeds from direct repository inspection;
* DevLog context unavailability is not a blocker for Story `0056`.

### Vault context

Vault was not consulted for this analysis.

Reason:

* the Story is tightly constrained by repository-local workflow seams, current
  maintenance state, and existing review/audit patterns;
* no transverse vault knowledge was needed to identify the main architectural
  gap.

## Story Understanding

Story `0056` asks DevLog to move maintenance findings from a passive warning
surface to a bounded human-reviewed remediation workflow.

The requested value is:

* explicit user actions for at least one finding family;
* tracked acknowledge / dismiss / resolve decisions;
* an audit trail describing who acted and why;
* preserved boundaries around destructive or ambiguous context mutations.

This is not simply “add a button”.

The repository currently supports:

* detection of maintenance issues;
* storage of findings;
* read-only cockpit visibility.

What it lacks is:

* a structured remediation decision model;
* mutation APIs for review actions;
* persisted auditability beyond a coarse `status`.

## Business Ownership

The capability belongs to Java Core with a likely backend-first delivery path.

Repository evidence suggests the work spans:

* `contextmaintenance` as the authoritative workflow/audit owner;
* Angular maintenance cockpit/workspace UI as the human action surface;
* existing trusted-knowledge and freshness domains only as the targets or
  context of remediation, not as new workflow owners.

The AI Engine is not the owner because:

* the Story is about explicit human-reviewed operations;
* ADR-053 keeps destructive or ambiguous changes under human control;
* the immediate gap is missing CRUD/workflow plumbing and audit persistence.

## Relevant Existing Architecture

### `docs/decisions/ADR-053.md`

This ADR is the primary architectural source for the Story.

It establishes that:

* context maintenance is internal and project-scoped;
* maintenance findings must be first-class and reviewable;
* human authority remains explicit for destructive or ambiguous changes;
* safe automatic actions must remain narrow, reversible, and low-risk.

Important implication:

Story `0056` should not normalize silent destructive remediation.

It should create a human-reviewed workflow around findings while preserving the
boundary between:

* acknowledging/removing the signal;
* and mutating the underlying project memory.

### `contextmaintenance/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceFinding.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceFindingStatus.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingService.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingController.java`
* `backend/src/main/resources/db/migration/V39__create_maintenance_findings_table.sql`

Current behavior:

* findings are persisted with:
  * classification;
  * summary/details;
  * `OPEN` / `RESOLVED` / `DISMISSED` status;
  * created/updated timestamps.
* the service only supports:
  * create;
  * list by project;
  * direct status update by id.
* the public controller only exposes:
  * `GET /api/v1/projects/{projectId}/maintenance-findings`
  * `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations`

Critical current limitation:

the domain has **no persisted audit trail** for remediation decisions.

Missing today:

* actor identity;
* rationale/comment;
* explicit action type beyond the final status;
* decision timestamp distinct from `updatedAt`;
* any remediation action record/history.

Important implication:

Story `0056` will almost certainly require either:

* additional finding fields for last decision metadata;
* or a dedicated remediation/audit entity.

### Existing maintenance UI

Relevant files:

* `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`
* `frontend/src/app/features/context-maintenance/maintenance-finding.models.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.html`

Current behavior:

* the cockpit loads and displays maintenance findings;
* the frontend model is read-only;
* no mutation methods or remediation actions exist;
* status is presented only as display information.

Important implication:

Story `0056` can likely extend the existing maintenance feature rather than
creating a completely separate UI surface, but the current frontend contract is
insufficient for review actions or audit metadata.

### `validation/*` as an existing audit pattern

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/validation/entity/Validation.java`
* `backend/src/main/java/com/hopeful117/devlogai/validation/dto/request/CreateValidationRequest.java`
* `backend/src/main/java/com/hopeful117/devlogai/validation/service/ValidationServiceImpl.java`

Current behavior:

* validations persist a decision;
* they record `validatedBy`, `validatedAt`, and optional `comment`;
* they represent one explicit human decision with traceability.

Important implication:

the repository already has a clear precedent for:

* human-reviewed decision capture;
* actor identity;
* optional rationale/comment.

Story `0056` should likely mirror this style rather than inventing an
unstructured status toggle.

### `projectcontextinput/*` as a bounded action pattern

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/controller/ProjectHumanContextInputController.java`

Current behavior:

* project-scoped read and write routes are explicit;
* archive is modeled as a dedicated lifecycle action endpoint rather than a
  generic free-form mutation RPC.

Important implication:

Story `0056` should likely expose explicit project-scoped remediation action
routes, not generic “update anything” endpoints.

### Story `0055` duplicate debt as the best first end-to-end family

Repository evidence strongly suggests the most credible first family for
end-to-end remediation is **trusted-knowledge duplicate debt** because:

* it already produces human-review-oriented findings;
* it is explicitly ambiguous/destructive enough to require human control;
* it can support tracked acknowledge/dismiss/resolve decisions even before full
  merge/delete tooling exists.

Important implication:

Story `0056` does not need to support every maintenance family equally in V1.

It can satisfy AC-1 by providing a complete review workflow for one bounded
family, most naturally the duplicate-debt findings introduced in `0055`.

## Architectural Constraints

### Do not equate status change with memory mutation

The current `updateStatus(...)` method changes the finding state directly.

That is insufficient as a remediation workflow because:

* it does not record who acted;
* it does not record why;
* it does not separate “finding reviewed” from “underlying memory changed”.

Therefore Story `0056` should avoid treating:

* `RESOLVED`
* `DISMISSED`

as naked status toggles with no audit record.

Preferred direction:

introduce explicit remediation actions with rationale and actor metadata.

### Preserve destructive boundaries

ADR-053 and the Story text explicitly require destructive or ambiguous changes
to remain blocked behind human control.

Therefore Story `0056` must not:

* auto-merge trusted duplicates;
* auto-delete trusted knowledge;
* auto-dismiss findings merely because a detector reran;
* silently mutate project memory as a side effect of a cockpit action unless an
  already-approved narrow path exists.

### Keep the workflow bounded

The Story is not asking for a general task board.

Therefore implementation should likely focus on:

* a small set of explicit actions;
* one family end-to-end;
* clear audit fields;
* minimal UI controls for the supported actions.

## Likely Design Direction

### 1. Add explicit remediation action requests

The repository currently lacks a remediation request model.

The likely seam is a new request DTO and service/controller method for actions
such as:

* acknowledge;
* dismiss with rationale;
* resolve with rationale;
* optionally launch a bounded remediation path when supported.

Important design choice:

these should probably be explicit action verbs rather than arbitrary status
updates.

### 2. Persist remediation decision metadata

The current `maintenance_findings` table cannot satisfy AC-3 by itself.

Repository evidence suggests one of two credible paths:

* enrich the finding row with last-action metadata;
* or add a remediation/audit-history table capturing each action.

Architecturally, a dedicated action-history table is cleaner if the Story wants
true audit trail semantics rather than only “latest actor/comment”.

### 3. Keep one supported family end-to-end, likely duplicate debt

To stay bounded, Story `0056` should probably implement the full review flow
for:

* `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`
* `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`
* `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`

This aligns with the repository’s most human-review-oriented maintenance slice.

### 4. Extend the existing cockpit maintenance feature rather than inventing a new page

The current cockpit card already displays active findings.

The most natural first UX extension is:

* add explicit action controls for supported findings;
* collect rationale when dismissing/resolving;
* reflect updated status and audit info in the existing component or an adjacent
  details interaction.

This is a better fit than building a broad new maintenance workspace in V1.

## Testing Implications

The repository already favors:

* service-level deterministic tests;
* controller WebMvc tests;
* focused frontend feature tests for action-oriented components.

Story `0056` should likely add:

* backend tests for status/action transitions;
* tests proving actor/comment/audit metadata persistence;
* tests blocking unsupported or ambiguous destructive actions;
* frontend tests for review controls and mutation state updates if UI actions
  are included in scope.

## Risks And Open Questions

### Risk: under-modeling the audit trail

If Story `0056` only adds comment fields to the current row, it may satisfy the
happy path but fail the architectural intent of “audit trail”.

Preferred mitigation:

decide explicitly whether the Story needs:

* last-action metadata;
* or append-only action history.

### Risk: overreaching into remediation engines

If the Story tries to launch real duplicate merges or broad cleanup workflows,
scope could balloon immediately.

Preferred mitigation:

keep the first remediation path bounded to explicit human-reviewed status
decisions and, at most, a narrow “launch supported remediation” placeholder or
traceable trigger.

### Open question: acknowledge semantics

The Story calls for “acknowledge or dismiss with rationale”.

The current status model only has:

* `OPEN`
* `RESOLVED`
* `DISMISSED`

Implementation planning must decide whether:

* `ACKNOWLEDGED` becomes a new persisted finding status;
* or acknowledgement is represented as an action history entry while the finding
  remains `OPEN`.

Repository evidence favors making this distinction explicit rather than
overloading `OPEN`.

## Recommended Planning Direction

Repository evidence supports the following plan for Story `0056`:

* introduce explicit remediation action handling in `contextmaintenance`;
* add persisted auditability for actor, action, rationale, and timestamp;
* support one bounded family end-to-end, most plausibly duplicate-debt
  findings from `0055`;
* extend the existing maintenance cockpit surface with targeted review actions;
* preserve a hard boundary between finding workflow and destructive project
  memory mutation.

That direction aligns with ADR-053, reuses existing repository patterns for
human-reviewed decisions, and keeps the first remediation workflow narrow and
traceable.
