# Story 0058 — Narrow Safe Automatic Maintenance Actions — Repository Analysis

## Workflow Status

* Story `0058` is registered in DevLog as `1f757909-0f21-4dca-9e7a-5ae1ee9abf7f`.
* DevLog repository-context preparation returned no usable projected evidence:
  `DEVLOG_CONTEXT_ERROR: DevLog RepositoryContext contains no usable evidence. Repository Analysis continues without DevLog.`
* Repository analysis therefore relies on direct repository inspection.

## Story Intent

Story `0058` is not asking for broad automation.

It is asking for the first **narrow safe automatic maintenance action** that:

* is deterministic;
* is low-risk;
* is reversible or safely recomputable;
* is traceable;
* does not silently mutate trusted or ambiguous project memory.

The repository already has:

* maintenance finding creation (`0052`, `0054`, `0055`, `0057`);
* explicit human review workflows (`0056`);
* traceable action history on findings.

The missing piece is a system-owned action path for the subset of cases where
manual maintenance handling is unnecessary because the underlying deterministic
condition has already disappeared.

## Relevant Repository Evidence

### 1. Maintenance evaluation already exists, but it is create-only

`MaintenanceEvaluationServiceImpl` currently does three things:

* evaluates deterministic freshness gaps;
* evaluates trusted-knowledge duplicate debt;
* evaluates stale active human-context inputs.

It only:

* creates new findings;
* skips equivalent open findings.

It does **not**:

* auto-resolve obsolete findings;
* auto-dismiss findings;
* record any system-owned maintenance action.

So the current model accumulates findings but does not automatically reconcile
them when a deterministic condition clears.

### 2. The current remediation model is explicitly human-shaped

Story `0056` introduced:

* `MaintenanceFindingAction`
* `MaintenanceFindingActionType`
* `MaintenanceFindingStatus.ACKNOWLEDGED`
* explicit action endpoints:
  * acknowledge
  * dismiss
  * resolve

Current `MaintenanceFindingActionType` contains only:

* `ACKNOWLEDGE`
* `DISMISS`
* `RESOLVE`

Current action requests require:

* `actedBy: UUID`
* optional `comment`

That model assumes a human reviewer identity.

There is no explicit system action identity and no explicit action type that
distinguishes:

* human-reviewed remediation;
* automatic finding management.

This is the main architectural seam Story `0058` needs to address.

### 3. ADR-053 strongly constrains what is safe to automate

`docs/decisions/ADR-053.md` is very specific:

* automatic actions must be narrow, reversible, low-risk, and semantically
  unambiguous;
* automatic deletion, merge, archival, or semantic mutation of trusted context
  must not occur silently;
* illustrative safe examples include:
  * refreshing derived health indicators;
  * creating maintenance findings;
  * scheduling regeneration suggestions;
  * marking a maintenance check as completed.

That last example matters.

The repository already has deterministic finding creation and explicit human
review workflows. The safest missing automation is therefore not “take a new
destructive action,” but “mark a deterministic maintenance condition as no
longer applicable.”

### 4. Some finding families are much safer to auto-close than others

Current finding families are not equally eligible.

#### Safe candidates

The following families are good candidates for automatic closure when their
underlying condition disappears:

* `STALE_PROJECT_UNDERSTANDING`
  * safe when freshness summary says the relevant checked source is no longer
    stale
* `MISSING_PROJECTION_REFRESH`
  * safe when the freshness projection exists again and `uncheckedSourceCount`
    returns to `0`
* `STALE_HUMAN_CONTEXT_INPUT`
  * safe only when the input is no longer active or no longer satisfies the
    deterministic stale rule

These are deterministic because the repository already has authoritative
evaluation logic for them.

#### Unsafe candidates

The duplicate-debt families are not equivalent:

* `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`
* `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`
* `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`

These findings are much riskier to auto-close because their resolution often
depends on semantic judgment or human interpretation of whether trusted memory
was genuinely remediated.

Even if a later duplicate audit no longer emits the same cluster, that can
reflect drift in clustering or detection boundaries rather than an obviously
safe cleanup event.

Therefore duplicate-debt findings should remain human-reviewed in this Story.

### 5. The repository already has enough traceability primitives

`MaintenanceFindingAction` already records:

* action type;
* acted by;
* acted at;
* comment.

So Story `0058` does not need a brand new audit subsystem.

It likely needs only:

* an explicit automatic action type or equivalent trace marker;
* a deterministic actor identity for system-owned actions;
* service logic that applies an automatic resolution path intentionally.

### 6. The current API/UI seam does not require broad new automation UX

The current maintenance cockpit already displays:

* status;
* action history;
* latest action summary.

That means a bounded automatic action can remain mostly backend-driven as long
as the resulting audit trail is surfaced as ordinary finding history.

The Story likely does **not** require:

* a new automation dashboard;
* a toggle-heavy policy UI;
* a scheduler product;
* a second review module.

## Architectural Implications

### A. The safest first automatic action is deterministic auto-resolution of cleared findings

The most repository-aligned safe action is:

* when a deterministic maintenance finding is still open or acknowledged;
* and the next authoritative evaluation proves the underlying condition is gone;
* automatically resolve that finding with explicit audit trace.

Why this is strongest:

* it does not introduce a new destructive mutation;
* it is reversible through re-evaluation if the condition reappears;
* it is derivable from the same deterministic sources that created the finding;
* it reduces repetitive manual cleanup for already-cleared operational debt.

This is much safer than:

* auto-archiving human context notes;
* auto-dismissing duplicate debt;
* auto-merging trusted knowledge;
* auto-refreshing analyses.

### B. Automatic closure must stay distinct from human remediation

Story `0056` created a human-owned remediation trail.

Story `0058` must preserve a clear distinction between:

* human reviewed and accepted/dismissed/resolved;
* system detected that a deterministic condition no longer applies.

The current action model does not yet encode that difference clearly enough.

The most likely required change is:

* add a dedicated automatic action type such as `AUTO_RESOLVE`

Alternative fallback:

* keep `RESOLVE` but reserve a system actor UUID and mandatory system comment.

Preferred choice:

* explicit action type is better because acceptance criteria require clear
  distinction between automated finding management and human-reviewed
  remediation.

### C. The action must remain evaluation-owned, not scheduler-owned

There is currently no dedicated maintenance scheduler or background job product
in this slice.

The safest orchestration point is the existing:

* `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations`

That route already owns deterministic maintenance recomputation.

Therefore Story `0058` should likely:

* extend evaluation to also reconcile eligible existing findings;
* keep automation local to evaluation runs;
* avoid adding timers, watchers, or background daemons in this slice.

### D. Automatic resolution should likely apply only to findings still in reversible operational states

The repository already distinguishes:

* `OPEN`
* `ACKNOWLEDGED`
* `DISMISSED`
* `RESOLVED`

The safest closure target set is likely:

* `OPEN`
* `ACKNOWLEDGED`

Automation should probably not overwrite:

* `DISMISSED`
  because that is an explicit human decision
* already `RESOLVED`
  because there is nothing to do

This preserves the governance boundary around deliberate human review outcomes.

## Likely Implementation Direction

The repository evidence supports the following bounded direction for Story
`0058`:

1. Introduce an explicit automatic maintenance action marker.
   Likely:
   * add `AUTO_RESOLVE` to `MaintenanceFindingActionType`

2. Define a deterministic system actor convention.
   Likely one of:
   * a reserved constant UUID in `contextmaintenance`
   * or a small explicit system-actor helper

3. Extend `MaintenanceEvaluationServiceImpl` so evaluation also reconciles
   existing eligible findings.

4. Restrict automatic resolution eligibility to deterministic families only:
   * `STALE_PROJECT_UNDERSTANDING`
   * `MISSING_PROJECTION_REFRESH`
   * `STALE_HUMAN_CONTEXT_INPUT`

5. Skip automatic handling for duplicate-debt families.

6. Record automatic closure in the same append-only action history surfaced by
   the read model.

7. Extend tests to cover:
   * successful automatic resolution when a condition clears;
   * no automation for non-eligible families;
   * no overwrite of dismissed findings;
   * explicit audit trail of the system action.

## Expected Affected Areas

### Backend

Likely files:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceFindingActionType.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceEvaluationServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImpl.java`
* possibly `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/dto/response/MaintenanceEvaluationResponse.java`
  if reconciliation counts are surfaced
* possibly `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/mapper/MaintenanceFindingMapper.java`
  only if response shape changes

Likely tests:

* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceEvaluationServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingControllerWebMvcTest.java`

### Frontend

Likely minimal frontend impact:

* `frontend/src/app/features/context-maintenance/maintenance-finding.models.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`
* tests only if the new automatic action type or auto-resolved history is
  surfaced in current views

This Story likely does not require major new UI.

### Documentation

Likely files:

* `docs/knowledge-model.md`
* `docs/ui-ux.md`

## Risks

* Auto-closing duplicate-debt findings would blur the human-review boundary and
  should be avoided.
* Using the existing `RESOLVE` action type for automatic actions would make
  audit semantics ambiguous.
* Overwriting `DISMISSED` findings during evaluation would violate explicit
  human intent.
* Expanding into scheduling or background automation would overshoot the scope
  dramatically.

## Conclusion

Story `0058` is feasible, but only if it stays extremely conservative.

The strongest repository-aligned implementation is:

* keep automation inside the existing evaluation pass;
* add explicit automatic audit semantics;
* automatically resolve only deterministic findings whose condition is proven
  cleared by the same authoritative evaluator;
* keep duplicate-debt and other semantically ambiguous cases strictly
  human-reviewed.

That delivers meaningful automation while preserving ADR-053’s safety
boundaries and the repository’s current governance model.
