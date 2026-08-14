# Story 0057 — Context Maintenance For Internal Human Context Inputs — Repository Analysis

## Workflow Status

* Story `0057` is registered in DevLog as `922a9ea0-26a9-45da-815f-3ce8f14d8f39`.
* DevLog repository-context preparation timed out:
  `DEVLOG_CONTEXT_ERROR: DevLog request timed out. Repository Analysis continues without DevLog.`
* A second direct DevLog retry completed with `HTTP 200`, but the projected
  `repositoryContext` remained unusable for primary evidence selection because:
  * `selectedCount = 0`
  * `evidence = []`
  * warnings include `REPOSITORY_CONTEXT_BUDGET_APPLIED`,
    `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED`, and
    `AGENT_PROJECTION_MINIMAL_EVIDENCE_COMPACTED`
* DevLog still returned useful non-evidence metadata:
  * one `ACTIVE` internal human context input currently exists;
  * the latest project freshness summary remains `STALE` for the GitHub source;
  * Story `0057` is visible in the engineering-story history as `REGISTERED`.
* Repository analysis therefore relies on direct repository inspection.

## Story Intent

Story `0057` extends the new context-maintenance capability to **internal human
context inputs** introduced by Story `0050`.

The core problem is not generic note management.

It is the narrower hygiene problem created by persistent human-authored context:

* an `ACTIVE` note can become stale;
* a note can become superseded by newer active context;
* archived notes should remain historical, not be treated as maintenance debt;
* active-but-still-useful notes must not be aggressively flagged just because
  they are old.

The first slice therefore needs a conservative detection policy, clear
separation from trusted knowledge, and explicit user review rather than silent
mutation.

## Relevant Repository Evidence

### 1. Human context exists as a narrow project-owned domain

Story `0050` already created a dedicated domain for internal human context:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/entity/ProjectHumanContextInput.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/entity/ProjectHumanContextInputType.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/entity/ProjectHumanContextInputStatus.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/service/ProjectHumanContextInputServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/controller/ProjectHumanContextInputController.java`
* `frontend/src/app/features/project-context-inputs/*`

The persisted model is currently:

* `title`
* `contentMarkdown`
* `type`
* `status`
* `createdAt`
* `updatedAt`

The lifecycle is intentionally minimal today:

* `ACTIVE`
* `ARCHIVED`

The service layer supports only:

* create
* list
* archive

There is no edit flow, no explicit supersession relation, no priority flag, and
no historical versioning.

### 2. Active human context is already projected into analysis and state

The current system already consumes only `ACTIVE` inputs in the main read
paths:

* `ProjectContextProviderImpl` loads `findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(..., ACTIVE)`
* `AnalysisContextServiceImpl` carries `projectContext.humanContextInputs()` into analysis context
* `ProjectStateProjectionServiceImpl` includes only `ACTIVE` human context in the project objective section

This is important because stale human context is not harmless: once it remains
`ACTIVE`, it is selected into downstream context surfaces.

The DevLog retry also confirmed that the current project actually has one
persisted active human-context note:

* type: `GOAL`
* title: `Medium-term objective`
* status: `ACTIVE`
* updated at: `2026-08-13T20:27:32.559552Z`

So Story `0057` is not designing an abstract future capability only; there is
already live human context in the system that can become hygiene debt if it
stops matching project reality.

### 3. ADR-052 explicitly requires lifecycle-aware human context

`docs/decisions/ADR-052.md` requires:

* human context to remain distinct from trusted knowledge;
* structured selection rather than an opaque note dump;
* lifecycle awareness so DevLog does not treat every note as permanently current.

The ADR explicitly names distinctions such as:

* active context
* obsolete context
* superseded context
* archived context

The current implementation only materializes `ACTIVE` vs `ARCHIVED`, so Story
`0057` is the first place where ADR-052’s richer lifecycle concern can be
expressed operationally through maintenance findings rather than by collapsing
everything into the note status enum itself.

### 4. ADR-053 explicitly says maintenance must span internal human context

`docs/decisions/ADR-053.md` establishes that context maintenance:

* is cross-surface;
* should be deterministic first where practical;
* may use AI only for ambiguity;
* must keep destructive or ambiguous changes under human control.

The ADR explicitly names **internal human context inputs** as one of the target
surfaces.

That makes Story `0057` a direct architectural continuation of ADR-053 rather
than a new side feature.

### 5. Current maintenance domain does not yet model human-context findings

The maintenance foundation from Stories `0052`–`0056` is still intentionally
narrow.

Current enums show the gap clearly:

* `MaintenanceContextSurface` only contains:
  * `PROJECT_UNDERSTANDING`
  * `PROJECT_PROJECTION`
* `MaintenanceFindingIssueType` only contains:
  * stale understanding / projection gaps
  * trusted-knowledge duplicate debt variants

`MaintenanceEvaluationServiceImpl` currently evaluates only:

* `ProjectFreshnessService`
* `TrustedKnowledgeDuplicateAuditService`

There is no repository access or evaluation branch for
`ProjectHumanContextInputRepository`.

The DevLog retry reinforces that asymmetry:

* freshness is already surfaced as a first-class maintenance signal;
* human context is already persisted and projected;
* but there is still no maintenance bridge between the two for the
  human-context surface itself.

### 6. Current review workflow is also bounded to duplicate debt

Story `0056` added explicit review actions for maintenance findings, but the
frontend gating remains limited to trusted-knowledge duplicate issues:

* `ProjectMaintenanceSection.supportsWorkflow(...)` whitelists only:
  * `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`
  * `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`
  * `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`

So even if backend evaluation created human-context findings today, the current
UI would display them but would not expose the review workflow required by the
Story scope.

## Architectural Implications

### A. Do not turn note status into the whole maintenance model

A tempting design would be to expand `ProjectHumanContextInputStatus` with
states like `STALE` or `SUPERSEDED`.

That would be the wrong first move.

Why:

* ADR-053 defines maintenance findings as first-class reviewable records.
* A note’s persisted lifecycle and a maintenance assessment are different
  concerns.
* `ARCHIVED` should remain a stable business state, not a mixed business +
  diagnostic taxonomy.
* A note can be `ACTIVE` and simultaneously be a maintenance concern pending
  review.

Therefore Story `0057` should likely preserve:

* human context input lifecycle state on the input itself;
* maintenance diagnosis in `maintenance_findings`.

### B. The first slice should stay deterministic and conservative

The repository does not currently provide:

* note version history
* explicit replacement links
* semantic ranking metadata
* human-authored “supersedes” relations

That makes a broad semantic supersession detector too speculative for this
slice.

The most robust V1 direction is therefore:

* detect a bounded stale/superseded class using deterministic rules;
* target only `ACTIVE` human context inputs;
* never flag `ARCHIVED` inputs as active maintenance debt;
* leave ambiguous semantic judgment for later stories.

### C. “Active but low-priority” should remain non-destructive in V1

Acceptance criterion `AC-2` does not require a new persisted low-priority
status.

The repository can already distinguish:

* archived historical context:
  * input status = `ARCHIVED`
* active current context:
  * input status = `ACTIVE` and no open maintenance finding
* maintenance-worthy active context:
  * input status = `ACTIVE` and one or more open human-context maintenance findings

That distinction is enough for the first slice if the finding summaries and UI
labels are explicit.

### D. Review support should reuse the existing finding workflow, not invent a second one

Story `0056` already established the review pattern:

* acknowledge
* dismiss with rationale
* resolve with rationale
* action history / audit trail

That pattern should likely be reused for human-context maintenance findings.

However, the system should not silently archive or rewrite notes from the
maintenance panel in this Story because:

* ADR-053 keeps ambiguous archival under human control;
* the current note feature has no edit/supersede workflow;
* the safest bounded path is to let users review a finding, then archive or
  replace the note through the dedicated context-input feature, and finally
  resolve the finding.

## Likely Implementation Direction

The repository evidence supports the following bounded direction for Story
`0057`:

1. Extend the maintenance domain with an explicit human-context surface.
   Likely:
   * add `INTERNAL_HUMAN_CONTEXT` or similarly explicit enum value to
     `MaintenanceContextSurface`

2. Add one bounded human-context issue family for V1.
   Likely one of:
   * `STALE_HUMAN_CONTEXT_INPUT`
   * `SUPERSEDED_HUMAN_CONTEXT_INPUT`

   The safest first implementation is stale-active-note detection with
   deterministic rules based on persisted note metadata rather than semantic AI
   judgment.

3. Extend `MaintenanceEvaluationServiceImpl` to inspect
   `ProjectHumanContextInputRepository`.
   The evaluator should:
   * read only project-owned human context inputs;
   * ignore archived inputs for open-finding generation;
   * create findings only for conservative deterministic cases;
   * deduplicate against equivalent open findings the same way the existing
     evaluator already does for other families.

4. Extend the maintenance API and frontend models for the new surface / issue
   types.

5. Extend `ProjectMaintenanceSection` so human-context findings also support the
   bounded review workflow from Story `0056`.

6. Update canonical documentation to explain:
   * internal human context is now part of context maintenance;
   * findings remain distinct from trusted knowledge;
   * maintenance review does not automatically archive or promote notes.

## Expected Affected Areas

### Backend

Likely files:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceContextSurface.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/entity/MaintenanceFindingIssueType.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceEvaluationServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/repository/ProjectHumanContextInputRepository.java`
* possibly `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/mapper/MaintenanceFindingMapper.java`
* possibly `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImpl.java`

Likely tests:

* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceEvaluationServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingControllerWebMvcTest.java`

### Frontend

Likely files:

* `frontend/src/app/features/context-maintenance/maintenance-finding.models.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.html`
* `frontend/src/app/features/context-maintenance/project-maintenance-section.spec.ts`

Possible companion touchpoint:

* `frontend/src/app/features/project-context-inputs/*` only if the Story chooses
  to improve note-state explanation or cross-link the review path

### Documentation

Likely files:

* `docs/knowledge-model.md`
* possibly `docs/ui-ux.md` if the human-context maintenance behavior is visible
  enough to deserve UI documentation

## Risks

* Over-detecting “stale” notes based on age alone could create noisy findings
  for still-valid long-lived goals or constraints.
* Expanding note statuses instead of using findings would blur domain state and
  maintenance diagnosis.
* Adding archive automation inside maintenance would overshoot the current trust
  and UX boundaries.
* Introducing semantic supersession without deterministic evidence would likely
  be brittle and hard to explain in tests.

## Conclusion

Story `0057` is feasible within the existing architecture, but it should remain
deliberately narrow.

The strongest repository-aligned approach is:

* keep human context inputs as their own domain;
* represent hygiene concerns as maintenance findings, not as overloaded note
  statuses;
* implement one conservative deterministic human-context detection family first;
* reuse the existing review/audit workflow from Story `0056`;
* avoid automatic archival or semantic mutation.

That direction satisfies ADR-052 and ADR-053 while keeping the first slice
small, explicit, and testable.
