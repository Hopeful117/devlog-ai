# Story 0182 - Core Redacted Candidate Fixture for Offline Selection Comparison

## Status

**READY FOR CODE REVIEW**

This Story proposes an evaluation-only, Core-authorized candidate fixture that
can be replayed by the offline selection comparison harness. It does not
authorize a production selection, ranking, retrieval, enrichment, projection,
grounding, trust, provider, or context-budget change.

## Problem

Story 0181 successfully exposes bounded aggregate telemetry, but the B6000
capture contains no complete candidate-level fixture. Its 256-entry diagnostic
ledger is truncated and does not preserve enough path, score, or rank data to
replay alternative policies without bias. The current report can identify
selection loss, but cannot honestly compare `current`, `balanced`, and
`unrestricted` policy outcomes.

## Goal

Expose a bounded, redacted candidate fixture derived by Java Core from the
authorized repository context and selection state, so the Python evaluation
harness can compare policy alternatives offline without reconstructing context
or changing production behavior.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0180 - Runtime Evaluation Readiness and Isolation
- Story 0181 - Bounded Selection Evaluation Telemetry

## Architectural Constraints

1. Java/Core remains the sole authority for candidate construction, ranking,
   selection, trust, projection, and grounding state.
2. Python may transport, validate, aggregate, and replay the fixture, but must
   not access the repository, database, or reconstruct candidate context.
3. The fixture is evaluation-only and must not become a general REST/MCP
   product response unless separately authorized.
4. Fixture rows contain bounded metadata only: no file content, prompts,
   provider payloads, credentials, tokens, or model-generated values.
5. Requested-file paths and references are normalized, bounded, and emitted only
   within the approved evaluation scope.
6. Existing production selection and budget policy remain unchanged.
7. Fixture and comparison results are correlated to one task, repository
   revision, context digest, projection digest, and ledger/fixture digest.

## In Scope

- A Core-owned bounded candidate fixture contract.
- Candidate metadata required by the offline comparator: reference, category,
  normalized path when permitted, deterministic score or rank, trust tier,
  grounded state, selected/projected state, and bounded rejection reason.
- Explicit maximum row and field limits with fail-closed validation.
- Digest-covered fixture extraction and correlation with the existing capture.
- Python extraction and validation without context reconstruction.
- Offline comparison of `current`, `balanced`, and `unrestricted` policies.
- Deterministic tests for bounds, redaction, digest correlation, and policy
  trade-off reporting.
- A Story-local evaluation report separating observations from policy decisions.

## Out of Scope

- Any production policy change or selector tuning.
- Increasing context, item, token, category, provider, or enrichment budgets.
- New retrieval channels, RAG, embeddings, vector search, or database access
  from Python.
- A supported public REST or MCP contract.
- Persisting raw repository content or provider/model payloads.
- Reinterpreting aggregate telemetry as candidate-level evidence.

## Acceptance Criteria

### AC-1 - Core-authorized candidate fixture

Every fixture row is derived by Core from the same authorized candidate and
selection state as the runtime capture. Python does not create or infer rows.

### AC-2 - Bounded redacted contract

The fixture has explicit maximum rows, field lengths, allowed categories, trust
tiers, statuses, and rejection reasons. Missing, malformed, oversized, or
unbounded fixtures are non-evaluable.

### AC-3 - Capture correlation

The fixture is correlated to the task, target revision, context digest,
projection digest, and a digest covering the canonical fixture contents.

### AC-4 - Offline policy comparison

The harness replays at least `current`, `balanced`, and `unrestricted` against
the same fixture and reports category concentration, requested-file survival,
trust, grounding, selected counts, and rejected counts separately.

### AC-5 - Authority and redaction safety

No Python-side context reconstruction, trust promotion, grounding inference,
repository/database access, raw content, prompt, credential, or provider payload
is introduced.

### AC-6 - Decision separation

The evaluation report separates valid measurement from policy recommendation and
does not recommend a production change automatically.

## Human Decisions Required

- Approve or reject this Story scope.
- Approve the candidate fixture fields and maximum bounds.
- Approve whether normalized candidate paths may be included in the evaluation
  fixture.
- Approve whether the fixture remains Story-local/transient or is exposed via an
  evaluation-only Core capability.
- Approve the Repository Analysis before Implementation Planning.
