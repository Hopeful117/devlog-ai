# Engineering Report — Story 0018

## Story

Story 0018 — On-Demand Project Understanding adds an explicit Project Cockpit action that lets a
user initialize or refresh DevLog's understanding of a selected Git Source whenever needed.

## Objective

Provide one user-oriented workflow that prepares a selected repository revision, launches the
canonical `describe-project-v1` analysis, preserves complete provenance, safely reuses equivalent
in-flight work, and retains mandatory human validation of generated proposals.

## Repository Analysis Summary

Repository Analysis established that DevLog already had the required synchronization, history,
deterministic collection, Profile, AnalysisContext, Repository Context, AI workflow, proposal, and
validation capabilities. The missing piece was a coherent product-level entry point. Generic
Analysis controls required users to understand internal Intent and workflow mechanics, and the
roadmap's bootstrap wording implied a one-time event rather than a reusable capability.

The approved direction composed existing Core services, kept the repository as the source of
truth, selected one active Git Source deliberately, and treated first initialization and later
refreshes as the same operation.

## Implementation Plan Summary

The approved plan introduced:

* a dedicated Project Understanding API and Angular service;
* canonical `describe-project-v1` resolution inside the Core;
* deterministic equivalence keys and database-enforced active-execution uniqueness;
* immutable Source snapshots plus a nullable relational Source link;
* preparation before a short transactional claim/reuse boundary;
* selected-Source collection without changing generic Analysis behavior;
* an always-available, accessible Cockpit interaction;
* PostgreSQL concurrency coverage, complete quality validation, documentation reconciliation, and
  non-destructive live validation.

## Implementation Summary

The backend now exposes
`POST /api/v1/projects/{projectId}/understanding-executions`. It validates the project and selected
active Git Source, resolves the canonical Intent, synchronizes the requested revision, imports
history using the synchronized workspace, and claims or reuses a product Analysis before starting
the normal workflow.

Flyway V31 adds selected Source identity, an immutable JSON snapshot, and a normalized execution
key to product Analyses. A partial unique index permits at most one equivalent `PENDING` or
`IN_PROGRESS` execution while allowing later refreshes after completion or failure. Source deletion
clears the relational link but retains historical provenance.

The Angular Project Cockpit now displays `Understand project` before a canonical execution and
`Refresh understanding` afterward. It keeps the capability available, exposes Source and optional
revision selection, prevents duplicate submissions, reports asynchronous states accessibly, and
navigates to either the created or reused Analysis. Generic Analysis controls remain available.

## Architecture Impact

The implementation preserves established ownership:

* Java Core owns validation, canonical Intent resolution, revision preparation, orchestration, and
  lifecycle transitions;
* PostgreSQL owns active-execution uniqueness;
* existing deterministic context and AI workflow services remain authoritative;
* Angular owns deliberate user input and outcome presentation, not prompt construction;
* generated proposals remain behind explicit human validation;
* generic Analyses retain their existing all-active-source behavior.

No ADR was required. No scheduler, webhook, message broker, durable AgentJob, second repository
scanner, or autonomous monitoring behavior was introduced.

## Documentation Reconciliation

Documentation update: Completed.

* `README.md` documents the action, API, reuse outcome, failure behavior, and validation boundary.
* `docs/ui-ux.md` documents the always-available first-run/refresh interaction.
* `docs/architecture.md` distinguishes explicit understanding refresh from future passive
  monitoring.
* `docs/roadmap.md` records bootstrap/on-demand understanding as implemented while retaining future
  monitoring and evolution work.

## Validation

Final validation evidence:

* backend `./mvnw -q verify`: 437 tests, 0 failures, 0 errors, 0 skipped;
* JaCoCo: 82.5% line coverage and bundle rule passed;
* PostgreSQL/Testcontainers: V31 migration, concurrent uniqueness, terminal-key release, Source
  deletion provenance, and project cascade compatibility passed;
* frontend: 85 tests across 23 files passed;
* Angular production build and changed-file formatting checks passed;
* SonarQube Quality Gate `OK`;
* new-code coverage 87.1%, new duplicated lines 0.0%, and new violations 0;
* Docker backend and frontend images rebuilt and started successfully;
* live execution created Analysis `bd71ca14-88fa-4028-b3f9-91365d931b44`, reused it for an
  equivalent request, completed Profile `298421b0-0a2f-4182-a85d-7567804cbbdf`, kept six proposals
  in `PROPOSED`, and enabled subsequent Engineering Story Context retrieval;
* `git diff --check` passed.

## Code Review Outcome

The final Code Review found no remaining Blocker, Major, Minor, or Observation finding and
recommended approval.

Human Code Review approval: granted on 2026-08-09.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Residual Risks

* Repository preparation remains synchronous and may make requests long-running for large or remote
  repositories.
* V1 deliberately analyzes one selected Source per execution.
* Browser-level accessibility validation is represented by semantic markup, component tests, and a
  running UI rather than an automated browser suite in this environment.
* Future schema changes must retain the active-key uniqueness and immutable-provenance conventions.

These are bounded constraints, not unfinished Story 0018 scope.

## Remaining Work

None for Story 0018.

Passive monitoring, scheduled refresh, durable background orchestration, multi-Source execution,
and semantic comparison between understanding runs remain separate future capabilities.

## Final Status

Completed

No commit, push, or merge was performed automatically.
