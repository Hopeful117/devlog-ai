# Implementation Plan - Story 0179

## Objective

Run a bounded, revision-pinned runtime evaluation of Story Context composition
and provider outcomes across the four existing Story 0158 scenarios. Produce
redacted captures and an evidence-based decision report without changing
production selection, enrichment, projection, prompt, provider, or budget
policies.

## Planned Changes

### 1. Runtime preflight

- Verify `git rev-parse HEAD` and the running backend image revision where
  available.
- Verify backend, AI Engine, PostgreSQL, Keycloak, and MCP launcher health.
- Rebuild only the required runtime image when the running image does not
  include the target revision.
- Record the exact target revision and runtime command in the local evaluation
  output, not in provider prompts.

### 2. Capture runner hardening

- Reuse `ai-engine/evaluations/story_context_budget_live.py` and its existing
  authenticated MCP client.
- Add only the minimum capture fields needed to retrieve and persist bounded
  `lossAccounting` and `providerOutcome` data from the authenticated task
  snapshot.
- Preserve the current task status and analysis-validity checks.
- Record timeout, failed, incomplete, preflight-rejected, and completed states
  as distinct rows.
- Do not record full prompts, raw model responses, credentials, tokens, or
  unbounded context payloads.

### 3. Controlled scenario execution

- Execute the four scenarios from
  `ai-engine/evaluations/scenarios/story-0158-specificity-v1/manifest.json`.
- Use three repetitions per scenario by default.
- Add a unique bounded nonce only to avoid idempotency collisions; do not use it
  as an evaluation feature or causal variable.
- Capture the repository revision, scenario, repetition, task ID, context digest,
  projection digest, terminal status, elapsed time, ledger counts, loss reasons,
  projection warnings, preflight outcome, provider outcome, and bounded
  incomplete reasons.
- Store raw captures under `/tmp/opencode/story0179-runtime/` or an equivalent
  non-repository temporary directory.

### 4. Deterministic aggregation

- Add a standard-library-only aggregation command or small evaluation helper.
- Reject rows with missing task identity, missing revision, mixed revision,
  missing digest, or malformed ledger identity.
- Aggregate per scenario and overall:
  - terminal status rates;
  - valid-analysis rate;
  - timeout and provider-incomplete rate;
  - candidate/selection/enrichment/projection/grounding counts;
  - normalized loss-reason frequencies;
  - projection-reduced and preflight outcomes;
  - elapsed-time distribution where available.
- Keep provider-visible reference counts separate from selected/projected counts
  and preserve `UNKNOWN` semantics.

### 5. Decision report

- Compare the observed dominant stage with the Story 0178 single-run signal.
- Classify the next bottleneck as composition/selection, enrichment, projection,
  prompt preflight, provider execution, or unresolved variance.
- State whether hybrid retrieval remains deferred.
- Recommend at most one next bounded Story and explicitly list rejected or
  unsupported changes.

## Files Expected To Change

- `ai-engine/evaluations/story_context_budget_live.py`: bounded runtime capture
  fields only, if the existing snapshot output is insufficient.
- `ai-engine/evaluations/`: one focused standard-library aggregation helper if
  aggregation cannot remain a one-off command.
- `docs/stories/0179-story-context-runtime-loss-evaluation/evaluation-report.md`:
  measured results and limitations.
- `docs/stories/0179-story-context-runtime-loss-evaluation/decision-report.md`:
  next-action recommendation.
- `docs/stories/0179-story-context-runtime-loss-evaluation/implementation-report.md`:
  validation and documentation reconciliation.

No backend, MCP protocol, AI provider, database migration, Docker configuration,
selection policy, enrichment policy, projection policy, or budget policy change
is planned.

## Validation Plan

1. Run Python syntax compilation for changed evaluation helpers.
2. Run any existing focused evaluation tests that do not require pytest, using
   standard-library commands where applicable.
3. Run the four-scenario runtime evaluation with three repetitions.
4. Validate capture redaction, revision consistency, digest format, bounded
   output, and ledger/provider correlation.
5. Inspect the aggregated report for incomplete or invalid rows.
6. Run `git diff --check` and inspect the complete diff.
7. Run the relevant backend tests only if implementation changes unexpectedly
   touch backend behavior; otherwise record backend validation as already
   established by Stories 0177 and 0178.

## Failure Handling

- If the provider is unavailable, preserve the bounded failure rows and report
  the runtime limitation; do not retry indefinitely.
- If a task times out, do not count it as a successful analysis.
- If the running image does not match the target revision and cannot be rebuilt,
  stop the evaluation and mark the capture invalid.
- If output redaction or digest validation fails, do not publish the capture or
  report it as valid.

## Documentation Reconciliation

The Story-local evaluation and decision reports are expected deliverables. No
general operator documentation, REST contract, MCP contract, or provider
documentation should be changed unless the runtime procedure becomes a stable
supported operational workflow. Any such change requires explicit evidence from
the implementation.

## Implementation-Level Decisions

- Keep runtime captures outside Git because they may contain repository-derived
  context and provider metadata.
- Use the existing Story 0158 scenarios for comparability rather than creating a
  new benchmark corpus.
- Treat profile labels as labels only; this plan does not claim that they alter
  Core budgets.
- Keep provider reference visibility unknown until the callback contract exposes
  per-reference attribution.

## Human Approval Gate

Implementation must not begin until the human explicitly approves this current
Implementation Plan. Approval of the Repository Analysis does not approve this
plan or authorize implementation.
