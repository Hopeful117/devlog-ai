# Story 0060 — Contradiction and Supersession in Incremental Knowledge Evolution — Engineering Report

## Status

Reported

## ADR

### Number

ADR-054

### Title

Contradiction and Supersession in Incremental Knowledge Evolution

### Status

Accepted

### Main decisions

* contradiction and supersession are distinct concepts;
* the AI proposes supersession; it never mutates trusted knowledge;
* supersession passes through the existing human validation lifecycle;
* historical truth is preserved, never rewritten;
* a binary trust-state marker distinguishes current from historical truth;
* a directional `SUPERSEDES` relation records the transition;
* the Core enforces structural constraints without parsing free text;
* repeated analysis with no material change remains idempotent.

### Related ADRs

* ADR-006
* ADR-047
* ADR-049
* ADR-050
* ADR-051

## Engineering Story

### Number

0060

### Title

Contradiction and Supersession in Incremental Knowledge Evolution

### Scope

Architecture-only first slice:

* trust-state marker on trusted knowledge;
* `SUPERSEDES` relation vocabulary;
* delta contract extended with `SUPERSEDES`;
* Core-side structural supersession validation;
* supersession promotion with history preservation;
* reuse of existing validation lifecycle.

### Status

Implemented

### Acceptance Criteria

Met.

## Previous Behavior

Repeated architecture analyses reproduced the same conclusions because trusted
knowledge was only a comparison input for `NEW`/`ENRICHES`. A promoted
statement could never change its trust representation, so DevLog could not
represent that a project replaced a technology without keeping both statements
as equally active or rewriting historical truth (both forbidden by ADR-050 and
ADR-006).

## Knowledge Context

### Source of trusted knowledge

Persisted trusted `Insight` records for the current project, selected through
the existing `existingArchitectureKnowledge` mechanism.

### Selection

Project-scoped, architecture-scoped, deterministic selection in
`KnowledgeSelectionServiceImpl`. Reused unchanged from Story 0037.

### Limit

5 existing architecture knowledge items in V1.

### Structure sent to analysis

Dedicated `existingArchitectureKnowledge` section containing trusted insight
identity, type, source classification, title, content, rationale, evidence, and
timestamps. The supersession target must be drawn from this selected set.

## Knowledge Delta

### Model retained

Payload-level delta metadata plus a trust-state marker:

* `deltaType`
* `targetInsightId`
* `InsightTrustState` (`ACTIVE` / `SUPERSEDED`)

### Behaviors supported

* `NEW`
* `ENRICHES`
* `SUPERSEDES`
* no-significant-delta through zero proposals

### Behaviors deferred

* `CONFIRMS`
* `CONTRADICTS`
* temporal truth engine
* deterministic contradiction detector

## AI Contract

### Changes

The architecture schema and prompt now support `SUPERSEDES`:

* `KnowledgeDeltaType.SUPERSEDES`;
* `targetInsightId` required for both `ENRICHES` and `SUPERSEDES`;
* prompt instructions tell the model to emit `SUPERSEDES` only when new evidence
  contradicts or dominates a supplied trusted item.

### Validation in Java Core

The Core validates structurally:

* `SUPERSEDES` target is required;
* target exists in selected trusted architecture knowledge;
* target belongs to the same project;
* target is `ACTIVE`;
* a rejected or invalid supersession never changes trust state.

## Lifecycle

Repository Evidence
* Existing Trusted Architecture Knowledge
  → Analysis
  → Delta Proposal (NEW | ENRICHES | SUPERSEDES | none)
  → Existing Human Validation
  → Trusted Knowledge Evolution
  → (SUPERSEDED target + `SUPERSEDES` relation on acceptance)

## Idempotence

Technical idempotence remains unchanged.

Semantic idempotence is preserved: an architecture scan with no contradiction
and no dominant new evidence returns zero proposals and changes no trust state.

A supersession is only ever decided, never automatic, so no trust-state change
occurs without a validated proposal behind it.

## Tests

### Added / updated

* backend promotion tests for accepted supersession (target `SUPERSEDED`,
  `SUPERSEDES` relation) and rejection of non-active targets;
* backend contract-validation test for valid `SUPERSEDES`;
* backend mapper test for `trustState`;
* backend controller DTO update for the added field;
* AI-engine schema validation for `SUPERSEDES`.

### Results

* backend verify: pass (647 tests)
* AI-engine full pytest: pass (52 tests)

## Quality Gates

* Backend `./mvnw verify`: **PASS**
* Backend JaCoCo check: **PASS**
* AI engine `./.venv/bin/python -m pytest -q`: **PASS**

## Limitations

1. supersession is only supported for `ARCHITECTURE_REVIEW`;
2. the trust-state marker is binary (`ACTIVE`/`SUPERSEDED`), not a full temporal
   model;
3. contradiction is surfaced through the supersession contract only; no
   deterministic contradiction detector exists yet;
4. legacy trusted insights default to `ACTIVE`; no historical audit was
   performed for current-correctness.

## Next Architectural Questions

1. Should trusted knowledge marked `SUPERSEDED` be hidden or annotated in
   Project State / freshness projections, or remain visible as historical
   truth?
2. Should the Core add a deterministic contradiction detector, or remain
   reliant on the AI surfacing supersession through the contract?
3. When should `CONFIRMS` evolve trusted provenance, and does it require a
   proposal or a separate provenance-only path?