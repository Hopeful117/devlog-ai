# Implementation Plan - Story 0181

## Objective

Extend the Core-owned Story Context loss ledger with bounded, digest-covered
evaluation summaries so policy alternatives can be compared offline without
changing production selection, projection, grounding, trust, or budget behavior.

## Proposed Human Decisions

1. **Evaluation-only exposure.** The new telemetry is persisted in the existing
   Story Context snapshot/ledger path for bounded evaluation and diagnostics. It
   is not added as a general REST/MCP product response unless separately
   authorized.
2. **Separate summary section.** Add a versioned summary section rather than
   increasing the existing 256 detail-entry limit. This preserves bounded ledger
   diagnostics and avoids making requested-file survival depend on reference sort
   order.
3. **Fail closed for malformed telemetry.** The evaluator marks a capture
   non-evaluable when the summary is missing, malformed, oversized, or has a
   digest/version mismatch. It must not infer missing distributions.

## Planned Changes

### 1. Define the Core telemetry model

Add a repository-consistent immutable model adjacent to
`ContextLossLedger` containing:

- bounded stage distributions by evidence kind/category;
- bounded trust-tier distributions by stage;
- bounded requested-file outcome rows;
- bounded grounding and projection summary;
- an explicit telemetry version and digest contribution.

The model must validate maximum key counts, path lengths, count ranges, allowed
stage names, allowed trust tiers, and bounded absence reasons.

### 2. Construct the summary from authoritative Core state

Extend `ContextLossLedger.capture(...)` or its immediate Core-owned collaborator
to derive the summary from:

- repository candidate evidence;
- authoritative selection decisions;
- projection references;
- Core trust mapping;
- Core grounding authorization;
- normalized requested file paths from the execution request.

The implementation must preserve the distinction between candidate, selected,
projected, and grounded stages. AI output and evaluation code must not contribute
to these values.

### 3. Preserve ledger and snapshot integrity

- Include the summary in the ledger identity/digest payload.
- Version the ledger or nested telemetry contract explicitly.
- Preserve compatibility behavior for existing snapshots according to the
  repository's current snapshot/version validation conventions.
- Keep raw detail entries bounded at 256.

### 4. Extend evaluation extraction and aggregation

Update:

- `ai-engine/evaluations/story_context_budget_live.py` to extract only the
  allowlisted summary fields;
- `ai-engine/evaluations/story_context_runtime_report.py` to aggregate category,
  trust, grounding, and requested-file outcomes;
- evaluation models/helpers as needed to validate bounds and version/digest
  correlation.

The evaluator must report absent telemetry as a readiness/evaluability failure,
not as zero evidence.

### 5. Add deterministic tests

Add focused tests for:

- category distribution construction at each stage;
- trust distribution derived from Core state;
- requested file found/selected/projected/grounded outcomes;
- missing requested files and bounded absence reasons;
- maximum key/row/path bounds;
- digest changes when telemetry changes;
- malformed or unsupported telemetry versions;
- evaluator redaction and non-evaluable classification;
- legacy ledger behavior where applicable.

Tests must not require live provider access, credentials, or repository payloads.

### 6. Run a bounded live smoke and offline comparison

After implementation and runtime synchronization:

- run one targeted scenario to verify requested-file telemetry;
- run the existing four-scenario B6000 campaign only if the smoke passes;
- aggregate current-policy telemetry;
- replay at least two instrumented alternatives offline;
- compare category concentration, requested-file survival, trust distribution,
  grounding, projected counts, and provider outcomes separately;
- produce a Story-local evaluation and decision report;
- make no production-policy recommendation if any required telemetry is invalid.

## Files Expected To Change

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/ContextLossLedger.java`
  or a directly adjacent Core-owned ledger telemetry model.
- Relevant backend Story Context ledger/snapshot tests.
- `ai-engine/evaluations/story_context_budget_live.py`.
- `ai-engine/evaluations/story_context_runtime_report.py`.
- Evaluation tests and bounded telemetry models/helpers.
- Story-local evaluation, decision, and implementation reports.

No selection policy, ranking, enrichment, projection, provider, retry, retrieval,
or context-budget implementation file is planned to change.

## Validation Plan

1. Run focused Core ledger/model tests.
2. Run focused evaluator tests.
3. Run backend compilation and relevant Story Context tests.
4. Verify telemetry bounds, redaction, digest correlation, and version handling.
5. Run a targeted live smoke with an explicitly requested file.
6. Run the bounded campaign only after smoke readiness succeeds.
7. Run offline alternative comparison using telemetry only.
8. Inspect the complete diff and run `git diff --check`.

## Failure Handling

- Missing or malformed telemetry stops policy comparison and produces a
  non-evaluable report.
- Requested-file absence remains an explicit outcome; it is not converted to a
  selected or grounded count.
- Provider timeout remains an independent execution outcome.
- Trust or grounding values missing from Core are unknown, never inferred.
- If implementing the summary changes production behavior or requires widening
  an existing public contract, stop and request a separate architectural Story.

## Documentation Reconciliation

Story-local evaluation and decision reports are expected. General API, MCP,
operator, and deployment documentation should not change unless the telemetry is
explicitly approved as a supported external contract.

## Human Approval Gate

Implementation must not begin until the human explicitly approves this current
Implementation Plan. Approval of Story 0181 and its Repository Analysis does not
authorize implementation until this gate is satisfied.
