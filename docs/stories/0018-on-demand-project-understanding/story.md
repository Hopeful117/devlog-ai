# Story 0018 — On-Demand Project Understanding

## Story ID
0018

## Title
Allow users to initialize or refresh project understanding on demand

## Status
Completed

## Priority
High

## Date
2026-08-09

---

## User Story

As a developer using the Project Workspace,
I want to launch project-understanding analysis whenever I need it,
So that I can initialize DevLog's understanding of a newly connected project or refresh that
understanding after meaningful repository changes without depending on a one-time automatic event.

---

## Context

DevLog already provides the main technical capabilities required to understand a project:

* project and Git Source management;
* repository synchronization at a branch, tag, or commit;
* deterministic collection of repository Facts and Observations;
* commit-history and bounded commit-diff evidence;
* immutable Project Profile and AnalysisContext snapshots;
* layered, ranked, traceable Repository Context;
* the `describe-project-v1` Intent;
* asynchronous AI execution and structured Insight Proposals;
* explicit human validation before promotion to Trusted Knowledge.

The current Workspace exposes generic analysis creation and launch controls. However, it does not
offer one clear product-level action whose purpose is to build or refresh the user's understanding
of the current project. Users must know which internal Intent and workflow steps to choose.

The V1 roadmap still lists repository bootstrap analysis as unfinished. Bootstrap is only the first
execution of a longer-lived capability: after architectural changes, dependency upgrades, major
features, migrations, or a period away from the project, the user must be able to request a fresh
understanding again.

This Story therefore introduces an on-demand Project Understanding capability. The first successful
execution initializes understanding; later executions refresh it. The action remains explicitly
user-triggered and does not introduce passive monitoring or autonomous scheduling.

---

## Objective

Provide one user-oriented, on-demand workflow that prepares the selected repository state and
launches a traceable `describe-project-v1` analysis without requiring the user to understand
internal Analysis, Intent, context-profile, or AI-task mechanics.

Every execution must preserve its exact source/revision, deterministic context, analysis lifecycle,
AI provenance, proposals, and human-validation boundary. Repeated requests must be safe and must
avoid creating uncontrolled equivalent work.

Repository Analysis and Implementation Planning must determine which existing synchronization,
history-import, analysis creation, launch, diagnostics, and navigation services can be composed
without duplicating their ownership or bypassing their contracts.

---

## Acceptance Criteria

### AC-1: Project understanding is available on demand

The Project Workspace exposes a clear `Understand project` or equivalent product-oriented action.
It remains available after the first execution so the user can intentionally refresh understanding
whenever project context has materially changed.

The UI must not describe the action as a one-time setup operation.

### AC-2: Preconditions are explicit

Before launch, the workflow verifies that:

* the project exists;
* at least one active compatible Git Source is available;
* the requested source belongs to the project;
* the requested branch, tag, commit, or default revision is valid under existing Source contracts;
* the `describe-project-v1` Intent and its context profiles remain registered.

Missing or invalid preconditions produce actionable standard errors and do not create a partial
Analysis.

### AC-3: Source and revision selection are deliberate

When one active source exists, the workflow may select it by default while keeping the selection
visible. When several active sources exist, the user must be able to select the intended source or
the workflow must use an explicitly documented multi-source policy.

The user may target the source's default revision or provide a supported branch, tag, or commit.
The requested and resolved revisions must remain traceable in the resulting Analysis and repository
evidence.

Repository Analysis may refine the smallest V1 interaction, but it must not silently analyze an
unrelated source or working tree.

### AC-4: Existing deterministic preparation is reused

The workflow composes existing repository synchronization, deterministic collection, history,
Project Profile, AnalysisContext, Repository Context, and analysis lifecycle capabilities where
applicable.

It must not introduce a second repository scanner, directly assemble prompts in the frontend,
allow the AI Engine to read repositories, or bypass the Core's context and knowledge-selection
boundaries.

### AC-5: The canonical understanding Intent is used

Every on-demand Project Understanding execution uses the registered `describe-project-v1` Intent
through the normal Intent Catalog and analysis workflow.

The public product action must not hardcode a duplicate prompt, output contract, allowed Insight
types, or context profile in the frontend.

### AC-6: Execution is traceable and observable

The workflow exposes a durable Analysis with the existing lifecycle and retains at least:

* project and source identity;
* requested and resolved revision;
* Intent identifier and version;
* User Guidance snapshot when supplied;
* deterministic diagnostics and warnings;
* selected knowledge/context versions and digests;
* AI task correlation, provider, model, prompt version, and digest;
* proposals and subsequent validation outcomes.

The UI exposes meaningful pending, running, completed, failed, and unavailable states without
requiring the user to interpret internal orchestration objects.

### AC-7: Repeated execution is safe

The user may intentionally launch understanding more than once. Each materially distinct execution
creates a traceable historical Analysis.

The workflow must prevent accidental duplicate submission and define deterministic behavior when
an equivalent understanding analysis for the same project, source, requested revision, Intent
version, and guidance is already pending or running. It must reuse, reject, or navigate to that
execution rather than create uncontrolled concurrent duplicates.

A later execution after relevant repository evolution must remain possible.

### AC-8: First execution and refresh share one capability

The first successful execution may be presented as initialization and later executions as refreshes,
but both use the same application service, API contract, validation rules, analysis lifecycle, and
UI action.

No permanent project flag may make future refresh unavailable.

### AC-9: Human validation remains mandatory

Project Understanding may produce Insight Proposals through the existing AI workflow. It must never
promote those proposals automatically into Trusted Knowledge.

The result experience guides the user to inspect evidence, review proposals, and explicitly accept
or reject them through the current validation workflow.

### AC-10: Failures preserve trustworthy state

A synchronization, collection, context, AI, or output-contract failure must:

* preserve earlier successful analyses and Trusted Knowledge;
* retain a traceable failed execution when work has begun;
* avoid incomplete proposal promotion;
* expose the existing diagnostics, correlation, and retry-relevant information;
* allow the user to launch a later corrected execution.

### AC-11: The frontend communicates outcomes, not internals

The Project Workspace explains the action in terms of initializing or refreshing project
understanding. Internal concepts such as context profiles, AI Tasks, PromptRequest, or collectors
may remain available in diagnostics but must not dominate the primary interaction.

The action and status experience must be responsive, keyboard accessible, and expose asynchronous
feedback to assistive technologies.

### AC-12: Backend coverage is mandatory

Focused tests must cover at least:

* first on-demand execution;
* later refresh execution;
* no active source;
* source/project ownership mismatch;
* default and explicit revision behavior;
* registered `describe-project-v1` resolution;
* equivalent pending/running duplicate behavior;
* allowed later execution after completion or repository change;
* preparation failure before Analysis creation where atomicity requires it;
* workflow failure after Analysis creation with preserved traceability;
* unchanged human-validation boundary.

### AC-13: Frontend coverage is mandatory

Focused tests must cover at least:

* action availability before and after an earlier analysis;
* source/revision form behavior;
* precondition and validation feedback;
* disabled duplicate submission;
* pending/running/completed/failed states;
* navigation to a newly created or reused Analysis;
* retry after failure;
* accessible labels and status/alert feedback.

### AC-14: Existing workflows remain compatible

Generic analysis creation/launch, project CRUD, source management, repository synchronization,
history import, diagnostics, proposals, validation, Trusted Knowledge, Deliverables, Engineering
Story Context, Docker/runtime contracts, and standard API errors remain compatible.

The new product action may compose existing services but must not silently change the semantics of
unrelated Intents.

### AC-15: Documentation and quality are reconciled

Canonical product, API, architecture, and roadmap documentation must describe the implemented
on-demand capability, its first-run/refresh semantics, source/revision selection, duplicate policy,
failure behavior, and human-validation boundary.

Run focused and complete backend/frontend validation, JaCoCo, authenticated SonarQube with Quality
Gate wait, and local Docker/API/UI validation. Completion requires a passing Quality Gate and no new
unresolved issue attributable to the Story.

---

## Out of Scope

* Passive repository monitoring or scheduled refresh.
* Webhooks, filesystem watchers, polling, or automatic execution after every commit.
* The durable `AgentJob` orchestrator, transactional outbox, or message broker.
* Automatic promotion of proposals into Trusted Knowledge.
* Comparing two understanding analyses or generating a semantic change report.
* Dependency graphs, call graphs, source-test relationships, or symbol solving unless an existing
  capability already supplies them.
* New AI providers, prompts, Intent types, or context profiles.
* Replacing generic analysis creation for advanced users.
* Multi-project or bulk understanding execution.

---

## Architectural Constraints

* Java Core remains authoritative for project/source ownership, revision validation, orchestration,
  deterministic context, analysis state, and proposal lifecycle.
* The Intent Catalog remains authoritative for `describe-project-v1` semantics.
* The AI Engine consumes prepared context and never scans repositories or owns trusted state.
* Angular calls one typed Core application contract and never builds prompts or context profiles.
* The current repository/revision remains the implementation evidence source of truth.
* Existing Analysis, AI Task, Proposal, Validation, Insight, and diagnostic records remain the
  durable traceability model.
* Human validation remains the only promotion path to Trusted Knowledge.
* Equivalent in-flight execution policy must be deterministic and concurrency-safe.

---

## Risks Requiring Repository Analysis

* Existing source synchronization and history import may not currently be composed by one
  application service.
* Analysis creation and launch are separate calls and may expose partial state if orchestration
  fails between them.
* Current duplicate prevention may operate only per HTTP submission, not across concurrent clients.
* Source selection may be implicit in AnalysisContext and incompatible with a single-source UI
  assumption.
* A requested revision may be recorded on Analysis while different active sources resolve
  independently.
* Reusing generic analysis components may leak internal terminology into the product action.
* A broad bootstrap coordinator could duplicate the future Agent Job Orchestrator prematurely.

---

## Expected Deliverables

* Human-approved Repository Analysis.
* Human-approved Implementation Plan.
* One Core application contract for on-demand Project Understanding.
* Deterministic precondition, revision, and in-flight duplicate policy.
* Reuse of the canonical `describe-project-v1` workflow.
* User-oriented Workspace action and observable execution states.
* Focused backend/frontend tests plus complete quality validation.
* Canonical documentation reconciliation.
* Independent Code Review Report.
* Final Engineering Report after human Code Review approval.
