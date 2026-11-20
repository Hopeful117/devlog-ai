# Story 0219 - Freshness, Scope and Provenance Explicitness

## Status

IMPLEMENTATION COMPLETE - AWAITING HUMAN ACCEPTANCE

This Story is the second planned Story of the `agent-response-reliability`
epic. It defines the explicit context metadata contract required by an agent
consumer. It does not authorize production implementation, budget changes, or
retrieval changes.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Objective

Make the deterministic limits of each Story Context Analysis explicit so an
agent can distinguish:

- which project, source, and repository revision were used;
- whether the context was current, stale, partially fresh, unavailable, or
  unknown;
- which evidence is authorized and where it came from;
- which provenance fields are missing or unresolved;
- which verification remains necessary before accepting the response.

The metadata must be produced and validated by Java Core. Python must receive
it as authorized input or execution metadata and must not reconstruct it.

## Problem

The repository already contains two complementary capabilities:

- Story 0114 introduced the path for carrying an authoritative freshness
  snapshot with Story Context Analysis, but its repository analysis identifies
  unresolved null handling, warning propagation, end-to-end test gaps, and
  REST compatibility risk.
- Story 0118 introduced revision-pinned human-authored repository documents
  and canonical document provenance. Its implementation commit is present on
  `main`, although the Story documentation still describes the implementation
  as branch-only and awaiting review.

The epic needs one explicit contract that prevents consumers from treating
these dimensions as implicit, inferred, or interchangeable.

## Scope

### In scope

- Define the authoritative metadata dimensions for a Story Context Analysis:
  project, source, resolved revision, context revision, freshness status,
  source-level freshness, provenance and bounded diagnostic limitations.
- Preserve the metadata through Core context construction, task execution,
  persistence, and REST/MCP read projections where the existing architecture
  exposes the analysis.
- Make unavailable or unknown values explicit rather than guessing them.
- Preserve source-aware, path-aware, revision-aware evidence references from
  revision-pinned repository documents.
- Reconcile the known Story 0114 capture, persistence, serialization, and test
  gaps.
- Verify that the Story 0118 revision scope and document provenance are present
  on the current `main` execution path.

### Out of scope

- AI-generated freshness, scope, or provenance.
- Changes to retrieval, ranking, knowledge selection, or context budgets.
- Automatic context expansion or recursive document discovery.
- RAG, embeddings, vector search, or a generic agent runtime.
- Semantic temporal validity of individual Facts, Insights, Decisions, or
  relationships.
- Reclassification of stale context as current.
- Promotion of AI output to trusted knowledge.
- Frontend changes unless a later approved contract explicitly requires them.

## Governing Constraints

- ADR-006: AI output remains non-trusted and subject to human validation.
- ADR-063: Core owns retrieval, composition, projection, grounding and
  capability boundaries.
- ADR-067: Python remains the probabilistic execution boundary; it does not
  reconstruct project context.
- ADR-079: budget and provider diagnostics remain distinct from freshness and
  provenance metadata.
- The epic remains design materialized; this Story does not authorize
  implementation.

## Acceptance Criteria

### AC1 - Authoritative execution scope

For every Story Context Analysis execution, Core exposes the resolved project,
repository source, and immutable repository revision used by the execution.
If a value cannot be resolved, the contract reports it as unknown or
unavailable and does not substitute an unverified current value.

### AC2 - Freshness snapshot preservation

The authoritative freshness status and source-level breakdown used to build the
context survive capture, task execution, persistence, and the existing read
projections. The statuses `CURRENT`, `STALE`, `PARTIALLY_FRESH`,
`NO_BASELINE`, and `UNKNOWN` remain distinguishable.

### AC3 - Null and warning semantics

Legitimate absent revisions and nullable freshness fields serialize without
causing retrieval failures. Aggregate and source-level warnings are preserved
as bounded metadata and are not silently discarded.

### AC4 - Provenance continuity

Every repository document evidence item exposed to the analysis retains a
deterministic source-aware, path-aware, revision-aware reference and its
provenance classification. A consumer can identify the evidence origin without
inferring it from content or model output.

### AC5 - Grounding boundary

The explicit metadata and provenance references do not broaden the authorized
grounding set. Python and MCP/REST adapters cannot add, rewrite, or infer
authorized evidence relationships.

### AC6 - Compatibility and projections

REST and MCP projections expose the additive metadata consistently, with
existing error behavior preserved where compatibility is required. The
serialization contract has direct tests for known, unknown, stale, partially
fresh, and nullable values.

### AC7 - Verification guidance

The resulting contract identifies the verification still required when
freshness, scope, provenance, or evidence completeness is unknown or limited.
It must not represent an unknown state as an acceptable current briefing.

## Required Validation

- Unit tests for freshness status, nullable fields, warnings, and provenance
  references.
- Core use-case tests covering capture and callback persistence.
- Persistence and migration verification where applicable.
- REST and MCP projection/serialization tests.
- Regression tests proving grounding allow-lists remain unchanged.
- Focused backend verification before any broader repository suite.
- `git diff --check` and final scope review.

## Dependencies and Reconciliation

- Story 0210: agent response acceptability contract and baseline.
- Story 0114: authoritative freshness snapshot path and documented gaps.
- Story 0118: revision-pinned repository document evidence, integrated in
  `main` by `db50dfbe` despite stale lifecycle wording in its documentation.
- ADR-063, ADR-067 and ADR-079.

## Deferred Decisions

- Whether the final metadata is exposed as a new versioned envelope or as an
  additive field on an existing projection.
- Whether frontend exposure is required after the Core and transport contract
  is verified.
- Any budget or retrieval policy changes, which require separate evidence and
  approval.

## Human Gate

This Story and its Repository Analysis require human approval before an
Implementation Plan may be created. The artifact status above is descriptive
only and does not grant that approval.
