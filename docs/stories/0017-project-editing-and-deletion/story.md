# Story 0017 — Project Editing and Deletion

## Story ID
0017

## Title
Allow users to edit and permanently delete projects

## Status
Draft

## Priority
High

## Date
2026-08-09

---

## User Story

As a DevLog user managing my project workspaces,
I want to edit a project's user-maintained information and permanently delete a project I no
longer need,
So that the project catalogue remains accurate and under my control without requiring direct API
or database operations.

---

## Context

DevLog already supports project creation, listing, detail retrieval, backend updates through
`PUT /api/v1/projects/{slug}`, and archival through a dedicated status endpoint. The Angular
application currently exposes creation and read-only project views, but it does not expose the
existing update capability. Neither the backend nor the frontend currently supports permanent
project deletion.

A project owns or is referenced by substantial durable data, including sources, analyses,
diagnostics, knowledge, insights, decisions, milestones, proposals, artifacts, documentation,
deliverables, profiles, and Git history. Permanent deletion is therefore not a simple UI action:
its ownership, referential-integrity, transaction, failure, and confirmation behavior must be
explicit and tested.

Archival remains the reversible lifecycle action. In this Story, deletion means an intentional,
irreversible removal of the project and the data whose lifecycle is owned by that project.

---

## Objective

Complete the user-facing project CRUD workflow by exposing project editing and safe permanent
deletion through the existing Angular and Spring Boot boundaries.

Editing must preserve stable project identity and navigation. Deletion must require explicit user
confirmation, execute atomically, preserve truthful API errors, and leave no project-owned orphan
data or stale UI state.

Repository Analysis must inventory every project relationship and determine the smallest coherent
database/JPA deletion policy before Implementation Planning. It must not assume that every current
foreign key already cascades safely.

---

## Acceptance Criteria

### AC-1: Users can open an edit interaction from the project UI

The project detail experience exposes a clear, accessible action for editing the current project.
The form is initialized with the current project name and description and distinguishes loading,
submitting, success, validation-error, and request-error states.

### AC-2: Editable fields and validation are consistent across frontend and backend

Users can update the project name and description within the backend's supported constraints.
Required-field, blank-value, and length behavior must be explicit and consistent between the form,
request contract, Bean Validation, and persistence constraints.

Project status remains governed by the existing lifecycle endpoint and is not silently folded into
the general edit operation.

### AC-3: Project identity and routes remain stable after editing

Editing a project name must not unexpectedly change its slug, UUID, workspace ownership, or route.
After a successful update, the UI displays the persisted response without requiring a full browser
reload.

If Repository Analysis finds that the current backend update semantics do not safely enforce name
uniqueness or validation, the implementation must correct them within this Story and return the
standard API error contract.

### AC-4: The update API is exposed through the Angular service

The frontend project service provides a typed update operation using the existing
`PUT /api/v1/projects/{slug}` contract. Service and component tests verify the encoded identifier,
request body, response handling, and error behavior.

### AC-5: Permanent deletion has an explicit backend contract

The backend exposes `DELETE /api/v1/projects/{slug}`. A successful deletion returns `204 No
Content`. An unknown project returns the repository's standard not-found error contract, and
validation or integrity failures use the standard API error representation rather than leaking
database details.

### AC-6: Deletion is atomic and removes project-owned data coherently

Permanent deletion executes within one transaction. Repository Analysis must enumerate all direct
and indirect project-owned records and define a single maintainable deletion strategy using
database cascades, explicit ordered deletion, JPA ownership, or a justified combination.

On success, the project and all data whose lifecycle is owned exclusively by it are removed, no
orphan remains, and no data belonging to another project is affected. On failure, partial deletion
must not be committed.

### AC-7: Referential integrity is migration-safe

Any foreign-key or cascade change is delivered through an additive Flyway migration compatible
with existing databases. The migration must preserve current data and name constraints explicitly.
Fresh-schema and migrated-schema behavior must agree.

### AC-8: The UI makes destructive intent unmistakable

The project detail experience exposes deletion in a visually and semantically destructive area,
separate from ordinary editing and archival. Before sending the request, the user must confirm the
specific project being deleted through a deliberate confirmation interaction; a single accidental
click must not delete data.

The confirmation explains that deletion is permanent and includes project-owned data. Cancelling
leaves all state unchanged. While deletion is running, duplicate submissions are prevented.

### AC-9: Successful deletion leaves no stale navigation state

After deletion, the user is returned to the projects list and the deleted project no longer
appears. Direct access to its former URL returns the normal project-not-found experience. Failed
deletion keeps the user on the project and presents an actionable error without pretending the
project was removed.

### AC-10: Accessibility and interaction behavior are verified

Edit and delete controls are keyboard accessible, have unambiguous accessible names, preserve
useful focus behavior, and expose asynchronous success or failure feedback to assistive
technologies. Confirmation behavior must not rely on color alone.

### AC-11: Backend tests cover update and deletion boundaries

Focused tests must cover at least:

* successful name and description updates;
* stable slug and UUID after editing;
* blank, oversized, duplicate, and unknown-project update cases;
* successful deletion and `204` response;
* unknown-project deletion;
* deletion of a project with representative dependent records from every ownership branch;
* isolation of records belonging to another project;
* rollback or integrity-failure behavior;
* API error serialization.

### AC-12: Frontend tests cover the complete user workflow

Focused Angular tests must cover at least:

* pre-populated edit state;
* client-side validation and disabled duplicate submission;
* successful update rendering;
* update failure rendering;
* delete confirmation, cancellation, and submission;
* successful navigation after deletion;
* deletion failure without navigation;
* encoded project identifiers in HTTP requests.

### AC-13: Existing project workflows remain compatible

Project creation, listing, retrieval, archival, project workspace routes, source management,
analysis, knowledge, deliverable, profile, history, and Engineering Story Context behavior must
remain compatible for projects that are not deleted.

### AC-14: Documentation is reconciled

Canonical API and user-facing documentation must describe project editing, stable slug behavior,
permanent deletion, confirmation, cascade semantics, and error behavior where those contracts are
currently documented. Planned capabilities must not be presented as implemented until validation
is complete.

### AC-15: Quality baseline remains healthy

Run focused backend and frontend tests, the complete backend and frontend validation suites,
JaCoCo verification, authenticated SonarQube analysis with Quality Gate wait when configured, and a
local Docker/API/UI validation appropriate to the changed behavior.

Completion requires a passing Quality Gate and no new unresolved issue attributable to the Story.

---

## Out of Scope

* Restoring a permanently deleted project.
* Soft deletion or a recycle bin; archival remains the reversible action.
* Bulk project editing or deletion.
* Editing the project UUID, slug, creation timestamp, or internal ownership metadata.
* Folding archive, pause, or restore transitions into the general edit form.
* Deleting external Git repositories, filesystem workspaces, remote provider data, or files outside
  DevLog's own persistence boundary.
* General redesign of the project cockpit or global navigation.
* Authentication, authorization, roles, or multi-user ownership unless an existing repository
  contract already requires enforcement.

---

## Architectural Constraints

* Spring Boot remains authoritative for validation, transactional behavior, and deletion outcome.
* PostgreSQL foreign keys remain authoritative for referential integrity.
* Flyway remains the only mechanism for persistent schema evolution.
* The Angular application consumes typed API contracts and must not infer deletion success before
  receiving it from the backend.
* Existing standard API error handling remains the public failure contract.
* Project UUID and slug remain stable identifiers during ordinary editing.
* Deletion must be scoped to one resolved project and must never use an unbounded cleanup query.
* External repository content is not owned by the project row and must not be deleted.

---

## Risks Requiring Repository Analysis

* Existing project foreign keys use inconsistent cascade behavior and may block or partially shape
  deletion.
* Indirect ownership chains may contain records not visible from the `Project` entity.
* Database cascades and JPA cascades may conflict or produce surprising flush behavior.
* Updating a name may violate its unique database constraint without a project-specific API error.
* A stale route or concurrent delete can make edit/delete outcomes ambiguous.
* An insufficient confirmation interaction can make irreversible data loss too easy.
* Frontend project-detail loading currently combines several independently failing resources and
  may need careful state refresh after an update.

---

## Expected Deliverables

* Human-approved Repository Analysis.
* Human-approved Implementation Plan.
* Backend edit-contract corrections where repository evidence requires them.
* Transactional project deletion API and persistence strategy.
* Flyway migration when referential-integrity changes are required.
* Typed Angular update/delete service operations.
* Accessible project edit and delete interactions.
* Focused backend, migration, API, frontend service, and component tests.
* Documentation reconciliation and validation evidence.
* Independent Code Review Report.
* Final Engineering Report after human Code Review approval.
