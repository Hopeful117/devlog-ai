# Story 0022 — Validated Engineering Event Vertical Slice

## Story ID

0022

## Title

Transform a bounded repository evolution into a validated and traceable Engineering Event

## Status

Draft

## Priority

High

## Date

2026-08-09

## User Story

As a developer maintaining the technical memory of a project,
I want DevLog to propose meaningful Engineering Events from an explicitly selected repository
evolution and promote only the events I accept,
So that the project begins to retain a trustworthy history of what changed and why it mattered.

## Context

Stories 0018–0021 completed the short-term Engineering Story assistance loop: Project Understanding
can be refreshed on demand, agent context is bounded, proposals can be reviewed efficiently, and
repository freshness is explicit. DevLog can therefore support the current workflow reliably.

The next product objective is the long-term living technical memory. The model and API already name
`ENGINEERING_EVENT`, `ENGINEERING_DECISION`, and `CHALLENGE` proposal types, and early CRUD domains
exist for Knowledge Events and Decisions. However, the governed AI-to-validation pipeline currently
promotes only accepted `INSIGHT` proposals. Accepted Engineering Event proposals do not become an
immutable, provenance-rich domain object, and no versioned Intent currently asks the AI Engine to
interpret one bounded repository evolution as event proposals.

This Story introduces the first complete vertical slice of evolving project memory. It starts with
Engineering Events because they answer the most evidence-grounded question—what meaningful change
happened—before later Stories attempt the more interpretive questions of decisions and challenges.

## Objective

Allow a user to explicitly analyze a bounded Git evolution, receive grounded Engineering Event
proposals, review them through the existing human-validation workflow, and promote each accepted
proposal into immutable, queryable, traceable project knowledge.

## Acceptance Criteria

### AC-1: Evolution analysis is explicit and bounded

The user explicitly starts an evolution Analysis for one active Git Source and a deterministic
revision boundary. The system never scans or interprets new commits in the background. The analyzed
base and target revisions are complete immutable commit identities recorded in the Analysis context.

Repository Analysis and the approved Implementation Plan must select the smallest coherent revision
contract compatible with the current Analysis and history models; open-ended or unbounded history
interpretation is forbidden.

### AC-2: A versioned evolution Intent owns the semantic objective

Core exposes a versioned Intent whose sole objective is to propose meaningful Engineering Events
from the supplied bounded evidence. Its output contract, prompt template, context profiles, proposal
limits, and allowed event categories are explicit and immutable for the version. Existing Intents
retain their current behavior.

### AC-3: Deterministic evidence precedes AI interpretation

The Analysis uses existing synchronized Git history, commit-diff context, Facts, Observations, and
Repository Context where relevant. Core selects and snapshots evidence before the AI call. The AI
Engine cannot read Git or invent revision boundaries, commit IDs, evidence references, or event
categories.

### AC-4: Engineering Event proposals have a governed schema

Every `ENGINEERING_EVENT` proposal contains a validated structured payload with at least a stable
category, title, summary, significance rationale, and bounded evidence links. Categories must map to
the V1 event taxonomy or a smaller explicitly justified subset. Invalid, unsupported, ungrounded, or
oversized results are rejected at the Core boundary and cannot enter the review queue.

### AC-5: Human validation remains proposal-specific

Engineering Event proposals use the existing review and Validation authority. Accepting or rejecting
one proposal remains an explicit individual action. There is no bulk acceptance, confidence-based
auto-acceptance, inferred decision, or background promotion.

### AC-6: Acceptance promotes one immutable Engineering Event

Accepting an `ENGINEERING_EVENT` proposal creates exactly one immutable Engineering Event. The event
retains direct provenance to its Project, Analysis, proposal, Validation, Source/revision boundary,
and supporting evidence. Repeated or concurrent validation cannot create duplicates. Rejecting a
proposal creates no event.

### AC-7: Existing knowledge models are reconciled deliberately

Repository Analysis must determine whether the existing `KnowledgeEvent` model should be evolved,
migrated, wrapped, or superseded. The implementation must not create a second competing concept with
ambiguous ownership. Existing persisted Knowledge Events and their APIs must remain readable or be
migrated through an explicit backward-compatible strategy.

### AC-8: Event truth and interpretation remain distinct

An Engineering Event is validated structured knowledge, not raw Git activity and not a generic
Insight. Its projection clearly distinguishes the factual revision/evidence boundary from the
human-approved interpretation of significance. DevLog must not claim causality, intent, a Decision,
or a Challenge unless those facts are explicitly supported and governed by later capabilities.

### AC-9: Events are queryable in project context

The user can list and inspect validated Engineering Events for a Project with stable deterministic
ordering and their principal provenance. The Project cockpit exposes a bounded entry point or
summary sufficient to make the new memory visible without replacing the existing Analysis and
Insight audit surfaces.

### AC-10: Engineering Story Context gains only bounded validated memory

Validated Engineering Events may enter knowledge selection and Engineering Story Context through a
small, budgeted, traceable projection. Unvalidated proposals never appear as trusted events. The
current repository remains authoritative for implementation details.

### AC-11: Existing INSIGHT promotion remains compatible

Accepted `INSIGHT` proposals continue to produce immutable Insights with their current validation,
severity, traceability, and Deliverable behavior. Other proposal types remain unpromoted unless
explicitly implemented by this or a later Story.

### AC-12: Failure is atomic and retry-safe

If event payload validation or promotion fails, the system cannot leave a proposal accepted without
its required Engineering Event, nor create an event without its Validation. Transactional behavior,
locking, and database uniqueness preserve exactly-once promotion under concurrent requests.

### AC-13: Backend, AI Engine, and frontend coverage is mandatory

Tests cover revision bounds, Intent resolution, prompt/output contracts, grounding validation,
every supported event category, invalid results, acceptance/rejection, concurrency, rollback,
provenance serialization, Project isolation, existing Insight compatibility, review rendering, and
accessible event consultation.

### AC-14: Representative live validation is mandatory

Validate through Docker on a disposable Project/Source or deliberately selected non-sensitive
revision range. Demonstrate explicit Analysis creation, at least one grounded Engineering Event
proposal, individual acceptance, exactly one promoted event, preserved provenance, and absence of
automatic decisions or challenges. Do not alter real pending DevLog proposals without separate
human approval.

### AC-15: Documentation and quality are reconciled

Update the canonical knowledge model, pipeline, architecture, API/UI guidance, and roadmap where
the implemented boundary changes them. Run focused and complete backend, AI Engine, frontend,
database migration, build, Docker, API/UI, JaCoCo, formatting, repository hygiene, and authenticated
SonarQube validations. Completion requires a passing Quality Gate with no unresolved
Story-attributable issue.

## Out of Scope

* Passive monitoring, scheduled analysis, webhooks, polling, or notifications.
* Automatic validation or promotion based on confidence.
* Engineering Decision or Challenge promotion.
* Causal inference, developer-intent inference, or semantic commit significance scoring outside the
  governed event proposal.
* Multi-repository or multi-Source event aggregation.
* Pull Request, issue tracker, chat, calendar, or external knowledge-source integration.
* Editing or deleting validated Engineering Events unless Repository Analysis proves an existing
  compatibility requirement; validated knowledge should otherwise remain immutable.
* Automatic documentation generation from events.
* Vector retrieval, embeddings, or autonomous agents.

## Architectural Constraints

* Java Core owns revision bounds, deterministic evidence, Intent contracts, output validation,
  transactions, human validation, promotion, and trusted Engineering Events.
* Python AI Engine interprets only the immutable selected context supplied by Core.
* PostgreSQL enforces provenance integrity and exactly-once promotion.
* Angular owns interaction and presentation only.
* Raw Git activity, proposals, validated Engineering Events, generic Insights, Decisions, and
  Challenges remain distinct concepts.
* Existing immutable snapshot, digest, correlation, validation, workspace-confinement, and Project
  ownership boundaries must be preserved.
* The repository is authoritative; DevLog context is navigation evidence.

## Expected Deliverables

* `story.md`
* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
