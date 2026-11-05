# Repository Analysis - Story 0182

## Scope Reviewed

This analysis covers the Story Context loss ledger, Core selection/projection
boundaries, the Story 0181 telemetry contract, the Python offline comparator,
and the latest B6000 runtime capture at revision
`2a92d65b42e41819b4e3bd915cf486d2f1bc8b83`.

The DevLog Story Agent was unavailable in this session. Current repository
artifacts and the live capture were used as the authoritative sources for
physical state.

## Current Evidence

- The B6000 campaign contains four valid captures, one for each scenario.
- The campaign is evaluable; task statuses are three `COMPLETED` and one
  `TIMED_OUT`. Provider statuses are reported separately.
- The captures contain `2712` candidates and `240` selected/projected items.
- `2472` candidate losses are reported, with the observed category distribution
  preserved in the runtime report.
- Story 0181 telemetry preserves bounded aggregate stage and trust distributions
  plus requested-file outcomes.
- Each capture's existing loss ledger contains only `256` detail entries.
- A ledger entry exposes `reference`, `kind`, `selected`, `projected`,
  `grounded`, and `selectionReason`, but the bounded entry does not preserve the
  complete candidate set, score/rank, or requested path needed for replay.
- The current Python comparator already accepts a bounded candidate fixture and
  reports current, balanced, and unrestricted trade-offs, but no approved
  Core-generated candidate fixture exists for the live capture.

## Relevant Implementation

### Core ledger and selection

`backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/ContextLossLedger.java`
constructs the existing digest-covered bounded ledger and Story 0181 telemetry.
It is the appropriate boundary for correlating evaluation metadata, but the
existing 256-entry detail list must not be widened as a substitute for a
separate fixture.

The repository context selector and its `SelectionDecision` state remain Core
authority for candidate category, score/rank, selected state, and rejection
reason. Projection and grounding state must be read from their existing Core
outputs rather than recomputed by Python.

### Evaluation harness

`ai-engine/evaluations/story_context_budget_live.py` extracts allowlisted
runtime data and `story_context_runtime_report.py` validates and aggregates it.
`ai-engine/evaluations/story_context_policy_comparison.py` can replay bounded
candidate metadata, but it must reject fixtures that are not Core-correlated or
that omit required fields.

## Contract Gap

Aggregate distributions answer how many candidates were lost by category, but
they cannot answer which candidates an alternative policy would select. The
truncated ledger is intentionally insufficient for this purpose. A separate
bounded candidate fixture is therefore required; increasing the ledger detail
limit would conflate diagnostics with replay input and would not preserve the
full candidate set reliably.

## Proposed Fixture Semantics

The implementation plan should resolve exact DTO names and bounds, but the
fixture must provide, for every included candidate:

- stable bounded reference;
- evidence category/kind;
- normalized requested-scope path when approved;
- deterministic score or rank used by the comparator;
- Core trust tier;
- Core grounded state;
- selected/projected state;
- bounded rejection or selection reason.

The fixture envelope must include task identity, target revision, context and
projection digests, fixture version, row bound, and a digest over canonical row
ordering and values. If the complete candidate set cannot fit the bound, Core
must classify the fixture non-evaluable rather than silently truncating it.

## Risks and Open Questions

- Candidate paths may expose repository structure; path inclusion requires an
  explicit human decision and strict normalization.
- Scores may be sensitive to implementation details; the fixture should expose
  only the deterministic scalar needed for replay, not internal explanations.
- A fixture produced after selection cannot faithfully replay the current
  candidate competition; Core must emit candidates before policy allocation while
  preserving the actual runtime candidate set and revision.
- The fixture must not be made a public MCP/REST response without a separate
  contract decision.
- The current runtime capture is transient under `/tmp`; no repository artifact
  should be created until the fixture contract and retention policy are approved.

## Validation Direction

Before implementation planning is approved, validate the proposed contract
against:

1. Core candidate and `SelectionDecision` construction;
2. projection and grounding ownership;
3. requested-file matching and path redaction;
4. existing digest/version validation conventions;
5. fixture row and field bounds;
6. Python fail-closed validation and offline comparison;
7. a live capture proving fixture correlation and complete candidate coverage.

## Recommendation

Approve this Repository Analysis only after confirming the fixture fields,
maximum bounds, path policy, and evaluation-only exposure mode. Then produce an
Implementation Plan. Do not implement or change production selection policy
until the Story and the Implementation Plan have passed their respective human
approval gates.
