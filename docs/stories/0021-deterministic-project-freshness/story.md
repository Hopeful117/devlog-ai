# Story 0021 — Deterministic Project Freshness and Refresh Guidance

## Story ID

0021

## Title

Determine whether Project Understanding still represents the current repository revision

## Status

Completed

## Priority

High

## Date

2026-08-09

## User Story

As a developer using DevLog during an Engineering Story workflow,
I want to check whether the latest Project Understanding still matches the selected repository,
So that I can refresh stale knowledge before relying on it without introducing automatic analysis.

## Context

Stories 0018–0020 completed the short-term preparation and validation path: Project Understanding
can be launched on demand, Engineering Story Context has a compact agent-ready projection, and
proposals can be reviewed efficiently while retaining individual human decisions. The remaining
short-term ambiguity is temporal: DevLog exposes revision-traceable snapshots but does not provide
one authoritative answer to whether the latest completed understanding still represents the
current revision of an active Git Source.

The current Project cockpit can infer “Workspace is up to date” from the mere presence of Sources,
Analyses, and Deliverables, and can imply that configured repositories are monitored even though
passive monitoring is explicitly deferred. In the real DevLog project, the latest understanding
captured revision `b2f2c888…`, while the repository has advanced through Stories 0018–0020 to
`f67344c…`. The system stores both revision provenance and synchronization timestamps but does not
turn them into honest refresh guidance.

This Story closes the current-workflow objective with a deterministic, explicitly requested
freshness check. It establishes the minimal contract that future passive monitoring may reuse; it
does not implement background monitoring, significance interpretation, or automatic refresh.

## Objective

Introduce a Project- and Source-scoped freshness capability that:

* resolves the current revision only after an explicit user or API request;
* compares it with the revision captured by the latest relevant completed Project Understanding;
* reports a small, stable, explainable freshness state;
* recommends an on-demand refresh when the baseline is stale or absent;
* distinguishes repository freshness, Analysis execution, pending proposal review, and Trusted
  Knowledge validation;
* exposes bounded freshness metadata to the Project cockpit and Engineering Story Context;
* never claims freshness when current revision resolution fails or evidence is incomplete.

## Acceptance Criteria

### AC-1: Freshness checking is explicit and Source-scoped

The user can request a freshness check for one active Git Source belonging to the Project. The Core
derives Project ownership and rejects inactive, unknown, or cross-Project Sources through existing
error conventions. Opening the Project or connecting a Source does not silently start a check.

### AC-2: The current repository revision is resolved deterministically

The check resolves the Source's current default revision using the existing Git workspace boundary
or a clearly owned equivalent. The response records the requested/default revision, resolved commit
SHA, check time, and Source identity. It does not invoke the AI Engine or infer semantic importance.

### AC-3: The baseline is the latest relevant completed understanding

The comparison baseline is the latest successfully completed `describe-project-v1` Project
Understanding for the same Project and Source. Pending, running, failed, generic, cross-Source, and
historical explicit-revision Analyses must not be selected accidentally. The baseline exposes its
Analysis ID, completion time, and analyzed commit SHA when available.

### AC-4: Freshness states are stable and honest

The Core returns an explicit versioned state with at least:

* `NO_BASELINE` when no comparable completed understanding exists;
* `CURRENT` only when both authoritative commit SHAs are available and equal;
* `STALE` when both are available and differ;
* `UNKNOWN` when persisted provenance cannot support a valid comparison;
* a standard error outcome or explicit `CHECK_FAILED` projection when current revision resolution
  fails, as determined by Repository Analysis and the approved Implementation Plan.

Timestamps alone, Source presence, Analysis count, Deliverable count, or proposal status can never
produce `CURRENT`.

### AC-5: Guidance remains non-authoritative

The response provides deterministic guidance such as `REFRESH_RECOMMENDED`, `REFRESH_NOT_NEEDED`,
or `ESTABLISH_BASELINE`. Guidance may navigate to the existing Project Understanding action but
must not create or launch an Analysis automatically.

### AC-6: Knowledge workflow state is not conflated with repository freshness

The user-facing projection distinguishes at least:

* repository revision freshness;
* Project Understanding availability/status;
* pending proposal count;
* accepted/rejected review progress where already available.

A current repository baseline with pending proposals is not presented as fully validated Trusted
Knowledge. Conversely, pending proposals do not make the analyzed repository revision stale.

### AC-7: The Project cockpit becomes truthful and actionable

The cockpit exposes the last checked time, analyzed revision, current revision, freshness state,
and a clear manual action to check again or refresh understanding. It removes or corrects claims of
passive monitoring and “up to date” state that are not supported by revision evidence. Unknown and
failure states remain visible and do not degrade into a green/current presentation.

### AC-8: Engineering Story Context carries bounded freshness metadata

The compact Engineering Story Context includes a small freshness summary or warning sufficient for
an agent to know whether Project Understanding is current, stale, absent, or unknown. It must not
inflate the compact projection with complete Analyses or operational diagnostics. Repository
evidence remains navigation context and the current repository remains authoritative.

### AC-9: Checks are concurrency-safe and bounded

Equivalent simultaneous checks for a Source must not corrupt its workspace or freshness state.
Existing per-workspace synchronization protection should be reused where appropriate. The check
performs no unbounded history traversal and does not calculate semantic commit significance.

### AC-10: Existing refresh and validation authority remain unchanged

`POST /api/v1/projects/{projectId}/understanding-executions` remains the only on-demand Project
Understanding launch path. Proposal validation remains individual and human-authorized. Freshness
checking cannot accept proposals, create Insights, generate Deliverables, or alter Trusted
Knowledge.

### AC-11: Failure preserves existing knowledge

A Git/network/workspace failure during checking does not invalidate or delete the latest completed
understanding, proposals, Insights, or Deliverables. The failure is traceable, retryable, and never
represented as `CURRENT`.

### AC-12: Backend coverage is mandatory

Focused backend tests cover ownership, active Source enforcement, baseline selection, every
freshness state, malformed/missing provenance, explicit revisions, concurrency behavior, failure
preservation, stable API serialization, and compatibility with Project Understanding.

### AC-13: Frontend and adapter coverage is mandatory

Angular tests cover initial unchecked state, checking, current, stale, no-baseline, unknown,
failure, refresh guidance, truthful pending-review messaging, keyboard accessibility, and narrow
layouts. Engineering Story Context adapter/contract tests cover the bounded freshness summary and
backward-compatible evidence transport.

### AC-14: Representative live validation is mandatory

Validate through the running Docker application with the real DevLog project or a disposable
repository: demonstrate a known stale revision, refresh through the existing explicit action, and
then demonstrate `CURRENT` at the same commit without automatically deciding any proposal. Record
the compared SHAs and preserve real proposal decisions unless the human reviews them separately.

### AC-15: Documentation and quality are reconciled

Canonical product, API, architecture, UI, and roadmap documentation distinguishes explicit
freshness checks from passive monitoring and explains the trust boundary. Run focused and complete
backend/frontend/adapter tests, production build and formatting, JaCoCo, authenticated SonarQube
with Quality Gate wait, Docker/API/UI validation, and repository hygiene checks. Completion
requires a passing Quality Gate and no new unresolved Story-attributable issue.

## Out of Scope

* Passive monitoring, schedulers, cron jobs, polling loops, or webhooks.
* Automatic Project Understanding or automatic proposal decisions.
* Commit significance, semantic-diff, impact, risk, or change-priority classification.
* Incremental collection or partial reanalysis.
* Notifications, AgentJob orchestration, or background retries.
* Multi-Source aggregate freshness beyond a deliberate Source-scoped check.
* Authentication, permissions, reviewer identity, or collaboration features.
* Editing, superseding, or deleting historical Analyses, Validations, Insights, or Deliverables.
* Treating local working-tree changes as a persisted Git revision.

## Architectural Constraints

* Java Core owns revision resolution, baseline selection, freshness classification, and operational
  projection state.
* Git commit identity, not timestamps or UI heuristics, is the comparison authority.
* PostgreSQL stores only operational/check provenance justified by Repository Analysis; it must not
  duplicate immutable Analysis snapshots unnecessarily.
* Angular owns interaction and presentation only.
* AI confidence and provider output have no role in freshness classification.
* Existing workspace confinement, synchronization locking, correlation/error contracts, and
  Project/Source ownership boundaries must be preserved.
* ADR-041 passive monitoring and ADR-043 autonomy remain future boundaries, not implementation
  authorization for this Story.

## Expected Deliverables

* `story.md`
* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
