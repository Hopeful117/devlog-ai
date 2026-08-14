# Story 0056 — Human-Reviewed Remediation Workflow For Maintenance Findings — Implementation Plan

## Overview

Implement Story `0056` as the **human-reviewed remediation workflow slice** for
maintenance findings.

The goal is to move maintenance from passive visibility to explicit,
traceable operator decisions by adding:

* explicit remediation actions;
* persisted auditability for who acted and why;
* a bounded end-to-end workflow for at least one supported finding family.

This Story should stay intentionally narrow.

It should not add:

* autonomous destructive remediation;
* trusted-knowledge merge or delete operations;
* a broad maintenance task-management product;
* new proposal-validation semantics.

## Final Implementation Strategy

The preferred implementation is:

1. introduce explicit maintenance remediation actions instead of raw status
   toggles;
2. persist an append-only remediation audit trail for each finding action;
3. extend the finding read model with latest remediation metadata and history as
   needed for UX;
4. support one bounded family end-to-end, most plausibly duplicate-debt
   findings from Story `0055`;
5. expose project-scoped backend endpoints for review actions;
6. extend the existing cockpit maintenance feature with minimal action UI;
7. keep destructive or ambiguous project-memory changes explicitly out of scope.

## Step 1 — Introduce explicit remediation action semantics

Targets:

* `contextmaintenance` domain model
* new request DTOs and service methods
* controller routing

Goals:

* satisfy AC-1 and AC-2 with explicit operator actions;
* avoid overloading plain status updates as if they were review decisions;
* keep the workflow understandable and auditable.

Implementation direction:

Define a bounded action set such as:

* acknowledge;
* dismiss;
* resolve.

Preferred design:

* treat these as explicit remediation actions rather than arbitrary field
  updates;
* map actions to allowed status transitions intentionally;
* validate action/request compatibility in the service layer.

Important rule:

* do not keep exposing remediation as a bare
  `updateStatus(projectId, findingId, status)` operation for the public
  workflow path.

Rationale:

The repository needs explicit decisions, not silent state mutation.

## Step 2 — Add persisted remediation audit history

Targets:

* new persistence model under `contextmaintenance`
* Flyway migration
* service/repository layer

Goals:

* satisfy AC-3;
* preserve who acted, what action was taken, when, and why;
* avoid losing history when a finding moves through multiple decisions.

Implementation direction:

Add an append-only remediation action entity/table containing at minimum:

* action id;
* finding id;
* action type;
* acted by;
* acted at;
* rationale/comment.

Preferred design:

* append-only history is better than mutating a single “last comment” field;
* the finding can still keep its current `status` as the current workflow
  summary, while actions preserve the decision trail.

Rationale:

`updatedAt` alone is not an audit trail, and the repository already uses
explicit actor/timestamp patterns in `validation`.

## Step 3 — Decide acknowledgement semantics explicitly

Targets:

* `MaintenanceFindingStatus`
* remediation action model
* service transition rules

Goals:

* avoid ambiguous meaning for “acknowledged”;
* keep open findings distinguishable from reviewed-but-not-resolved findings;
* align workflow semantics with the Story text.

Implementation direction:

Preferred first-slice choice:

* add `ACKNOWLEDGED` as a distinct finding status.

Alternative fallback:

* keep `OPEN` as the status and represent acknowledgement only in history.

Recommendation:

* prefer explicit `ACKNOWLEDGED` status because the Story asks for acknowledge
  as a first-class action and the cockpit will need to reflect that state.

Rationale:

Acknowledged-but-not-resolved is operationally different from untouched `OPEN`.

## Step 4 — Support one bounded family end-to-end, centered on duplicate debt

Targets:

* duplicate-debt maintenance findings from Story `0055`
* remediation service rules
* frontend action availability rules

Goals:

* satisfy AC-1 without overreaching across every maintenance family;
* prove the workflow on a high-value human-review-oriented case;
* preserve destructive boundaries.

Implementation direction:

Support full review workflow for:

* `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`
* `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`
* `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`

Recommended first-slice action behavior:

* acknowledge: operator has reviewed and accepted the need for follow-up;
* dismiss: operator judges the finding not actionable or not useful, with
  rationale;
* resolve: operator confirms an external/manual fix was applied, with rationale.

Important rule:

* resolving a duplicate-debt finding must not itself merge, delete, or rewrite
  trusted knowledge.

Rationale:

Duplicate debt is the most natural first family because it is already
human-review-oriented and explicitly non-destructive in current scope.

## Step 5 — Expose explicit project-scoped remediation endpoints

Targets:

* `MaintenanceFindingController`
* new request/response DTOs
* service routing

Goals:

* provide a stable API for review actions;
* keep workflow actions explicit and bounded;
* support frontend integration and backend tests.

Implementation direction:

Add explicit project-scoped action routes, for example:

* `POST /api/v1/projects/{projectId}/maintenance-findings/{findingId}/acknowledgements`
* `POST /api/v1/projects/{projectId}/maintenance-findings/{findingId}/dismissals`
* `POST /api/v1/projects/{projectId}/maintenance-findings/{findingId}/resolutions`

Alternative acceptable shape:

* one `POST /.../{findingId}/actions` endpoint with an explicit `action` field
  in the request body.

Preferred choice:

* a single action endpoint may be cheaper, but explicit routes keep semantics
  clearer and match the repository’s bounded-action style.

Rationale:

The repository already favors explicit lifecycle action endpoints over generic
free-form mutation RPCs.

## Step 6 — Extend the read model with latest remediation metadata

Targets:

* `MaintenanceFindingResponse`
* mapping layer
* frontend models/service

Goals:

* make action outcomes visible without requiring a separate history fetch for
  every card;
* support cockpit display of the current reviewed state;
* preserve room for future deeper audit views.

Implementation direction:

Extend the finding response with bounded latest-action information such as:

* current status;
* last action type;
* last acted by;
* last acted at;
* last rationale/comment.

Optional bounded extension:

* include a small action-history list only if the cockpit truly needs it now.

Preferred first-slice choice:

* return latest-action summary in the main response;
* keep full history accessible through a dedicated sub-resource only if needed.

Rationale:

This keeps the list view usable without overloading it with full audit payloads.

## Step 7 — Add service-side transition rules and guardrails

Targets:

* remediation service implementation
* controller validation
* tests

Goals:

* satisfy AC-4;
* prevent invalid or unsafe transitions;
* keep review actions consistent across callers.

Implementation direction:

Define rules such as:

* only `OPEN` findings can be acknowledged;
* `OPEN` or `ACKNOWLEDGED` findings can be dismissed or resolved;
* repeated identical actions on terminal findings should be blocked or treated
  as conflicts;
* rationale is required for dismissal and resolution;
* supported remediation actions may be restricted by issue type/family where
  appropriate.

Important rule:

* no review action may silently mutate trusted knowledge or other project
  memory.

Rationale:

The workflow must be explicit, safe, and deterministic.

## Step 8 — Extend the existing cockpit maintenance feature with minimal action UI

Targets:

* `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`
* `maintenance-finding.models.ts`
* `project-maintenance-section.ts/.html/.scss`

Goals:

* satisfy AC-1 and AC-2 on the user-facing path;
* avoid creating a whole new maintenance application surface;
* keep the first review UX compact and operational.

Implementation direction:

Extend the existing card/section to:

* show action buttons for supported findings;
* collect rationale for dismiss/resolve;
* refresh the list or local state after mutation;
* render acknowledged/resolved/dismissed state distinctly.

Preferred scope:

* keep the UI focused on the supported duplicate-debt family first;
* avoid broad multi-step workflows or modal-heavy merge tools.

Rationale:

The cockpit already hosts maintenance findings and is the natural first place
for operators to act.

## Step 9 — Add focused backend and frontend tests for workflow auditability

Targets:

* `contextmaintenance` service tests
* `MaintenanceFindingControllerWebMvcTest`
* frontend feature tests for the maintenance section

Goals:

* satisfy AC-5;
* prove status transitions and audit persistence;
* protect against unsafe or unsupported actions.

Implementation direction:

Add backend tests covering at least:

* acknowledge action persists actor/timestamp/comment and updates status
  correctly;
* dismiss action requires rationale and persists audit history;
* resolve action requires rationale and persists audit history;
* unsupported transitions or repeated terminal actions are rejected;
* only supported finding families expose the intended actions when scoped.

Add frontend tests covering at least:

* action controls render for supported findings;
* rationale submission calls the correct service method;
* updated status/audit summary renders after success;
* error states remain bounded and understandable.

Rationale:

This Story’s value is mostly workflow correctness and auditability, so tests
must focus there.

## Step 10 — Update canonical documentation for remediation boundaries

Targets:

* `README.md`
* `docs/knowledge-model.md`
* any other canonical maintenance-facing doc that proves necessary

Goals:

* satisfy AC-6;
* document what remediation actions exist;
* record what remains explicitly out of scope.

Implementation direction:

Document:

* that maintenance findings now support explicit human-reviewed actions;
* which actions are available in the first slice;
* that duplicate-debt findings can be acknowledged/dismissed/resolved with
  traceability;
* that no trusted-knowledge merge/delete mutation occurs through this workflow.

Rationale:

The repository must make the review boundary visible and explicit.

## Expected Implementation Shape

Repository evidence supports an implementation centered on:

* explicit maintenance remediation action requests;
* append-only audit history for finding decisions;
* a current-status summary on the finding;
* one bounded supported family, most likely duplicate debt;
* minimal cockpit action UI on top of the existing maintenance section.

## Validation Plan

Before requesting Code Review approval, validate with:

* targeted backend tests for remediation action services and controller routes;
* targeted frontend tests for the maintenance action surface;
* focused manual verification of one end-to-end duplicate-debt workflow if
  needed;
* regression checks that maintenance evaluation and read APIs still work after
  the workflow additions.

## Deferred Work

Explicitly defer to later Stories:

* trusted-knowledge merge/delete remediation tooling;
* autonomous remediation execution;
* cross-family remediation parity for every maintenance type;
* broad maintenance task orchestration or queue management;
* rich remediation dashboards outside the existing cockpit surface.
