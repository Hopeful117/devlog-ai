# Implementation Plan — Story 0022

## Status

Ready for Human Approval Gate 2.

## Overview

Implement the first trusted historical-memory vertical slice around one explicitly selected Git
commit. A dedicated Project-scoped execution will resolve a complete target commit for one active
Git Source, import its existing bounded history metadata, derive the first parent, persist an
immutable evolution scope, build the existing `CommitDiffAnalysisContext`, and execute a new
versioned Engineering Event Intent.

The AI Engine will receive only the Core-selected immutable context and may return zero or more
schema-valid `ENGINEERING_EVENT` proposals. Core will independently validate proposal type,
payload, and every evidence reference before persistence. Existing individual human Validation will
dispatch an accepted event proposal to a new immutable `EngineeringEvent` domain object in the same
transaction. Existing `INSIGHT` promotion remains unchanged; proposal types without a promotion
handler fail explicitly instead of being silently accepted.

The initial scope is exactly one non-root commit against its first parent. Merge commits use the
already documented first-parent policy and remain explicitly marked. Arbitrary ranges,
multi-commit grouping, root commits, patch/symbol semantics, Decisions, Challenges, and passive
execution remain deferred.

## Approved inputs

Implementation will use:

* Story 0022;
* the human-approved Repository Analysis and its fixed domain boundary;
* the clean `main` baseline at `2e6c71e`;
* existing Analysis, Project Understanding, history, Context, Intent, AI Task, proposal, Validation,
  Insight, review, Project cockpit, and Engineering Story Context conventions;
* ADR-004, ADR-006, ADR-007, ADR-009, ADR-013–016, ADR-018–020, ADR-028–033, ADR-035–036,
  ADR-040, and ADR-043;
* the configured OpenAI provider only for the final disposable live validation, without exposing or
  persisting its credentials;
* the real DevLog Project only as read-only reference data. Its six pending proposals remain
  untouched.

No production implementation work may begin before explicit human approval of this plan.

## Fixed contract decisions

### Evolution boundary

The v1 execution accepts exactly one complete Git object ID:

```text
targetCommit = 40- or 64-character hexadecimal commit ID
baseCommit   = target ProjectCommit's first parent
```

Rules:

* normalize to lowercase;
* reject abbreviated IDs, refs, branches, tags, blank values, and non-hexadecimal strings;
* synchronize the Source at `targetCommit`, then require the resolved revision to equal it exactly;
* import reachable history through the already synchronized workspace;
* require the target commit to exist for the exact Source and Project;
* reject root commits with a stable validation error because v1 requires a base;
* use parent position zero for merge commits and persist `mergeCommit=true` plus
  `FIRST_PARENT` comparison policy;
* never infer or accept the base commit from the client;
* never traverse or interpret an arbitrary range.

### Explicit execution API

Add:

```text
POST /api/v1/projects/{projectId}/engineering-event-executions
```

Request:

```json
{
  "sourceId": "<uuid>",
  "targetCommit": "<complete-commit-id>",
  "userGuidance": null
}
```

Response version `engineering-event-execution-v1`:

```json
{
  "version": "engineering-event-execution-v1",
  "analysisId": "<uuid>",
  "status": "IN_PROGRESS",
  "projectId": "<uuid>",
  "sourceId": "<uuid>",
  "baseCommit": "<complete-commit-id>",
  "targetCommit": "<complete-commit-id>",
  "comparisonPolicy": "FIRST_PARENT",
  "mergeCommit": false,
  "intentId": "analyze-engineering-event",
  "intentVersion": "v1",
  "outcome": "CREATED"
}
```

Equivalent active executions return the existing winner with `outcome=REUSED`; they do not launch a
second Analysis. Completed/failed executions do not prevent a deliberate new execution.

Use existing 404 conventions for unknown/cross-Project/inactive Sources and absent commits, 400 for
invalid commit identity/root commit/non-Git Source, and normal workflow failures for synchronization
or AI submission. Do not return remote URLs, workspace paths, Git stderr, or credentials.

Generic `POST /api/v1/analyses` must reject Intents whose execution mode is dedicated. This prevents
creation of an event Analysis without an immutable evolution scope.

### Evolution-scope persistence

Add migration V33 with:

```text
analysis_evolution_scopes
├── analysis_id UUID PRIMARY KEY/FK analyses ON DELETE CASCADE
├── project_id UUID NOT NULL/FK projects ON DELETE CASCADE
├── source_id UUID NOT NULL/FK sources ON DELETE CASCADE
├── context_version VARCHAR(50) NOT NULL
├── comparison_policy VARCHAR(30) NOT NULL
├── base_commit VARCHAR(64) NOT NULL
├── target_commit VARCHAR(64) NOT NULL
├── target_committed_at TIMESTAMPTZ NOT NULL
└── merge_commit BOOLEAN NOT NULL
```

Constraints enforce lengths `40` or `64`, lowercase hexadecimal format, unequal base/target, and
one scope per Analysis. Add indexes for `(project_id, target_committed_at DESC, target_commit)` and
`(source_id, target_commit)`.

Add nullable `evolution_execution_key VARCHAR(64)` to `analyses` and a PostgreSQL partial unique
index for non-null keys while status is `PENDING` or `IN_PROGRESS`, mirroring the Project
Understanding active-work claim. The SHA-256 key includes Project ID, Source ID, normalized target,
Intent ID/version, and canonical User Guidance. It does not include mutable timestamps.

The preparation stage performs Git/network work outside a database transaction. The claim stage
reloads Project/Source and target commit inside a short transaction, persists Analysis and scope,
and handles unique-index races by loading the winner.

### Engineering Event domain

Do not alter the semantics or API of legacy `KnowledgeEvent`. Add migration V33 table:

```text
engineering_events
├── id UUID PRIMARY KEY
├── project_id UUID NOT NULL/FK projects ON DELETE CASCADE
├── analysis_id UUID NOT NULL/FK analyses
├── proposal_id UUID NOT NULL UNIQUE/FK validatable_proposals
├── validation_id UUID NOT NULL UNIQUE/FK validations
├── source_id UUID NOT NULL/FK sources
├── category VARCHAR(50) NOT NULL
├── title VARCHAR(255) NOT NULL
├── summary TEXT NOT NULL
├── significance TEXT NOT NULL
├── base_commit VARCHAR(64) NOT NULL
├── target_commit VARCHAR(64) NOT NULL
├── occurred_at TIMESTAMPTZ NOT NULL
└── created_at TIMESTAMPTZ NOT NULL
```

Add ownership/provenance checks where PostgreSQL can enforce them safely and service-level exact
Project/Analysis/Source/scope equality checks before insert. Unique proposal and Validation links are
the final exactly-once boundary. The entity has no update/delete service and exposes no setters for
business fields.

Taxonomy `EngineeringEventCategory`:

* `FEATURE_INTRODUCTION`
* `BUG_RESOLUTION`
* `ARCHITECTURE_CHANGE`
* `TECHNOLOGY_CHANGE`
* `ENGINEERING_IMPROVEMENT`
* `INFRASTRUCTURE_CHANGE`

There is no `OTHER`; zero proposals is preferable to unsupported categorization.

### Engineering Event proposal payload

Version `engineering-event-proposal-v1` requires exactly:

```json
{
  "schemaVersion": "engineering-event-proposal-v1",
  "category": "FEATURE_INTRODUCTION",
  "title": "...",
  "summary": "...",
  "significance": "..."
}
```

Limits:

* title: 1–255 characters;
* summary: 1–5000 characters;
* significance: 1–5000 characters;
* no unknown payload fields;
* maximum 10 proposals;
* duplicate normalized `(category,title)` proposals in one callback are rejected;
* supporting Fact/Observation IDs and evidence references retain existing bounded list contracts;
* at least one grounding reference across Fact IDs, Observation IDs, or evidence references is
  required for every event proposal.

Core validates the complete schema and proposal type against the persisted Analysis Intent before
`saveAll`. It also reconstructs the allowed Fact IDs, Observation IDs, and repository references
from the AiTask's immutable selected-knowledge snapshot. Python applies the same checks before its
callback as defense in depth.

### Intent generalization

Add ADR-047 recording **Proposal-type-aware Intent output and validated Engineering Event
ownership**.

Extend the versioned `IntentDefinition` contract additively with:

* `outputProposalType` — exactly one `ProposalType` for v1;
* `executionMode` — `GENERIC` or `DEDICATED_ENGINEERING_EVENT`;
* existing `supportedInsightTypes`, retained and non-empty only for `INSIGHT` output;
* existing output schema, prompt template, constraints, and Context Profiles.

Existing three Intent versions become explicit `outputProposalType=INSIGHT` and
`executionMode=GENERIC` without changing their IDs, objectives, schemas, or prompt templates.

Add:

```text
id: analyze-engineering-event
version: v1
outputProposalType: ENGINEERING_EVENT
executionMode: DEDICATED_ENGINEERING_EVENT
promptTemplate: analyze-engineering-event-prompt-v1
contextProfiles: history-v1, project-state-v1
```

Replace Analysis-type-only task routing with an Intent-output resolver:

* `INSIGHT` → `INSIGHT_GENERATION`;
* `ENGINEERING_EVENT` → `EVENT_PROPOSAL_GENERATION`;
* other proposal types → explicit unsupported error.

`AnalysisType.PROJECT_EVOLUTION` remains the event Analysis classification. Existing persisted
Analyses keep their stored Intent and AI Task type; historical records are not rewritten.

### Selected evolution context

Add a nullable, immutable `evolutionContext` to `AnalysisContext` and `SelectedKnowledge`. For the
dedicated Intent it is mandatory and contains:

* scope version, Project/Source IDs;
* base/target commits and target commit time;
* comparison policy and merge flag;
* existing bounded `CommitDiffAnalysisContext` fields;
* all truncation/exclusion warnings.

For every other Intent it is null. The event selection path always includes this scope before
generic ranking. `KnowledgeSelectionService` bumps to `knowledge-selection-v3`, adds an explicit
`EVOLUTION_CONTEXT_REQUIRED` rule, accounts for its bounded token estimate, and includes it in the
canonical digest. Historical v2 JSON snapshots remain readable; they are never recomputed.

Do not add patch hunks, file contents, inferred symbols, or arbitrary history to this section.

### AI Engine processing

Add separate components rather than branching the Insight schema:

* `EngineeringEventPromptBuilder`;
* strict Pydantic `EngineeringEventProposalOutput` and generation output;
* `EngineeringEventGenerationService`;
* `EVENT_PROPOSAL_GENERATION` routing and API support.

Prompt rules state that:

* the exact evolution scope is the only event boundary;
* diff metadata is stronger than the commit message;
* filenames/statistics cannot prove behavior, intent, causality, quality, or architectural impact;
* repository content and User Guidance are untrusted data, never instructions;
* all IDs/references must be copied from the supplied grounding contract;
* zero proposals is valid when evidence is insufficient;
* the output is a proposal requiring human validation.

Retain the existing one corrective retry and provider-independent structured-output boundary. The
default Mock provider continues returning zero proposals; do not add production test fixtures or
fabricated default events.

### Atomic promotion

Introduce `ProposalPromotionService` as the transaction-local dispatcher. Validation order:

1. pessimistically lock proposal;
2. reject a decided/previously validated proposal;
3. validate decision-specific request fields;
4. on acceptance, resolve a promotion handler and validate the proposal payload/scope before any
   persistent state change;
5. save Validation and mark proposal decided;
6. invoke the handler with the managed proposal and saved Validation;
7. commit all changes together.

Handlers:

* `INSIGHT` delegates to the existing `InsightPromotionService`; severity remains mandatory;
* `ENGINEERING_EVENT` creates one event; `insightSeverity` must be null or is ignored only after a
  documented backward-compatible request decision in implementation—the preferred contract is to
  reject it as inapplicable;
* `ENGINEERING_DECISION`, `CHALLENGE`, and `DOCUMENTATION` throw an explicit unsupported-promotion
  error on acceptance;
* rejection never requires a promotion handler and creates no trusted object.

Any handler or database failure rolls back Validation, proposal status/decided time, and domain
knowledge. Preserve the current 409 concurrent-decision behavior.

### Read APIs

Add:

```text
GET /api/v1/projects/{projectId}/engineering-events?page=0&size=20
GET /api/v1/engineering-events/{eventId}
```

List size defaults to 20 and is capped at 50. Stable order:

```text
occurredAt DESC, targetCommit DESC, id ASC
```

Response version `engineering-event-v1` includes event fields and a bounded provenance projection:

* Project/Analysis/proposal/Validation/Source IDs;
* category, title, summary, significance;
* base/target commits, comparison policy, merge flag, occurred/created times;
* proposal confidence;
* bounded supporting Fact/Observation IDs and evidence references.

The list uses the same compact item with page metadata. Detail does not expose remote URL, local
path, prompt content, complete selected knowledge, or credentials; existing Analysis/proposal pages
remain the deep audit surfaces.

### Proposal review projection

Bump the additive review projection to `proposal-review-v2` and add nullable
`resultingEngineeringEvent`. Preserve `resultingInsight` unchanged.

Batch-load event results by proposal IDs; do not introduce per-item queries. The frontend:

* labels proposals using their actual type;
* renders event category, summary, significance, and base→target boundary explicitly;
* shows Insight severity only when accepting an `INSIGHT`;
* sends no severity when accepting an Engineering Event;
* links accepted events to their detail page;
* retains raw JSON as an audit fallback;
* preserves individual confirmation, reviewer UUID, conflict refresh, focus, and pagination.

### Project and agent context

Add at most 10 recent validated Engineering Events to `ProjectContextSnapshot`, ordered by the
stable event order. Add at most 5 compact events to the Project cockpit and a full paged route:

```text
/projects/:slug/events
/engineering-events/:id
```

Add a bounded `validatedEngineeringEvents` section to rich and agent-ready Engineering Story
Context containing only ID, category, title, summary, source ID, base/target commits, occurred time,
and proposal reference. Include it in projection accounting/digest and deterministic reduction.

Add the same maximum-10 trusted-event snapshot to future AI `SelectedKnowledge`. Unvalidated
proposals and raw `KnowledgeEvent` rows do not enter this trusted section. Events must not displace
the mandatory evolution context; when budgets tighten, reduce older event tail entries first.

## Planned implementation sequence

### 1. Record baseline evidence

Before production edits, record outside Git:

* branch, HEAD, and worktree state;
* current backend/AI/frontend test counts and Sonar state;
* current Intent JSON and AI Engine supported task types;
* absence of the event execution/read APIs;
* zero live Engineering/Knowledge Events and the six untouched real proposals;
* one representative non-root commit, its first parent, merge/root flags, changed-file count, and
  bounded commit-context warnings for later disposable validation.

### 2. Add ADR and shared contracts

Create ADR-047 first. Add enums/DTO changes for output proposal type, execution mode, event category,
comparison policy, proposal schema version, and strict Git identity normalization. Update Core and
Python serialization tests before changing orchestration.

### 3. Add V33 and persistence models

Implement migration, evolution scope entity/repository, Engineering Event entity/repository, active
execution key, database constraints, Project deletion coverage, fresh PostgreSQL migration test,
and repository query tests. Do not modify legacy event rows.

### 4. Implement preparation and claim flow

Create a cohesive `engineeringevent.execution` application package modeled on Project
Understanding but without sharing semantic execution keys. Keep workspace synchronization/history
import outside transactions; persist/recheck ownership inside claim. Add controller and stable error
handling.

### 5. Integrate evolution context and selection

Load the immutable scope and exact persisted commit context for event Analyses. Extend Context,
selection v3, accounting, digest, serialization, and compatibility constructors where necessary.
Assert the selected target/source exactly match scope and imported history.

### 6. Implement event Intent and AI Engine

Add catalog entry, Intent-output task resolver, Core proposal contract validator, Python prompt,
schema, service, routing, grounding validation, corrective retry, and callback metadata. Existing
Insight and Deliverable paths remain separate and unchanged.

### 7. Implement atomic promotion and read projections

Add dispatcher and event handler, move Insight call behind its handler, enforce unsupported
acceptance, and add query/detail services/controllers. Extend proposal review with batched resulting
events and verify rollback/concurrency on PostgreSQL.

### 8. Implement Angular surfaces

Add explicit event execution form with active Git Source and complete target commit, clear
first-parent explanation, progress/navigation to Analysis, generic proposal review rendering,
conditional severity, recent events, paged list, and detail/audit links. No automatic launch occurs
on route load, Source connection, or freshness state.

### 9. Reconcile canonical documentation

Update only the documentation listed in Repository Analysis and ADR-047. Explicitly distinguish raw
Knowledge Events, validated Engineering Events, repository evidence, generic Insights, Decisions,
and Challenges. Record documentation outcome in `implementation-report.md` before Code Review.

### 10. Validate in increasing scope

Run focused tests after each boundary, then complete suites/builds/migrations, Docker/live, Sonar,
and hygiene. Correct only Story-local findings.

## Detailed test matrix

### Backend

* Complete 40/64 commit normalization; reject abbreviated/non-hex/mismatched resolved target.
* Active Git Source ownership and Project isolation.
* Non-root first parent, merge first-parent policy, root rejection, missing target.
* Preparation outside transaction and claim recheck.
* Equivalent active execution race produces one Analysis/scope/workflow launch.
* Completed execution allows deliberate rerun.
* Scope immutability, database checks/indexes, deletion cascade.
* Event Intent identity, output type, execution mode, schema, and task routing.
* Generic Analysis rejects dedicated Intent.
* Evolution context mandatory only for event Intent; source/target mismatch rejected.
* Selection v3 budget, accounting, stable digest, historical/null compatibility.
* Event payload every category, text bounds, extra/missing fields, duplicates, empty grounding.
* Core rejects wrong proposal type and foreign/unknown Fact, Observation, or repository references.
* Existing Insight callback/promotion and prompt traceability regression.
* Accepted event creates exactly one immutable event; rejection creates none.
* Unsupported accepted types roll back; promotion/database failure rolls back all state.
* Concurrent decisions retain one Validation/event and return conflict to loser.
* Stable list ordering, paging cap, ownership, detail provenance, batched review hydration.
* Bounded Project/agent context contains only validated events.

### AI Engine

* Intent contract accepts current Insight Intents and the event Intent.
* Task API accepts Event and Insight generation, rejects still unsupported task types.
* Prompt contains exact scope, trust hierarchy, injection boundary, zero-output allowance, and
  version identity.
* Strict event output models cover six categories, bounds, extra fields, and maximum 10.
* Grounding subset covers Fact, Observation, commit, changed-file, ADR, and roadmap references.
* Unsupported category/type, duplicate, or invented reference triggers one corrective retry then a
  sanitized failure callback.
* Successful callback emits `ENGINEERING_EVENT`, prompt metadata, and no secret/full prompt logs.
* Default Mock returns zero event proposals; configured OpenAI path remains provider-independent.
* Existing Insight and Deliverable suites remain green.

### Frontend

* Event launch is explicit; opening Project performs no POST.
* Source/commit validation, pending, success, reused, error, duplicate-click suppression.
* Review renders actual proposal type and event fields.
* Severity shown/sent for Insight only; event decision remains individual and confirmed.
* Resulting event navigation, accepted/rejected state, conflict refresh, error retention.
* Recent/list/detail loading, empty/error states, stable links, provenance labels.
* Keyboard focus, semantic status/alert regions, and narrow-layout behavior.

## Complete validation commands

Run from the appropriate module/directories:

```text
backend:  ./mvnw -q test
backend:  ./mvnw -q clean verify
ai-engine: pytest
ai-engine: formatting/lint commands declared by pyproject
frontend: npm test -- --watch=false
frontend: npm run build
frontend: formatting check declared by package scripts
root: docker compose up -d --build backend ai-engine frontend
root: git diff --check
```

Run authenticated SonarQube from `backend` using the ignored root `.env` token without printing it:

```text
set -a
. ../.env
set +a
./mvnw -q sonar:sonar -Dsonar.token="$SONAR_TOKEN" -Dsonar.qualitygate.wait=true
```

Record project key, Quality Gate, new bugs, vulnerabilities, security hotspots, code smells,
new-code coverage, duplication, and unresolved new issues. Never store tokens in artifacts.

## Representative live validation

Use Docker and create a disposable Project/Source pointing to the existing public DevLog repository.
Do not reuse the real DevLog Project for writes.

Preferred target is one non-root, non-merge Story commit whose changed files and documentation make
one category objectively supportable. Record target and first parent before execution. With the
already authorized OpenAI configuration:

1. POST the explicit event execution once and confirm `CREATED`;
2. repeat while active and confirm `REUSED` with the same Analysis;
3. inspect persisted scope, selected-knowledge digest, prompt metadata, and callback;
4. require at least one grounded event proposal for AC-14—if the provider validly returns zero,
   try one other pre-recorded representative commit once; if still zero, stop and report AC-14 as
   unmet rather than fabricate knowledge;
5. accept exactly one proposal with a disposable reviewer UUID;
6. verify one Validation and one Engineering Event with exact Source/base/target/provenance;
7. retry/concurrently submit the same decision and verify no duplicate;
8. verify Project list/detail, proposal review, cockpit, and Engineering Story Context;
9. verify no Decision, Challenge, documentation, or automatic additional event was created;
10. remove only the disposable Project through the normal recoverable scoped API after recording
    evidence, confirming the remote repository is untouched.

Real DevLog proposal counts and decisions must be checked before and after and remain unchanged.

## Documentation reconciliation

Expected updates:

* `README.md` — explicit event execution, validation, and read flow;
* `docs/decisions/ADR-047.md` — new architectural decision;
* `docs/architecture.md` — raw versus validated event boundary;
* `docs/pipeline.md` — first implemented event promotion path;
* `docs/knowledge-model.md` and `docs/data-model.md` — provenance/taxonomy;
* `docs/ui-ux.md` — launch, review, recent/list/detail behavior;
* `docs/roadmap.md` — mark only the delivered single-commit vertical slice;
* `ai-engine/README.md`, `frontend/README.md`, and the manual MVP test guide where required.

Do not claim arbitrary range analysis, code semantic diffing, event grouping, passive monitoring,
Decisions, Challenges, or autonomous memory updates.

## Expected implementation artifacts

Workflow artifacts:

* `implementation-report.md` after implementation and documentation reconciliation;
* `code-review.md` after independent Story/plan/diff review;
* `engineering-report.md` only after explicit Gate 3 approval.

Production changes will be confined to the affected Core, AI Engine, Angular, migration, ADR, and
canonical documentation files. No commit, push, merge, real proposal decision, or Engineering
Report is authorized before its normal workflow point.

## Gate 2 decision requested

Approval authorizes implementation of this exact single-commit vertical slice, including V33,
ADR-047, the dedicated Event Intent/task, Core-authoritative proposal validation, atomic promotion,
read/context/UI surfaces, and the stated validations. It does not authorize the deferred monitoring,
range/grouping, patch-semantic, Decision, Challenge, or autonomous capabilities.
