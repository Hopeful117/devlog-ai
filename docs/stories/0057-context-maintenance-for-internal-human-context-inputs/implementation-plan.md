# Story 0057 — Context Maintenance For Internal Human Context Inputs — Implementation Plan

## Overview

Implement Story `0057` as the **first human-context maintenance slice** on top
of:

* Story `0050` internal human context inputs;
* Story `0052` maintenance-finding foundation;
* Story `0056` human-reviewed remediation workflow.

The goal is to let DevLog detect and expose a bounded class of stale or
superseded **active** human-authored project context without:

* reclassifying it as trusted knowledge;
* silently archiving notes;
* inventing a broad semantic note-management system.

This Story should stay intentionally narrow.

It should not add:

* automatic archival of notes;
* semantic AI-based note supersession judgment;
* note editing/version history redesign;
* promotion of human context into trusted knowledge;
* a second review workflow separate from maintenance findings.

## Final Implementation Strategy

The preferred implementation is:

1. extend the maintenance domain to explicitly cover internal human context;
2. add one bounded human-context maintenance issue family for the first slice;
3. evaluate only `ACTIVE` human context inputs with deterministic,
   conservative rules;
4. generate project-scoped maintenance findings through the existing
   maintenance evaluation seam;
5. reuse the `0056` review workflow for these findings;
6. keep note lifecycle state and maintenance diagnosis separate;
7. update canonical documentation and focused tests accordingly.

## Step 1 — Extend the maintenance taxonomy for the human-context surface

Targets:

* `MaintenanceContextSurface`
* `MaintenanceFindingIssueType`
* frontend maintenance models
* related tests and serialization expectations

Goals:

* represent human-context hygiene explicitly in the maintenance domain;
* avoid overloading `PROJECT_UNDERSTANDING` with a note-specific concern;
* preserve clear separation from trusted-knowledge duplicate debt.

Implementation direction:

Add a dedicated maintenance surface, likely:

* `INTERNAL_HUMAN_CONTEXT`

Add one bounded issue type for the first slice, likely:

* `STALE_HUMAN_CONTEXT_INPUT`

Optional fallback if needed by the final wording:

* `SUPERSEDED_HUMAN_CONTEXT_INPUT`

Preferred first-slice choice:

* start with a single stale/superseded-active-note family rather than multiple
  nuanced note issue types.

Rationale:

The repository already keeps human context as a distinct domain. Maintenance
should mirror that boundary directly.

## Step 2 — Keep note lifecycle state separate from maintenance diagnosis

Targets:

* `ProjectHumanContextInputStatus`
* `ProjectHumanContextInputServiceImpl`
* maintenance evaluation and finding workflow

Goals:

* avoid mixing business state and maintenance assessment;
* preserve Story `0050` semantics;
* keep archival as an explicit note-domain action rather than a maintenance
  side effect.

Implementation direction:

Do **not** extend `ProjectHumanContextInputStatus` with values such as:

* `STALE`
* `SUPERSEDED`
* `LOW_PRIORITY`

Keep the note lifecycle unchanged for this Story:

* `ACTIVE`
* `ARCHIVED`

Express maintenance concerns only via `maintenance_findings`.

Rationale:

A note can remain `ACTIVE` while still being a review-worthy maintenance
concern. The finding model already exists to express that distinction.

## Step 3 — Add deterministic evaluation for active human context inputs

Targets:

* `MaintenanceEvaluationServiceImpl`
* `ProjectHumanContextInputRepository`
* supporting helpers inside `contextmaintenance`

Goals:

* satisfy AC-1 and AC-4 with a bounded deterministic slice;
* avoid speculative semantic ranking;
* create explainable findings from persisted note metadata.

Implementation direction:

Extend `MaintenanceEvaluationServiceImpl.evaluate(projectId)` to inspect
project-scoped human context inputs.

Evaluation policy for V1:

* only inspect `ACTIVE` inputs;
* ignore `ARCHIVED` inputs for open-finding generation;
* apply conservative deterministic rules based on persisted fields such as:
  * `updatedAt`
  * `type`
  * relative recency among active notes
* generate findings only when the rule can be explained directly from metadata.

Preferred rule shape:

* detect stale active inputs using age/recency thresholds or a bounded
  “older active note overshadowed by newer active context of the same semantic
  family” heuristic;
* avoid using free-form AI interpretation for this first slice.

Important rule:

* do not create findings for every old note automatically if the rule would be
  too noisy for long-lived goals and constraints.

Rationale:

ADR-053 explicitly prefers deterministic maintenance first, and the current
repository has no robust note-history or supersession graph to support anything
more ambitious safely.

## Step 4 — Preserve idempotency and stable equivalence for human-context findings

Targets:

* open-finding suppression in `MaintenanceEvaluationServiceImpl`
* finding summary/detail generation

Goals:

* avoid repeated identical findings on every evaluation run;
* keep the maintenance cockpit stable and low-noise;
* align human-context findings with the same operational discipline as
  freshness and duplicate-debt findings.

Implementation direction:

Treat a human-context finding as equivalent when the combination remains stable:

* context surface
* issue type
* affected input identity or deterministic summary anchor
* summary/details payload

Preferred first-slice behavior:

* skip creation when an equivalent `OPEN` or review-active finding already
  exists for the same active note condition;
* allow a new finding only after prior resolution/dismissal if the condition
  remains or reappears.

Rationale:

Without stable equivalence, the feature would quickly become noisy and fail the
story’s hygiene objective.

## Step 5 — Reuse the existing human-reviewed maintenance workflow

Targets:

* `MaintenanceFindingServiceImpl`
* `MaintenanceFindingController`
* `ProjectMaintenanceSection`
* frontend maintenance service/models/tests

Goals:

* satisfy AC-2 and AC-3 without inventing a parallel workflow;
* keep review decisions auditable;
* preserve explicit human control around ambiguous archival decisions.

Implementation direction:

Allow the Story `0056` action workflow to operate for the new human-context
findings as well:

* acknowledge
* dismiss with rationale
* resolve with rationale

Preferred semantics:

* acknowledge:
  * operator reviewed the note-level concern and accepts follow-up is needed;
* dismiss:
  * operator judges the finding not useful or not applicable, with rationale;
* resolve:
  * operator confirms the note was handled outside the finding workflow
    (for example by archiving or replacing it through the context-input feature),
    with rationale.

Important rule:

* resolving the finding must not itself archive or mutate the note.

Rationale:

This preserves the clear boundary between maintenance review and note-domain
state changes.

## Step 6 — Extend the maintenance cockpit copy and availability rules

Targets:

* `frontend/src/app/features/context-maintenance/maintenance-finding.models.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.html`
* related frontend specs

Goals:

* make the new findings legible to users;
* keep the maintenance section coherent across surfaces;
* distinguish human-context hygiene from trusted-knowledge duplicate debt.

Implementation direction:

Extend frontend models with the new surface / issue type values.

Update UI labeling so users can clearly see:

* surface = internal human context;
* issue = stale/superseded active note;
* action remains review-oriented rather than destructive.

Preferred first-slice UX:

* reuse the same action controls as `0056`;
* adapt the surface label and explanatory copy;
* optionally guide the user toward the dedicated project-notes section for the
  actual archive/change operation.

Rationale:

The current maintenance panel already provides the right cockpit seam. This
Story should extend it, not fragment it.

## Step 7 — Keep dedicated note management in the context-input feature

Targets:

* `project-context-inputs` frontend feature
* note-domain service/API boundaries

Goals:

* preserve clear ownership of archive operations;
* avoid accidental scope creep into a maintenance-driven note editor;
* maintain the distinction required by AC-2 and the Story constraints.

Implementation direction:

Do not add hidden archive side effects to the maintenance action endpoints.

If the UX needs a handling path, prefer one of:

* user archives the note via the existing context-inputs section, then resolves
  the finding;
* user dismisses the finding with rationale when the note should remain active.

Optional bounded enhancement:

* add minimal copy or linking guidance between maintenance findings and the
  project-notes panel if useful, but do not redesign the notes feature in this
  Story.

Rationale:

The repository already has a dedicated note-management seam. Maintenance should
point to it, not subsume it.

## Step 8 — Reconcile canonical documentation

Targets:

* `docs/knowledge-model.md`
* possibly `docs/ui-ux.md`
* implementation report documentation outcome

Goals:

* satisfy AC-5;
* document that internal human context now participates in context maintenance;
* keep the distinction from trusted knowledge explicit.

Implementation direction:

Update the relevant canonical docs to state that:

* internal human context is now a maintenance surface;
* findings for that surface are operational review records;
* those findings do not themselves turn notes into trusted knowledge;
* resolving a finding does not silently rewrite project memory.

Rationale:

This is durable repository behavior and should be reflected in canonical docs.

## Step 9 — Validate with focused backend and frontend tests

Targets:

* `MaintenanceEvaluationServiceTest`
* `MaintenanceFindingServiceTest`
* `MaintenanceFindingControllerWebMvcTest`
* relevant human-context-input tests if impacted
* `project-maintenance-section.spec.ts`
* `maintenance-finding.service.spec.ts`

Goals:

* satisfy AC-4 and non-regression expectations;
* prove human-context maintenance stays separate from trusted-knowledge logic;
* prove still-valid notes are not flagged under the bounded first-slice policy.

Implementation direction:

Backend tests should cover at minimum:

* creation of a human-context maintenance finding for the bounded stale case;
* no finding for archived notes;
* no duplicate open finding for the same note condition;
* remediation action transitions and audit trail for the new finding family.

Frontend tests should cover at minimum:

* rendering of the new surface / issue labels;
* action availability for the new finding family;
* acknowledgement / dismissal / resolution request flow.

Important rule:

* include at least one negative test proving a still-valid active note does not
  trigger a finding under the chosen heuristic.

Rationale:

The main failure mode of this Story is false-positive maintenance noise, so
negative tests are as important as positive ones.

## Planned Validation Commands

Backend:

```bash
cd backend
./mvnw -Dtest=MaintenanceEvaluationServiceTest,MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest,ProjectHumanContextInputServiceTest test
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

After Story `0057`:

* DevLog can produce explicit maintenance findings for a bounded class of stale
  internal human context inputs;
* these findings remain distinct from trusted knowledge and note lifecycle
  state;
* users can review them through the same audited maintenance workflow already
  introduced in Story `0056`;
* still-valid active notes remain protected from aggressive false-positive
  cleanup pressure.
