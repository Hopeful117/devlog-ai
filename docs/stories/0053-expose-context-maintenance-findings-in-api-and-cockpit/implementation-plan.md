# Story 0053 — Expose Context Maintenance Findings In API And Cockpit — Implementation Plan

## Overview

Implement Story `0053` as the **first vertical read slice** for the
maintenance-finding domain introduced by Story `0052`.

The goal is to make maintenance findings:

* retrievable through a bounded project-scoped API;
* visible in the project cockpit;
* clearly distinguishable from trusted knowledge and future remediation flows.

This Story remains intentionally read-only.

It should not add:

* detector logic;
* review / dismiss mutations in the UI;
* scheduler behavior;
* hidden “health score” abstractions.

## Final Implementation Strategy

The preferred implementation is:

1. expose the existing `MaintenanceFindingService#getByProject(...)` through a
   new bounded controller under `/api/v1/projects/{projectId}/maintenance-findings`;
2. reuse the existing `MaintenanceFindingResponse` DTO as the first stable API
   contract;
3. add focused WebMvc coverage for list, empty, and error behavior;
4. add a dedicated Angular maintenance feature with typed models and a simple
   read service;
5. render the first maintenance surface directly in the **project cockpit** as
   a compact operational section with explicit loading, empty, error, and
   review-needed states;
6. update canonical documentation to record the initial API and UX boundaries.

## Step 1 — Expose a project-scoped maintenance findings read endpoint

Targets:

* new controller under
  `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/`

Goals:

* satisfy AC-1 with a bounded public read path;
* align with existing project-scoped API routing conventions;
* keep the Story read-only.

Implementation direction:

* add `GET /api/v1/projects/{projectId}/maintenance-findings`;
* delegate directly to `MaintenanceFindingService#getByProject(projectId)`;
* return `200 OK` with a JSON array;
* preserve existing project-not-found semantics through the shared exception
  handler.

Recommended design choice:

* do not add filtering, pagination, or mutation endpoints in this Story unless
  the existing response shape proves impossible to consume cleanly.

Rationale:

The service and DTO already exist; the missing piece is a stable controller
surface.

## Step 2 — Keep the first API contract explicit and reusable

Targets:

* `MaintenanceFindingResponse`
* controller serialization tests

Goals:

* satisfy AC-1 and AC-6 without inventing a second projection prematurely;
* keep later remediation Stories compatible with the first read path;
* expose the fields the cockpit actually needs.

Implementation direction:

Use the current response shape as the first contract, including:

* `id`
* `projectId`
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

Important rule:

* treat this DTO as an operational maintenance record, not as trusted
  knowledge;
* keep enum names and property names explicit rather than collapsing them into
  a generic score or badge payload.

Rationale:

The current shape already matches the Story scope and avoids needless adapter
work.

## Step 3 — Add backend tests around routing, scoping, and serialization

Targets:

* new `MaintenanceFindingControllerWebMvcTest`
* existing `contextmaintenance` test package

Goals:

* satisfy AC-5 on the backend side;
* prove route stability and JSON shape;
* cover graceful empty-state behavior at the API boundary.

Implementation direction:

Add tests for:

* `GET /api/v1/projects/{projectId}/maintenance-findings` returns `200`;
* a populated project returns findings in the service-provided order;
* an empty project returns `[]`;
* a missing project returns the repository-standard error response;
* serialization includes review-relevant fields such as:
  * `contextSurface`
  * `severity`
  * `status`
  * `suggestedAction`
  * `humanReviewRequired`

Preferred pattern:

* mirror the style used by
  `ProjectHumanContextInputControllerWebMvcTest`.

## Step 4 — Add a dedicated Angular maintenance read feature

Targets:

* new feature package under
  `frontend/src/app/features/context-maintenance/`

Goals:

* isolate maintenance-specific API and presentation logic;
* avoid mixing maintenance concerns into project knowledge or context-input
  features;
* establish a reusable frontend surface for future remediation Stories.

Implementation direction:

Create:

* typed models for maintenance findings;
* a `MaintenanceFindingService` using
  `GET /api/v1/projects/{projectId}/maintenance-findings`;
* a dedicated cockpit component responsible for rendering the first view.

Recommended pattern:

* follow the existing feature-local service/component model used by
  `project-context-inputs` and `project-freshness`.

Important non-goal:

* do not introduce review mutations, optimistic state machines, or workspace
  navigation changes in this Story.

## Step 5 — Render the first maintenance surface in the project cockpit

Targets:

* `frontend/src/app/features/projects/project-detail-page.html`
* new maintenance section component and styles

Goals:

* satisfy AC-2, AC-3, and AC-4;
* make maintenance visible where operators already look for project status;
* keep the first slice compact and explainable.

Implementation direction:

Add a dedicated cockpit card/section that:

* loads findings for the current project;
* shows a concise list of active findings;
* renders high-signal fields:
  * context surface
  * severity
  * status
  * suggested action
  * summary
* distinguishes informational vs review-needed items explicitly;
* handles loading and request failures without collapsing the rest of the
  cockpit.

Preferred presentation behavior:

* empty state copy should say that no maintenance findings currently exist;
* review-needed items should be visually more prominent than informational
  items;
* blocked or human-review states should be described in operational wording,
  not as validated truth.

Important non-goal:

* do not hide findings behind Settings or create a broad new maintenance page
  for this first slice.

## Step 6 — Keep the UX semantics operational, not authoritative

Targets:

* maintenance component copy and styling
* any helper formatting logic

Goals:

* preserve the distinction introduced in Story `0052`;
* avoid implying that findings are canonical knowledge;
* make no-findings vs review-needed states immediately understandable.

Implementation direction:

Use wording along these lines:

* maintenance
* investigate
* review
* refresh recommended
* no active maintenance findings

Avoid wording or placement that suggests:

* trusted knowledge;
* project truth;
* resolved system health through a single score.

Rationale:

This Story is about visibility and operator guidance, not knowledge validation.

## Step 7 — Add focused frontend rendering tests

Targets:

* new maintenance section component spec
* maintenance service spec if needed

Goals:

* satisfy AC-5 on the frontend side;
* lock the first UX semantics in place;
* reduce regression risk around sparse states.

Implementation direction:

Add tests for:

* loading state;
* empty state with explicit “no findings” wording;
* rendered list with informational and review-needed findings;
* graceful error state;
* mapping of enum-backed fields into readable UI labels.

Recommended scope:

* keep tests at component/service level rather than broad end-to-end flows.

## Step 8 — Update canonical documentation for the first API and cockpit slice

Targets:

* the most relevant existing canonical documentation file(s)
* Story-local implementation/report artifacts later in the workflow

Goals:

* satisfy AC-6;
* document the new public read endpoint and cockpit boundary;
* make clear that remediation remains out of scope.

Implementation direction:

Document:

* the existence of the maintenance findings read API;
* the fact that the cockpit exposes a first read-only operational view;
* the non-authoritative nature of findings;
* the deferred nature of review / remediation workflows.

Important rule:

* update only the canonical docs directly affected by this new surface;
* avoid speculative documentation for future maintenance automation.

## Validation Plan

Backend validation:

* targeted WebMvc tests for the new controller;
* targeted service tests if small API-shaping helpers are introduced.

Frontend validation:

* maintenance service spec;
* maintenance section component spec;
* targeted project cockpit integration test if the section is embedded directly
  in `project-detail-page`.

Manual verification:

* `GET /api/v1/projects/{projectId}/maintenance-findings` returns the expected
  payload for both empty and populated cases;
* the project cockpit renders:
  * loading state
  * empty state
  * findings state
  * error state

## Out Of Scope Confirmation

This Implementation Plan intentionally excludes:

* automatic finding generation;
* review / dismiss write flows;
* background jobs;
* workspace-wide maintenance navigation redesign;
* vault mutation or trusted-knowledge integration.

Implementation Plan ready for human review.
