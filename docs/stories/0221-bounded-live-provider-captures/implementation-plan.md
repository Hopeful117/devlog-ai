# Implementation Plan - Story 0221

## Status

PLAN APPROVED - IMPLEMENTATION AUTHORIZED.

This plan follows the approved Repository Analysis for Story 0221. It remains
evaluation-first and does not authorize provider credentials, production budget
changes, new persistence or retrieval changes.

## DevLog Context

The DevLog preflight was attempted and returned `401 Unauthorized`. The current
repository, accepted ADRs and related Stories are therefore the authoritative
planning inputs. This limitation must remain visible in the implementation
report.

## Objective

Project existing Core-owned execution metadata and `AiInteractionTrace` attempts
into the Story 0220 bounded runtime-capture contract so a real provider outcome
can be measured without creating a second production trace model or treating
provider output as trusted knowledge.

## Design Boundary

The implementation remains in the evaluation boundary unless a focused contract
test proves that an existing trace field cannot be safely projected. The default
direction is:

```text
Core snapshot + AiInteractionTrace
        -> bounded evaluation adapter
        -> Story 0220 capture contract
        -> live report / NOT_EVALUATED result
```

The adapter must not:

- retrieve repository context;
- reconstruct project or revision identity;
- persist a new domain entity;
- change provider retry or output policy;
- expose raw prompts, raw model output, repository payloads or credentials;
- promote evaluation results to trusted knowledge.

## Capture Contract

Extend the Story 0220 evaluation capture with:

```text
captureVersion
captureSource = LIVE_PROVIDER | DETERMINISTIC_FIXTURE
taskId
correlationId
executionScope
freshness
contextDigest
projectionDigest
lossAccounting
providerOutcome
terminalOutcome
analysisAvailable
captureDiagnostics
```

`providerOutcome` must be normalized to bounded fields:

```text
provider
model
status
visibility
outcomeSource
failureCategory
incompleteReason
effectiveOutputLimit
usage
latencyMs
```

`MEASURED` visibility requires an approved attribution source and matching task,
correlation, revision and digest identity. Missing attribution remains
`UNKNOWN`; missing identity or contradictory metadata is `NOT_EVALUATED`.

## Implementation Sequence

### Phase 1 - Freeze decisions and contract

1. Record the human decisions listed below.
2. Define the live-capture version and source discriminator.
3. Define the approved provider attribution vocabulary.
4. Define bounds for usage, latency, incomplete reasons, diagnostics and report
   rows.
5. Add a manifest containing expected identity and digest inputs.

### Phase 2 - Project existing traces

1. Read the existing `AiInteractionTrace`/trace response shape.
2. Map provider, model, status, failure, usage, latency and correlation fields.
3. Map Core-owned scope, freshness, context/projection digests and ledger.
4. Reject captures when the trace and Core snapshot disagree.
5. Ensure normal capture mode excludes diagnostic prompt/response payloads.
6. Preserve explicit unknown values instead of substituting zero, current
   revision or successful completion.

### Phase 3 - Normalize and report

1. Reuse Story 0220 terminal-state normalization.
2. Apply failure precedence: provider failure, callback validation failure,
   preflight rejection, projection reduction, then successful completion.
3. Cross-validate provider outcome and terminal callback state.
4. Keep technical status counts separate from semantic denominators.
5. Keep live and deterministic capture sources visibly separate.
6. Emit bounded JSON and human-readable reports.

### Phase 4 - Test and reconcile documentation

1. Add provider-free tests for every live projection path.
2. Add redaction and forbidden-field tests.
3. Add missing usage/latency and incomplete-reason tests.
4. Verify Story 0220 deterministic fixtures remain byte-stable.
5. Run backend trace persistence/query tests and relevant MCP compatibility
   tests if the adapter consumes transport projections.
6. Update only evaluation documentation or runbooks proven necessary.

## Scenarios

The deterministic scenario set must cover:

1. Live provider completed with full attribution.
2. Live provider `response.incomplete`.
3. Live provider timeout.
4. Transport failure.
5. Callback validation failure after provider response.
6. Projection reduced followed by completed provider response.
7. Missing provider attribution.
8. Missing or mismatched revision/digest/ledger identity.
9. Missing usage or latency values.
10. Forbidden secret, prompt, repository payload or raw response fields.
11. Contradictory provider and terminal outcome statuses.

No provider credential or network call is required for deterministic CI.

## Human Decisions Required Before Implementation

These decisions are intentionally not inferred from existing configuration:

1. **Artifact retention:** keep captures evaluation-only and file/export scoped,
   or retain them in an existing immutable technical snapshot.
2. **Retention duration:** maximum lifetime for exported live metadata.
3. **Usage scope:** token usage only, or token usage plus provider cost metadata.
4. **Attribution vocabulary:** approved values for `outcomeSource` and which
   trace fields constitute explicit provider attribution.
5. **Live acceptance:** whether at least one real provider capture is mandatory
   for Story acceptance, or whether unavailable credentials may leave the live
   portion `NOT_EVALUATED`.

## Proposed Defaults For Review

These are recommendations, not decisions:

- Keep captures evaluation-only and do not add production persistence.
- Reuse the existing diagnostic retention ceiling rather than introduce a new
  retention policy; confirm before implementation.
- Capture token usage when supplied; defer cost metadata unless a provider-neutral
  contract exists.
- Allow only `AI_INTERACTION_TRACE` and an explicitly approved provider-response
  metadata source for `MEASURED` visibility.
- Accept Story 0221 without live credentials only if the deterministic adapter,
  redaction, and `NOT_EVALUATED` behavior are fully validated.

## Proposed Files

### Evaluation

- `ai-engine/evaluations/story_context_runtime_loss.py`
  - extend the capture source and live projection contract without weakening
    Story 0220 validation.
- `ai-engine/evaluations/scenarios/story-0221-live-provider-captures-v1/manifest.json`
  - version live-capture scenarios and expected identity policy.
- `ai-engine/evaluations/scenarios/story-0221-live-provider-captures-v1/*.jsonl`
  - deterministic provider-free representations of live outcomes.

### Tests

- `ai-engine/tests/test_story_context_runtime_loss.py`
  - preserve Story 0220 regressions and add source/attribution behavior.
- `ai-engine/tests/test_story_context_live_captures.py`
  - adapter, redaction, missing measurement, identity mismatch and terminal
    correlation tests.
- Existing backend trace persistence/query tests, only if implementation
  requires a transport or DTO adjustment.

### Documentation

- `docs/stories/0221-bounded-live-provider-captures/implementation-report.md`
- `docs/stories/0221-bounded-live-provider-captures/decision-report.md`
- A canonical evaluation runbook only if the final implementation adds a new
  operator command or retention procedure.

## Validation Commands

```bash
uv run pytest ai-engine/tests/test_story_context_runtime_loss.py \
  ai-engine/tests/test_story_context_live_captures.py \
  ai-engine/tests/test_evaluation_harness.py -q
python3 -m compileall -q ai-engine/evaluations ai-engine/tests
./backend/mvnw -pl backend -am \
  -Dtest=AiInteractionTracePersistenceServiceTest,AiInteractionTraceQueryServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
./backend/mvnw -pl mcp-server -am \
  -Dtest=StoryContextAnalysisToolTest \
  -Dsurefire.failIfNoSpecifiedTests=false test -B
git diff --check
```

Unavailable provider credentials or live runtime access must be reported as
`NOT_EVALUATED`, not as a failed deterministic baseline and not as a successful
live result.

## Acceptance Mapping

| Criterion | Planned evidence |
|---|---|
| Live source is explicit | Versioned capture source and manifest tests |
| Core identity is preserved | Story 0219 executionScope/freshness tests |
| Provider attribution is explicit | Approved vocabulary and negative tests |
| Terminal states remain distinct | Provider/callback contradiction matrix |
| Incomplete output fails closed | Terminal normalization and denominator tests |
| No sensitive data | Allowlist, redaction and boundedness tests |
| Existing traces are reused | Adapter tests over `AiInteractionTrace` shape |
| Deterministic baseline unchanged | Story 0220 replay and digest regression |
| Retention is explicit | Decision report and configuration tests |
| No production authority change | Dependency-direction and regression checks |

## Approval Boundary

This Implementation Plan is ready for human approval. Approval of this plan
does not authorize implementation until the human explicitly authorizes the
implementation stage after the decisions above are resolved.
