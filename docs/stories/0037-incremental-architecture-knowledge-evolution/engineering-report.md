# Story 0037 — Incremental Architecture Knowledge Evolution — Engineering Report

## Status

Reported

## ADR

### Number

ADR-050

### Title

Incremental Knowledge Evolution

### Status

Accepted

### Main decisions

* trusted knowledge may become bounded analysis input for future scans;
* idempotence remains required;
* knowledge delta is a first-class concept distinct from simple reconstruction;
* AI proposes evolution, Java Core owns lifecycle;
* trusted knowledge selection must be targeted and bounded;
* historical truth must be preserved.

### Related ADRs

* ADR-006
* ADR-047
* ADR-049

## Engineering Story

### Number

0037

### Title

Incremental Architecture Knowledge Evolution

### Scope

Architecture-only first slice:

* bounded trusted architecture knowledge input;
* structured delta payload;
* `NEW`
* `ENRICHES`
* no-significant-delta behavior;
* reuse of existing validation lifecycle.

### Status

Implemented

### Acceptance Criteria

Met.

## Previous Behavior

Repeated architecture analyses produced similar knowledge because DevLog already
had:

* deterministic repository-context selection;
* stable prompt construction;
* bounded structured output.

But trusted knowledge was only exposed as generic history, not as a dedicated
comparison target. The system therefore remained technically stable but
semantically reconstructive.

## Knowledge Context

### Source of trusted knowledge

Persisted trusted `Insight` records for the current project.

### Selection

Project-scoped, architecture-scoped, deterministic selection in
`KnowledgeSelectionServiceImpl`.

### Limit

5 existing architecture knowledge items in V1.

### Structure sent to analysis

Dedicated `existingArchitectureKnowledge` section containing trusted insight
identity, type, source classification, title, content, rationale, evidence, and
timestamps.

## Knowledge Delta

### Model retained

Payload-level delta metadata:

* `deltaType`
* `targetInsightId`

### Behaviors supported

* `NEW`
* `ENRICHES`
* no-significant-delta through zero proposals

### Behaviors deferred

* `CONFIRMS`
* `CONTRADICTS`
* `SUPERSEDES`
* `INVALIDATES`
* temporal truth lifecycle

## AI Contract

### Changes

Architecture prompt and schema now support:

* explicit trusted architecture comparison context;
* explicit delta semantics;
* explicit enrichment target identity.

### Validation in Java Core

The Core validates:

* allowed Insight types;
* allowed delta types;
* target identity restricted to selected trusted architecture knowledge.

## Lifecycle

Repository Evidence
* Existing Knowledge
  → Analysis
  → Delta
  → Proposal
  → Validation
  → Trusted Knowledge

For accepted enrichments, trusted evolution is additionally traced by a
`KnowledgeRelation`.

## Idempotence

Technical idempotence remains unchanged.

Semantic idempotence is improved because the system can now complete an
architecture scan with zero proposals when no meaningful architecture delta is
found.

This avoids redundant equivalent trusted-knowledge creation.

## Tests

### Added / updated

* backend selection tests
* backend contract-validation tests
* backend promotion traceability tests
* backend DTO coverage updates
* AI-engine prompt tests
* AI-engine generation tests

### Results

* backend verify: pass
* AI-engine full pytest: pass

## Quality Gates

* Backend `./mvnw verify`: **PASS**
* Backend JaCoCo check: **PASS**
* AI engine `./.venv/bin/python -m pytest -q`: **PASS**

## Limitations

1. legacy trusted insights without `sourceType` are only approximated through
   normalized types.
2. enrichment traceability uses `DERIVED_FROM` because the current relation
   taxonomy has no dedicated `ENRICHES` relation type.
3. contradiction and supersession lifecycles remain intentionally unimplemented.

## Next Architectural Questions

1. Should DevLog introduce a dedicated relation type for enrichment instead of
   overloading `DERIVED_FROM`?
2. Should `CONFIRMS` evolve trusted provenance without creating a proposal, or
   remain an explicit proposal class?
3. How should current-vs-historical truth be modeled once contradiction and
   supersession enter scope?
