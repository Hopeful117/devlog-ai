# Story 0020 — Guided Proposal Review Workspace

## Story ID

0020

## Title

Review an Analysis proposal queue efficiently without weakening human validation

## Status

Completed

## Priority

High

## Date

2026-08-09

---

## User Story

As a developer reviewing the results of a Project Understanding Analysis,
I want to examine and decide its proposals in one guided workspace,
So that I can complete the human-validation loop efficiently without losing evidence,
traceability, or deliberate proposal-by-proposal decisions.

---

## Context

DevLog now makes the preparation side of the Engineering Story workflow effective: project
understanding can be initialized or refreshed on demand, and the resulting repository context is
compact enough for direct agent use. The remaining short-term friction is the return path from an
AI Analysis to trusted project knowledge.

The current Angular workflow lists proposals inside an Analysis, but reviewing one navigates to a
separate detail route. The reviewer must enter or generate a reviewer UUID, inspect mostly raw
evidence identifiers, accept or reject, return to the Analysis, find the next pending proposal, and
repeat. An Analysis producing several proposals therefore incurs repeated navigation and identity
entry even though the Core already owns a correct atomic validation and Insight-promotion boundary.

This Story improves review ergonomics, not validation authority. Every proposal must still receive
one explicit, immutable human decision. Faster navigation must never become bulk or automatic
acceptance.

## Objective

Introduce an Analysis-scoped guided review workspace that:

* presents pending proposals as a coherent queue;
* makes progress and completed decisions visible;
* keeps evidence, rationale, metadata, and decision controls together;
* advances safely to the next pending proposal after a successful decision;
* avoids repeated local reviewer-identity entry during one review session;
* preserves individual immutable validation, severity assignment, comments, conflict handling, and
  Insight promotion;
* retains direct access to proposal, Analysis, validation, and resulting Insight traceability.

## Acceptance Criteria

### AC-1: Review starts from the source Analysis

An Analysis with proposals exposes one clear action to review its results. The review workspace is
scoped to that Analysis and cannot include proposals from another Analysis or Project.

The existing proposal-detail route remains usable for direct navigation and audit.

### AC-2: The queue and progress are explicit

The workspace reports total, pending, accepted, and rejected counts and identifies the current
position. Pending proposals are presented first in a stable deterministic order. The reviewer can
navigate previous/next without losing local form state unexpectedly.

Completed proposals remain inspectable but cannot be decided again.

### AC-3: Review context is actionable

For the current proposal, the workspace displays at least title, summary, rationale, type,
confidence, creation time, structured payload, evidence references, and supporting Fact or
Observation information available from the Core.

Repository or domain evidence must be labelled honestly. A missing detail route or unavailable
evidence must remain explicit rather than being represented as verified content.

### AC-4: Every decision remains individual and deliberate

Accept and reject remain proposal-specific operations. Each decision requires an explicit
confirmation and sends one request through the existing Core validation authority.

Bulk acceptance, bulk rejection, automatic acceptance, keyboard shortcuts that bypass
confirmation, and inferred decisions are forbidden.

### AC-5: Acceptance preserves severity and promotion semantics

Accepting an Insight proposal requires a valid severity and atomically creates exactly one
immutable Insight linked to its proposal and Validation. Rejecting creates no Insight. Comments
remain optional and bounded.

The Story must not weaken the `PROPOSED → ACCEPTED | REJECTED` state machine or the unique
Validation-per-Proposal database constraint.

### AC-6: Reviewer identity is efficient but honest

Until authentication exists, a locally generated reviewer UUID may be retained for the active
browser review session and reused across proposals. The workspace clearly labels it as a local MVP
identity, permits replacement, validates its UUID shape, and never presents it as an authenticated
person.

Reviewer identity must not be persisted as global trusted user configuration or silently generated
at decision time.

### AC-7: Successful decisions advance the queue safely

After a successful decision, counts refresh and the workspace advances to the next pending
proposal. When none remain, it displays a completion state and links to the resulting validated
Insights and deliverable workflow.

Navigation occurs only after the Core confirms the decision. Failed requests retain the current
proposal and form values.

### AC-8: Concurrent decisions are reconciled

If another request decides the current proposal first, the Core's conflict remains authoritative.
The workspace refreshes the queue, shows the final persisted state, and does not retry or overwrite
the decision automatically.

Duplicate clicks and in-flight repeated requests are prevented locally without replacing database
integrity.

### AC-9: Read models remain bounded and traceable

Any new Analysis-level review endpoint or DTO is a read projection over existing proposals,
validations, Insights, Facts, and Observations. It uses deterministic ordering, bounded payloads,
and stable identifiers. It does not create a second proposal lifecycle or copy mutable validation
state into an independent store.

Avoid N+1 request/query behavior for the normal review queue. Exact bounds and query ownership must
be established by Repository Analysis and the Implementation Plan.

### AC-10: Accessibility and responsive use are preserved

Queue navigation, status updates, errors, confirmation, and progress are keyboard-accessible and
announced with appropriate semantic HTML and live regions. Decision meaning is not communicated by
color alone. The workspace remains usable at narrow viewport widths.

### AC-11: Existing workflows remain compatible

Existing proposal list/detail APIs, validation APIs, proposal-detail route, Insights pages,
deliverable generation, generic Analysis creation, and Project Understanding execution continue to
work. Existing persisted proposals and decisions require no destructive migration.

### AC-12: Backend coverage is mandatory

Focused backend tests cover Analysis scoping, deterministic queue ordering and counts, pending and
completed proposals, evidence projection, bounded results, existing validation compatibility,
accepted Insight promotion, rejection, duplicate/conflicting decisions, and query behavior where
applicable.

### AC-13: Frontend coverage is mandatory

Angular tests cover loading, empty/error/completion states, progress, stable navigation,
session-local reviewer identity, individual confirmation, acceptance severity, rejection, pending
suppression, failure retention, conflict refresh, resulting Insight links, accessibility-relevant
semantics, and compatibility of the direct proposal route.

### AC-14: Representative workflow validation is mandatory

Validate the complete review of a disposable or safely reusable multi-proposal Analysis through
the running Docker application. Record navigation steps, requests, persisted Validation/Insight
outcomes, conflict behavior where practical, and the reduction in repeated reviewer/navigation
actions compared with the current workflow.

The validation demonstrates workflow behavior only; it must not claim that accepted AI proposals
are correct without human judgment.

### AC-15: Documentation and quality are reconciled

Canonical UI, API, and architecture documentation describes the guided review flow, local reviewer
identity boundary, individual immutable decisions, conflict behavior, and continued direct audit
routes.

Run focused and complete backend/frontend tests, Angular production build and formatting,
JaCoCo, authenticated SonarQube with Quality Gate wait, Docker/API/UI validation, and repository
hygiene checks. Completion requires a passing Quality Gate and no new unresolved issue attributable
to the Story.

## Out of Scope

* Bulk acceptance or rejection.
* Automatic validation based on confidence, severity, model, or evidence count.
* Authentication, authorization, durable user accounts, roles, or reviewer permissions.
* Editing AI-generated proposal content before validation.
* Reversing, deleting, or superseding an immutable Validation or Insight.
* Re-running or comparing Analyses from the review workspace.
* Knowledge-freshness scoring or refresh recommendations.
* New proposal-generation prompts, ranking, confidence interpretation, or AI providers.
* Deliverable editing/export improvements.
* WebSockets, message brokers, or durable AI-job orchestration.

## Architectural Constraints

* Java Core remains authoritative for proposal state, validation, and Insight promotion.
* PostgreSQL remains authoritative for uniqueness and persisted decision integrity.
* Angular owns review interaction and session-local convenience state only.
* AI confidence is informational and can never authorize a decision.
* Accepted Insights remain immutable trusted knowledge with complete proposal/validation provenance.
* Existing project and Analysis ownership boundaries must be enforced on every review projection.
* The Story should reuse existing services and contracts unless a focused read projection materially
  reduces coupling, N+1 behavior, or client orchestration.

## Expected Deliverables

* `story.md`
* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
* backend/frontend implementation and tests as approved by the plan
* reconciled canonical documentation
