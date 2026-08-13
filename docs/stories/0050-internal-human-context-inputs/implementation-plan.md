# Story 0050 — Internal Human Context Inputs — Implementation Plan

## Status

Completed

## Final Implementation Strategy

Story 0050 should deliver a real first slice of internal human-authored project
context, not just a storage experiment.

The implementation therefore must cover the full vertical path:

* project-owned persistence
* user-facing CRUD
* authoritative context propagation
* bounded AI-facing selection

At the same time, the first slice should remain intentionally narrow.

The plan below keeps the scope to:

* one dedicated backend entity;
* a minimal type and status model;
* basic create/list/archive behavior in the workspace;
* bounded inclusion in project-understanding context surfaces.

It deliberately excludes richer versioning, advanced ranking, and a dedicated
workspace navigation redesign.

## Step 1 — Introduce a dedicated backend domain for human context inputs

Targets:

* new package under
  `backend/src/main/java/com/hopeful117/devlogai/projectcontextinput/`
* database migration under `backend/src/main/resources/db/migration/`

Goals:

* model internal human context as a project-owned DevLog entity;
* keep it distinct from `Insight`, `Decision`, `KnowledgeEvent`, and `Source`;
* support minimal semantics required by ADR-052.

Implementation direction:

* create an entity with fields along these lines:
  * `id`
  * `project`
  * `title`
  * `contentMarkdown`
  * `type`
  * `status`
  * `createdAt`
  * `updatedAt`
* introduce a small enum set for the first slice:
  * types:
    * `GOAL`
    * `CONSTRAINT`
    * `ASSUMPTION`
    * `KNOWN_GAP`
    * `DOMAIN_CONTEXT`
  * statuses:
    * `ACTIVE`
    * `ARCHIVED`
* create repository methods for:
  * project-scoped list
  * active-only retrieval
  * stable ordering by update/create time

Rationale:

This preserves explicit semantics and avoids overloading `Source` with a
workflow it was not designed to represent.

## Step 2 — Add minimal project-scoped CRUD API and service behavior

Targets:

* controller, service, repository, mapper, request/response DTOs
* WebMvc and service tests

Goals:

* allow the user to create internal human context inputs;
* list project inputs;
* archive an existing input;
* keep project scoping explicit and safe.

Implementation direction:

* expose endpoints under a project-scoped route, for example:
  * `GET /api/v1/projects/{projectId}/context-inputs`
  * `POST /api/v1/projects/{projectId}/context-inputs`
  * `PATCH /api/v1/projects/{projectId}/context-inputs/{inputId}/archive`
* validate:
  * non-blank title
  * non-blank markdown content
  * required type
* keep the first slice intentionally simple:
  * no edit endpoint yet unless implementation ergonomics strongly justify it
  * no delete endpoint
* map responses clearly as human-authored project context, not as knowledge

Rationale:

List/create/archive is enough to establish durable usage while keeping lifecycle
handling minimal and safe.

## Step 3 — Extend authoritative analysis context with human context snapshots

Targets:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContext.java`
* project-context assembly services/providers and related tests

Goals:

* make active human context part of the authoritative analysis context;
* preserve explicit separation from evidence and trusted knowledge.

Implementation direction:

* add a dedicated snapshot collection to `AnalysisContext`, for example
  `humanContextInputs`
* define a compact snapshot shape containing only analysis-relevant fields such
  as:
  * `id`
  * `type`
  * `title`
  * `contentMarkdown`
  * `updatedAt`
* fetch only active inputs for context propagation
* preserve empty-list defaults in legacy constructors to avoid breaking
  unrelated code paths

Rationale:

If the data is not present in `AnalysisContext`, then the feature has not yet
become part of DevLog’s real reasoning surface.

## Step 4 — Include active human context in bounded selected knowledge

Targets:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`
* related backend and AI-facing tests

Goals:

* ensure active human context is visible in the AI-facing selected-knowledge
  payload;
* keep the inclusion bounded and stable;
* preserve semantic labeling as declarative human context.

Implementation direction:

* add a dedicated `SelectedKnowledge` snapshot list for human context inputs
* in `KnowledgeSelectionServiceImpl`, select only active inputs from
  `AnalysisContext`
* apply a small hard cap and stable ordering for the first slice
  rather than inventing a ranking engine
* project the new section explicitly in the AI-facing prompt projection under a
  clear name

Recommended first-slice policy:

* include up to a small number of active inputs, ordered by most recently
  updated first, then stable ID
* expose type and timestamps so consumers can distinguish current goals from
  other context shapes

Rationale:

This achieves the core product value while respecting the same bounded-context
discipline already applied elsewhere in DevLog.

## Step 5 — Add a minimal user-facing workspace flow

Targets:

* new frontend feature under `frontend/src/app/features/project-context-inputs/`
* integration inside
  `frontend/src/app/features/workspace/project-workspace-section-page.html`
  and related files

Goals:

* make the capability discoverable and usable for a human project owner;
* avoid a broad navigation redesign for the first slice.

Implementation direction:

* create a small project context inputs section component similar in style to
  existing project-scoped management panels
* place it under the `settings` workspace section
* provide:
  * list of existing inputs
  * create form with title, type, markdown content
  * archive action for active items
* keep UI terminology human-facing, for example:
  * “Project Notes”
  * or “Human Context”

Preferred first-slice UX:

* simple panel with explanatory copy
* compact typed badges
* a create form that supports the seed note use case directly

Rationale:

This is enough to establish real product value without prematurely deciding the
final workspace information architecture.

## Step 6 — Seed the agreed project objective through the new capability

Targets:

* implementation path and report, not a hardcoded seed migration

Goals:

* prove the feature can carry the real use case that motivated ADR-052;
* avoid fake or synthetic-only validation.

Implementation direction:

* once the CRUD path exists, create the first note through the implemented
  system during verification or final validation
* store the agreed medium-term objective as a `GOAL` input

Rationale:

This turns the Story from a generic framework slice into a directly useful
capability.

## Step 7 — Reconcile canonical repository documentation

Targets:

* `docs/knowledge-model.md`
* potentially `docs/architecture.md`
* implementation report documentation outcome

Goals:

* reflect that DevLog now has an internal human-context input capability;
* keep the distinction between repository evidence, human context, and trusted
  knowledge explicit in the canonical docs.

Implementation direction:

* update only the relevant sections if implementation introduces durable
  repository-level behavior
* record explicitly when a document does not require change

## Step 8 — Validate end-to-end with focused tests

Validation targets:

* backend entity/repository/service/controller tests
* context propagation tests
* selected-knowledge / prompt projection tests
* frontend component/service tests
* targeted UI workflow verification
* formatting and diff checks

Minimum command set:

* backend targeted Maven tests covering the new domain and context wiring
* targeted frontend tests for the new section
* `npm run lint`
* `npm run format:check`
* `git diff --check`

If the local stack is used for manual verification:

* rebuild/restart the relevant containers
* create the first note through the UI or API
* verify that the stored note appears in the project-scoped read surface and
  in the selected-knowledge payload for the relevant analysis path

## Explicit Deferrals

The following concerns are intentionally deferred beyond Story 0050:

* full revision history / note versioning
* edit workflows beyond what is strictly necessary for the first slice
* semantic ranking or retrieval across many notes
* rich markdown rendering policies
* dedicated workspace tab or broader navigation redesign
* automatic derivation of proposals from note changes alone

## Final Recommendation

Implement Story 0050 as a compact but complete vertical slice:

* new dedicated backend entity
* list/create/archive API
* minimal workspace UI under settings
* active-input propagation into `AnalysisContext`
* bounded inclusion into `SelectedKnowledge`
* real-use-case validation with the agreed medium-term project objective

This plan is small enough to execute safely and complete enough to prove the
architectural value of ADR-052.
