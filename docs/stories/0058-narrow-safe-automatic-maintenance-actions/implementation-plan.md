# Story 0058 — Narrow Safe Automatic Maintenance Actions — Implementation Plan

## Overview

Implement Story `0058` as the **first safe automation slice** for the
maintenance capability introduced by Stories `0052`, `0054`, `0056`, and
`0057`.

The goal is not to automate maintenance broadly.

The goal is to let DevLog automatically reconcile a small class of
deterministic maintenance findings when the authoritative evaluation proves that
their underlying condition has disappeared.

This Story should stay intentionally narrow.

It should not add:

* a maintenance scheduler or background daemon;
* autonomous trusted-knowledge mutation;
* automatic archival of human context;
* automatic closure of duplicate-debt findings;
* a broad automation-control UI.

## Final Implementation Strategy

The preferred implementation is:

1. introduce explicit audit semantics for system-owned automatic maintenance
   actions;
2. keep automation inside the existing project-scoped maintenance evaluation
   flow;
3. auto-resolve only deterministic finding families whose conditions can be
   recomputed authoritatively;
4. preserve human-reviewed outcomes by never overwriting dismissed findings;
5. surface the automatic action through the existing append-only finding
   history;
6. document exactly which families are automation-eligible and why.

## Step 1 — Introduce explicit automatic action semantics

Targets:

* `MaintenanceFindingActionType`
* `contextmaintenance` service logic
* action-history serialization/tests

Goals:

* distinguish automated finding management from human remediation;
* satisfy AC-2 with explicit traceability;
* avoid overloading human `RESOLVE` semantics.

Implementation direction:

Add a dedicated automatic action type, preferably:

* `AUTO_RESOLVE`

Also define a deterministic system actor convention, such as:

* a reserved UUID constant inside `contextmaintenance`

Preferred first-slice behavior:

* every automatic closure writes an append-only action entry with:
  * `actionType = AUTO_RESOLVE`
  * reserved system actor UUID
  * explanatory comment describing the cleared deterministic condition

Rationale:

The repository already has append-only audit history. Story `0058` should make
system-owned actions explicit, not implicit.

## Step 2 — Keep automation inside `evaluate(projectId)`

Targets:

* `MaintenanceEvaluationServiceImpl`
* `MaintenanceFindingController`
* `MaintenanceEvaluationResponse` only if response reconciliation counts are
  needed

Goals:

* keep the automation seam narrow and deterministic;
* avoid introducing schedulers, watchers, or background maintenance loops;
* reuse the existing evaluation authority for maintenance recomputation.

Implementation direction:

Extend the existing:

* `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations`

so that one evaluation run can:

* create new deterministic findings when conditions are present;
* auto-resolve eligible existing findings when conditions are absent.

Preferred first-slice choice:

* no new endpoint for automation;
* no separate scheduler concept;
* no policy toggle system yet.

Rationale:

The evaluation flow already owns deterministic maintenance recomputation.
Keeping automation there avoids architectural spread.

## Step 3 — Restrict eligibility to deterministic finding families only

Targets:

* `MaintenanceEvaluationServiceImpl`
* maintenance issue-family routing helpers
* targeted tests

Goals:

* satisfy AC-1 and AC-3 safely;
* ensure the first slice favors too little automation over too much;
* keep automation explainable from repository state alone.

Implementation direction:

Restrict automatic resolution to the families whose condition can be
deterministically re-evaluated:

* `STALE_PROJECT_UNDERSTANDING`
* `MISSING_PROJECTION_REFRESH`
* `STALE_HUMAN_CONTEXT_INPUT`

Preferred rule:

* only auto-resolve when the next authoritative evaluation proves the finding’s
  trigger condition no longer holds for the same bounded rule identity.

Important rule:

* do **not** auto-resolve:
  * `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`
  * `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`
  * `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`

Rationale:

Duplicate debt remains too semantically sensitive for safe automatic closure in
this slice.

## Step 4 — Preserve human governance boundaries during automatic reconciliation

Targets:

* finding status transition logic
* maintenance reconciliation helpers
* tests for blocked cases

Goals:

* ensure automation never overwrites explicit human intent;
* keep the resulting lifecycle understandable;
* preserve the distinction between reviewable and system-owned outcomes.

Implementation direction:

Automatic resolution should likely apply only when finding status is:

* `OPEN`
* `ACKNOWLEDGED`

Automation should not overwrite:

* `DISMISSED`
* `RESOLVED`

Preferred behavior:

* `DISMISSED` stays as an explicit human judgment even if the underlying
  condition later clears;
* `RESOLVED` remains terminal for that finding instance.

Rationale:

This preserves the existing human-review contract instead of letting evaluation
quietly rewrite deliberate human outcomes.

## Step 5 — Reconcile deterministic findings by rule identity, not by broad status only

Targets:

* equivalence / matching logic in `MaintenanceEvaluationServiceImpl`
* finding summary/detail generation

Goals:

* auto-resolve only the intended finding instance;
* avoid accidental closure of unrelated findings in the same family;
* keep the action explainable and testable.

Implementation direction:

Use the same bounded identity principles already used for creation-side
deduplication:

* context surface
* issue type
* summary/details anchor or equivalent deterministic rule identity

Preferred first-slice behavior:

* match only eligible still-open findings whose deterministic rule anchor is no
  longer emitted by the current evaluation;
* record one `AUTO_RESOLVE` action per auto-resolved finding.

Rationale:

The safest automation is not “close all findings of a family when current
evaluation has none,” but “close the specific eligible findings whose exact
deterministic condition no longer exists.”

## Step 6 — Keep frontend impact minimal and observational

Targets:

* `frontend/src/app/features/context-maintenance/maintenance-finding.models.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`
* related specs only if needed

Goals:

* keep the UI aligned with new audit semantics;
* avoid inventing a broad automation-control experience;
* ensure users can see that an automatic action occurred.

Implementation direction:

If the current action-history model already renders latest action summaries,
only minimal frontend change may be needed:

* include `AUTO_RESOLVE` in the action-type model;
* ensure the humanized label renders sensibly.

Preferred first-slice UX:

* no new controls;
* no new settings panel;
* automation is visible only through the finding status and audit trail.

Rationale:

Story `0058` is primarily backend governance and traceability work, not a new
UI feature set.

## Step 7 — Update canonical documentation with explicit automation boundaries

Targets:

* `docs/knowledge-model.md`
* `docs/ui-ux.md`
* implementation report documentation outcome

Goals:

* satisfy AC-5;
* document exactly which actions are automated and why they are safe;
* preserve the distinction from human-reviewed remediation and prohibited
  autonomous mutation.

Implementation direction:

Update canonical docs to state that:

* only a narrow subset of deterministic findings can auto-resolve;
* automatic resolution is recorded explicitly in finding audit history;
* duplicate-debt findings remain human-reviewed;
* automation does not delete, merge, archive, or semantically rewrite project
  memory.

Rationale:

This Story is fundamentally about governance boundaries. The repository docs
must say so clearly.

## Step 8 — Validate both successful and blocked automation paths

Targets:

* `MaintenanceEvaluationServiceTest`
* `MaintenanceFindingServiceTest`
* `MaintenanceFindingControllerWebMvcTest`
* frontend maintenance tests if action-type rendering changes

Goals:

* satisfy AC-4;
* prove automatic resolution works only for eligible deterministic cases;
* prove blocked families and statuses stay untouched.

Implementation direction:

Backend tests should cover at minimum:

* auto-resolve a stale-understanding finding when freshness becomes current;
* auto-resolve a missing-projection finding when the gap disappears;
* auto-resolve a stale-human-context finding when the note is no longer stale
  or no longer active;
* do not auto-resolve duplicate-debt findings;
* do not auto-resolve dismissed findings;
* record explicit `AUTO_RESOLVE` history with system actor metadata.

Frontend tests should cover at minimum:

* action-history rendering remains stable when `AUTO_RESOLVE` appears.

Rationale:

The value of this Story is safe selectivity, so negative tests are essential.

## Planned Validation Commands

Backend:

```bash
cd backend
./mvnw -Dtest=MaintenanceEvaluationServiceTest,MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest test
```

Frontend:

```bash
cd frontend
npm test -- --watch=false --include src/app/features/context-maintenance/maintenance-finding.service.spec.ts --include src/app/features/context-maintenance/project-maintenance-section.spec.ts
npm run lint
npm run format:check
npm run build
```

## Expected Outcome

After Story `0058`:

* DevLog can automatically resolve a narrow class of deterministic maintenance
  findings when their condition clears;
* every automatic closure is explicitly traceable in the same audit model as
  human remediation;
* human dismissals remain authoritative;
* duplicate-debt and other semantically ambiguous findings remain outside the
  automatic path.
