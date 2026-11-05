# Implementation Plan - Story 0180

## Objective

Make the Story Context runtime evaluation fail early and report readiness
separately when task identity, repository revision, snapshot completeness, or
budget declarations are not coherent. Preserve the existing bounded capture and
aggregation behavior for evaluable rows without changing Core production policy.

## Proposed Human Decisions

The following choices are proposed for this plan and require explicit approval
with the plan:

1. **Readiness failure behavior: stop immediately.** The runner should stop the
   campaign after the first non-ready capture, write a bounded readiness report,
   and return a non-zero exit status. Partial rows remain diagnostic only and are
   never passed to campaign aggregation.
2. **Runtime synchronization ownership: operator prerequisite.** The operator or
   delivery workflow rebuilds/synchronizes the backend, MCP, and AI Engine runtime
   before execution. The runner verifies the target revision and required
   readiness metadata but does not rebuild containers or change deployment state.

These choices keep the evaluation runner deterministic and avoid introducing
production deployment or idempotency behavior into the evaluation Story.

## Planned Changes

### 1. Define the readiness envelope

Add a bounded readiness classification shared by the live runner and report
helper. The classification will include only allowlisted fields:

- target revision and observed snapshot revision;
- scenario and repetition;
- task identity and identity uniqueness state;
- context and projection digest presence/correlation;
- loss-ledger version/digest presence;
- analysis availability;
- observed and expected budget values;
- bounded failure code and short diagnostic message.

Readiness failures will be distinct from terminal provider outcomes and will not
be converted into loss reasons.

### 2. Add runner preflight and identity checks

Update `ai-engine/evaluations/story_context_budget_live.py` to:

- accept a required target revision argument;
- validate target revision format before opening the campaign;
- validate the authenticated snapshot immediately after retrieval;
- reject missing or mismatched freshness, digests, ledger, or provider outcome;
- maintain an in-memory set of task IDs and scenario/repetition identities;
- stop on the first duplicate task or non-ready capture according to the
  proposed stop policy;
- write a bounded readiness result before returning a non-zero status;
- preserve profile budget and observed runtime budget as separate fields.

The runner will add a bounded, explicitly labeled repetition marker to the
evaluation question. This is required because the Core currently falls back to
the logical submission digest after checking the supplied idempotency key. The
marker creates a distinct evaluation submission without changing Core
idempotency semantics; reports must retain the scenario identity and document the
marker as an evaluation-only variable.

### 3. Separate readiness from campaign aggregation

Update `ai-engine/evaluations/story_context_runtime_report.py` to:

- validate readiness envelopes independently from evaluable captures;
- aggregate only rows that pass readiness and capture validation;
- report readiness failure counts and bounded reasons separately;
- retain duplicate identity and mixed-revision rejection;
- avoid raising before emitting a bounded readiness diagnostic where possible;
- preserve existing strict provider, ledger, digest, revision, and redaction
  validation.

The existing aggregate schema will remain compatible for valid campaigns; new
readiness fields will be additive and versioned in the evaluation output.

### 4. Add deterministic validation coverage

Add focused standard-library tests or an existing repository-consistent test
module for:

- target revision mismatch;
- missing or malformed freshness;
- missing ledger and provider outcome;
- duplicate task IDs;
- duplicate scenario/repetition identities;
- missing or mismatched digests;
- expected versus observed budget mismatch;
- malformed bounded diagnostics;
- readiness failure exclusion from loss/provider aggregates;
- successful valid-capture aggregation unchanged.

Tests must not require live provider access or secrets.

### 5. Re-run the bounded runtime campaign

After implementation and runtime synchronization:

- verify runtime images/workspace correspond to the target revision;
- execute the four Story 0158 scenarios with three repetitions;
- stop and report if readiness fails;
- aggregate only a fully ready campaign;
- produce a Story-local evaluation report and decision report;
- make no product-policy recommendation if the campaign remains non-ready.

## Files Expected To Change

- `ai-engine/evaluations/story_context_budget_live.py`: target revision,
  readiness preflight, identity checks, and bounded readiness output.
- `ai-engine/evaluations/story_context_runtime_report.py`: readiness validation
  and separate readiness aggregation.
- `ai-engine/evaluations/`: focused deterministic tests, using the existing
  evaluation test convention if one is available.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/evaluation-report.md`:
  readiness and rerun evidence.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/decision-report.md`:
  evaluability decision and one next action at most.
- `docs/stories/0180-runtime-evaluation-readiness-and-isolation/implementation-report.md`:
  validation and documentation reconciliation.

No backend, MCP protocol, database migration, Docker, provider, retrieval,
selection, enrichment, projection, prompt, retry, or budget policy file is
planned to change.

## Validation Plan

1. Run `python3 -m py_compile` for changed evaluation modules.
2. Run focused deterministic readiness and aggregation tests.
3. Verify malformed rows are rejected without exceptions or unbounded output.
4. Verify the readiness report is bounded and contains no raw prompt/provider
   payload.
5. Verify a valid fixture campaign produces the existing aggregate totals.
6. Synchronize runtime images/workspace to the target revision outside the runner.
7. Execute the live four-scenario campaign with three repetitions.
8. Run `git diff --check` and inspect the complete Story-local diff.
9. Run applicable backend checks only if implementation unexpectedly crosses a
   backend contract boundary; otherwise record that no backend code changed.

## Failure Handling

- A readiness failure stops the live campaign under the proposed policy.
- The readiness result is preserved as bounded diagnostics outside the repository
  unless a redacted Story-local report is explicitly required.
- No invalid capture is included in loss-stage, provider, or quality conclusions.
- A runtime revision mismatch is reported as an operator/runtime synchronization
  failure, not as a Core selection or provider failure.
- A task identity collision is reported without changing Core idempotency logic.
- If the runtime cannot be synchronized, implementation stops at validation and
  the Story remains non-evaluable.

## Documentation Reconciliation

The Story-local evaluation and decision reports are expected deliverables. General
operator documentation, REST contracts, MCP contracts, and deployment
documentation should not change unless the implementation demonstrates that this
procedure is becoming a supported operational workflow.

## Implementation-Level Decisions

- Keep raw runtime captures outside Git.
- Reuse the existing Story 0158 scenario manifest for comparability.
- Keep provider-visible reference attribution `UNKNOWN` unless the callback
  contract explicitly exposes it.
- Preserve the current Core/Python authority boundary.
- Treat the current `6000` observed budget versus `32000` profile declaration as a
  readiness mismatch, not as authorization to change either value.

## Human Approval Gate

Implementation must not begin until the human explicitly approves this current
Implementation Plan. Approval of Story 0180 and the Repository Analysis does not
authorize implementation.
