# Story 0053 — Expose Context Maintenance Findings In API And Cockpit — Repository Analysis

## Purpose

Analyze how DevLog should expose the new maintenance-finding domain introduced
by Story `0052` through:

* a bounded project-scoped backend API;
* a first user-facing cockpit/workspace surface.

This Story is a **visibility slice**.

It is not:

* a detector Story;
* a remediation Story;
* a scheduler Story;
* a broad cockpit redesign.

## Repository Context

### Current Git state

Repository branch at analysis time:

* `main`

Observed local nuance:

* Story `0052` is merged into local `main` via fast-forward from `origin/main`;
* Story directories `0053` through `0059` remain local untracked planning
  inputs;
* local branch `feat/story-0052-context-maintenance-findings` was **not**
  deleted because `git branch -d` still considered it not fully merged, so
  cleanup was safely skipped rather than forced.

Impact:

* the repository already contains the new `contextmaintenance` backend domain;
* Story `0053` can plan directly against implemented `0052` code;
* no destructive cleanup was performed on an ambiguously merged branch.

### DevLog lifecycle

Story registration succeeded:

* DevLog story id: `afef0001-cfab-46a4-a8f9-8eff253fbe99`

DevLog repository-context preparation was retried after fixing
`AgentContextProjectionService` and now succeeds:

* `POST /api/projects/f3d56247-aada-4a76-982b-e6802c0b309c/engineering-story-context`
  returned `200`;
* the returned agent projection fits within the configured `32768`-byte budget;
* the projection was heavily compacted and ended with `evidenceCount = 0`,
  plus warnings such as:
  * `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED`
  * `AGENT_PROJECTION_PROFILE_DETAILS_REMOVED`

Additional context exposed by DevLog:

* project freshness is currently `STALE`;
* source review guidance is `REFRESH_RECOMMENDED`;
* the latest analyzed revision lags behind `origin/main`.

Impact:

* Repository Analysis no longer depends on the fallback
  `DEVLOG_CONTEXT_ERROR: DevLog returned HTTP 500.`;
* DevLog context is available again for the rest of Story `0053`;
* the compaction outcome confirms that future work on maintenance visibility
  should keep API payloads bounded and explainable.

### Vault context

Vault was not consulted for this analysis.

Reason:

* the repository already contains the decisive implementation from Story `0052`
  plus the frontend patterns needed for `0053`;
* the Story is a local read-path design problem, not a cross-project knowledge
  question.

## Story Understanding

Story `0053` asks DevLog to make context-maintenance findings visible and
operational.

The Story does **not** ask DevLog to create findings.

That was the job of Story `0052` for the model and Story `0054+` for future
producers.

The required value here is:

* a bounded read API for project-scoped maintenance findings;
* a first human-facing cockpit/workspace view;
* empty/sparse-state behavior that remains explainable;
* clear visual distinction between:
  * no findings;
  * informational findings;
  * findings requiring review.

The Story is therefore the first vertical read path on top of the maintenance
foundation.

## Business Ownership

The affected capability remains **internal context maintenance**, but the
delivery now spans two repository owners:

* Java Core owns the authoritative persisted findings and the read API;
* Angular frontend owns the first human-facing cockpit/workspace projection.

The AI Engine remains unaffected.

This ownership split matches existing repository structure:

* project-scoped CRUD/read APIs are exposed by Java controllers;
* workspace and cockpit read experiences are implemented in Angular feature
  modules.

## Relevant Existing Implementation

### `contextmaintenance/*`

Relevant files:

* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingService.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/dto/response/MaintenanceFindingResponse.java`
* `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/repository/MaintenanceFindingRepository.java`

Current behavior:

* findings can be created;
* findings can be listed by project;
* findings can change basic lifecycle status;
* no public controller exists.

Important implication:

Story `0053` does not need to invent the read model from scratch.

It can expose the existing response shape through a controller, while deciding
whether any additional read-only projection semantics are required for UX.

### `projectcontextinput/*` controller pattern

Relevant file:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/controller/ProjectHumanContextInputController.java`

Why it matters:

* it is a project-scoped controller using the exact route style Story `0053`
  likely needs;
* it already demonstrates list/create/archive under
  `/api/v1/projects/{projectId}/...`.

Important implication:

the natural API shape for maintenance findings is likely another project-scoped
controller under:

* `/api/v1/projects/{projectId}/maintenance-findings`

### Project cockpit and workspace surfaces

Relevant files:

* `frontend/src/app/features/projects/project-detail-page.html`
* `frontend/src/app/features/workspace/project-workspace-layout.html`
* `frontend/src/app/features/workspace/project-workspace-section-page.ts`
* `frontend/src/app/features/workspace/project-workspace-section-page.html`

Current behavior:

* the project cockpit already shows high-signal dashboard cards and “Pending
  work” guidance;
* the workspace already provides named sections:
  * cockpit
  * overview
  * timeline
  * activity
  * knowledge
  * documentation
  * settings

Important implication:

Story `0053` has two plausible UI landing zones:

1. add a compact maintenance module to the project cockpit;
2. add a more detailed maintenance section in the workspace.

The Story text asks for “a first maintenance view into the project cockpit”.

That is a strong signal that the cockpit should be the primary first exposure,
even if the implementation reuses workspace-style section components internally.

### Existing frontend list/mutation pattern

Relevant file:

* `frontend/src/app/features/project-context-inputs/project-context-inputs-section.ts`

Current behavior:

* Angular feature-local service + component pair;
* observable loading/error/loaded view models;
* explicit empty states;
* minimal mutation feedback.

Important implication:

the first maintenance UI should probably follow the same reactive pattern:

* dedicated models/service;
* simple loaded/error/empty states;
* no optimistic inference or generic health-score abstraction.

## Existing Architectural Rules

### Findings must remain non-authoritative

Story `0052`, ADR-053, and `docs/knowledge-model.md` now establish that
maintenance findings are:

* operational;
* reviewable;
* distinct from trusted knowledge;
* distinct from proposal history.

Implication for `0053`:

the API and UI must not present findings as if they were validated project
truth.

Examples to avoid:

* mixing findings into the trusted knowledge list;
* using trusted-knowledge wording or styling;
* presenting a single opaque “health score” with no explanation.

### Read path only

The Story explicitly excludes remediation actions.

Implication:

Story `0053` should expose only a bounded read API and passive UI presentation.

It should not add:

* resolve/dismiss actions in the cockpit;
* bulk review flows;
* background mutation triggers.

### API stability matters now

The Story explicitly says the API should be stable enough for later remediation
workflows.

Implication:

the backend response should be explicit and unsurprising now, even if later
Stories add more status transitions or more issue families.

## Likely Affected Components

### Backend

Likely files/packages:

* new controller under `contextmaintenance/controller/`
* possible small service expansion if filtering or ordering helpers are needed
* WebMvc tests for the new read endpoint

Likely API:

* `GET /api/v1/projects/{projectId}/maintenance-findings`

Possibly useful but not clearly required:

* filtered read variants by status

Assessment:

The Story can likely satisfy AC-1 with one bounded list endpoint returning the
existing maintenance response shape.

### Frontend

Likely files/packages:

* new feature package such as
  `frontend/src/app/features/context-maintenance/`
* API service + models
* cockpit component or section component
* unit/component tests

Likely integration point:

* `project-detail-page.html` for first cockpit visibility

Possible secondary integration:

* later workspace page, but that looks more like future refinement than the
  minimal first slice.

## Recommended UI Direction

### Preferred first landing zone: project cockpit

Reason:

* the Story wording explicitly says “cockpit”;
* the cockpit already hosts high-signal operational modules such as project
  freshness and engineering events;
* maintenance visibility belongs next to operational project health, not buried
  inside settings.

### Preferred presentation shape

A strong first slice would be:

* one dedicated maintenance card/section in the cockpit;
* small grouped list of current findings;
* clear labels for:
  * context surface
  * severity
  * status
  * suggested action
* empty state explaining that no maintenance findings currently exist
* explicit copy distinguishing informational findings from review-needed items

### What not to do

Avoid:

* a generic red/green score with no finding details;
* a broad new navigation section just for this Story;
* over-describing future remediation actions that do not yet exist.

## API Considerations

### Current response shape is likely reusable

`MaintenanceFindingResponse` already includes:

* project id
* surface
* issue type
* severity
* status
* suggested action
* review requirement
* summary/details
* timestamps

This already maps well to Story `0053` requirements.

Likely conclusion:

* no new backend projection DTO is necessary unless the current shape proves too
  coupled to internal naming or too verbose for stable public use.

### Compatibility

Because no maintenance API exists yet:

* adding a new endpoint is backward compatible;
* the main design risk is exposing an awkward response shape that future Stories
  have to work around.

## Tests

Likely required tests:

### Backend

* controller returns project-scoped findings
* empty project returns `200` with empty list
* invalid project id / missing project behavior matches repository conventions
* serialization includes the expected maintenance fields

### Frontend

* loading state renders
* empty state renders
* findings list renders bounded details correctly
* error state renders gracefully
* visual distinction between no findings / informational / review-needed is
  present in the rendered output

## Risks

### 1. UI lands in the wrong surface

If the first visibility slice is placed only in `settings`, the Story will
technically expose data but miss the “cockpit” and operational visibility
intent.

Recommended mitigation:

* make the cockpit the first-class landing zone.

### 2. API/UI imply authority the findings do not have

If wording or placement makes findings look like trusted knowledge, the
repository will weaken the distinction introduced in `0052`.

Recommended mitigation:

* use operational wording such as maintenance, review, refresh, investigate;
* do not place findings inside the knowledge surface.

### 3. Empty states feel like missing data rather than good health

If the empty state simply says “nothing loaded”, users cannot tell whether the
system is healthy or broken.

Recommended mitigation:

* explicit empty-copy explaining that no maintenance findings currently exist.

### 4. Story scope drifts into remediation

It will be tempting to add resolve/dismiss actions once findings are visible.

Recommended mitigation:

* keep this Story read-only;
* defer write-side review flows to the remediation Story.

## Conclusion

The repository is ready for Story `0053`.

The clearest implementation direction is:

1. expose `MaintenanceFindingService#getByProject(...)` through a new bounded
   controller under `/api/v1/projects/{projectId}/maintenance-findings`;
2. add a dedicated frontend feature/service for reading those findings;
3. surface the first maintenance view in the **project cockpit** as a compact,
   explainable operational section with strong empty/error states;
4. keep the entire slice read-only and clearly distinct from trusted knowledge
   and future remediation workflows.

Repository Analysis ready for human review.
