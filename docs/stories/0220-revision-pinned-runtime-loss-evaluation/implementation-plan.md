# Implementation Plan - Story 0220

## Status

IMPLEMENTED - VALIDATION COMPLETE.

This plan follows the approved Repository Analysis for Story 0220. It is
evaluation-first and does not authorize production behavior, public contract,
budget, retrieval, or persistence changes.

## Objective

Add a deterministic, bounded evaluation contract that correlates existing
revision-pinned Story Context snapshots and interaction-trace metadata with
terminal execution outcomes. Reuse Story 0178's `ContextLossLedger` rather than
introducing a second ledger.

## Design Boundary

The implementation will remain in `ai-engine/evaluations` unless a validation
failure proves that an existing Core snapshot or trace field cannot be consumed.

The evaluator will:

1. validate capture identity and revision consistency;
2. normalize Core/preflight/provider/callback states;
3. correlate the normalized state with `lossAccounting`;
4. decide technical readiness and semantic eligibility separately;
5. emit bounded JSON and text reports;
6. use `NOT_EVALUATED` whenever the evidence does not support a quality claim.

No provider request, prompt, model output, or Core selection policy will be
changed.

## Capture Contract

Add a versioned evaluation capture contract with these bounded fields:

```text
captureVersion
taskId
scenario
repetition
scope
freshness
contextDigest
projectionDigest
lossAccounting
terminalOutcome
analysisAvailable
semanticEligibility
captureDiagnostics
```

The capture contract will require:

- project/source/revision identity from Core-owned metadata;
- matching context and projection digests;
- the existing `story-context-loss-ledger-v1` identity and digest;
- bounded terminal outcome and provider status metadata;
- no credentials, full prompt, repository payload, or raw incomplete output.

Provider-visible evidence remains `UNKNOWN` unless the capture contains an
explicit, bounded attribution. Projected or grounded evidence alone cannot be
promoted to provider-visible evidence.

## Terminal-State Normalization

Normalize the existing surfaces to the Story vocabulary:

| Source evidence | Normalized outcome | Semantic eligibility |
|---|---|---|
| accepted preflight, valid completed callback | `COMPLETED` | eligible when capture/oracle valid |
| deterministic projection reduction | `PROJECTION_REDUCED` | eligible with bounded limitation |
| preflight rejection | `REJECTED` | `NOT_EVALUATED` |
| provider incomplete status/reason | `PROVIDER_INCOMPLETE` | `NOT_EVALUATED` |
| provider timeout | `PROVIDER_TIMEOUT` | `NOT_EVALUATED` |
| callback validation failure | `VALIDATION_FAILURE` | `NOT_EVALUATED` |
| missing, invalid, stale, or mixed identity | `NOT_EVALUATED` | `NOT_EVALUATED` |

The evaluator must preserve the distinction between Core reduction,
preflight rejection, provider failure, callback validation failure, and missing
capture evidence. It must not infer a provider outcome from `lossAccounting`
alone when the callback trace is absent.

## Proposed Files

### Evaluation implementation

- `ai-engine/evaluations/story_context_runtime_report.py`
  - extend the existing bounded capture validation only where necessary;
  - preserve current readiness checks and report compatibility;
  - add terminal-state normalization and `NOT_EVALUATED` denominator handling.
- `ai-engine/evaluations/story_context_runtime_loss.py`
  - introduce the focused capture model, normalization, correlation, and report
    functions instead of expanding unrelated ABC evaluator logic.
- `ai-engine/evaluations/scenarios/story-0220-runtime-loss-v1/manifest.json`
  - version the deterministic scenarios and expected technical/semantic gates.
- `ai-engine/evaluations/scenarios/story-0220-runtime-loss-v1/*.json`
  - provide bounded provider-free captures for complete, reduced, insufficient,
    provider-incomplete, timeout, and invalid-revision cases.

### Tests

- `ai-engine/tests/test_story_context_runtime_loss.py`
  - capture schema validation;
  - revision and digest mismatch rejection;
  - terminal-state normalization;
  - provider visibility remaining unknown without explicit attribution;
  - `NOT_EVALUATED` denominator behavior;
  - deterministic digest/report output.
- `ai-engine/tests/test_story_context_runtime_report.py` if the existing report
  tests are present or require focused additions.
- `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/usecase/ContextLossLedgerTest.java`
  - only additive regression assertions if the evaluator exposes a mismatch
    discovered at the Core snapshot boundary; no production Core change is
    planned by default.

### Documentation

- `docs/stories/0220-revision-pinned-runtime-loss-evaluation/implementation-report.md`
  - record implementation, documentation reconciliation, validation, and
    unavailable live-capture evidence.
- `docs/stories/0220-revision-pinned-runtime-loss-evaluation/decision-report.md`
  - report the measured dominant bottleneck and explicitly defer any corrective
    production Story.

No public REST/MCP documentation or runtime contract documentation is expected
to change unless implementation proves an existing surface is insufficient.

## Implementation Sequence

### Phase 1 - Freeze capture and scenario contracts

- Define the versioned capture schema and bounded field limits.
- Define the scenario manifest, revision identity rules, and expected terminal
  outcomes.
- Add fixture validation before any evaluation result is scored.

### Phase 2 - Normalize and correlate

- Read `lossAccounting`, freshness, scope, digests, and bounded trace metadata.
- Normalize technical terminal states using explicit precedence:
  provider/callback terminal failure, preflight rejection, projection reduction,
  then successful completion.
- Correlate loss reasons to the normalized outcome without changing their
  original names or counts.
- Keep provider-visible count/status unknown unless explicitly captured.

### Phase 3 - Evaluate eligibility and report

- Mark invalid, incomplete, timeout, mixed-revision, and missing-identity rows
  `NOT_EVALUATED` for semantic quality.
- Exclude `NOT_EVALUATED` rows from semantic denominators.
- Report technical counts separately from semantic quality counts.
- Bound row diagnostics and report only normalized reasons, identities, digests,
  counts, and statuses.

### Phase 4 - Replay and document

- Run deterministic Story 0220 scenarios without provider credentials.
- Verify repeated runs produce byte-equivalent structured results and stable
  digests.
- Produce the decision report identifying the dominant loss stage.
- Record whether any live capture was unavailable and preserve that result as
  `NOT_EVALUATED`.

## Scenario Set

The initial deterministic fixture set will contain:

1. complete targeted execution;
2. broad execution with Core/projection reduction;
3. revision-pinned multi-source execution;
4. insufficient evidence with correct abstention;
5. provider incomplete response;
6. provider timeout;
7. preflight rejection;
8. mixed-revision or digest-invalid capture.

The scenarios will reuse valid identity and ledger shapes from existing Story
0178/0209 fixtures where possible. They will not claim live provider quality.

## Validation Commands

```bash
python3 -m pytest ai-engine/tests/test_story_context_runtime_loss.py ai-engine/tests/test_evaluation_harness.py -q
python3 -m compileall -q ai-engine/evaluations ai-engine/tests
./backend/mvnw -pl backend -am test -Dtest=ContextLossLedgerTest,StoryContextCoreIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false -B
./backend/mvnw -pl mcp-server -am test -Dtest=StoryContextAnalysisToolTest -Dsurefire.failIfNoSpecifiedTests=false -B
git diff --check
```

The full backend suite remains a broader validation step when the repository
environment supports it. Missing provider credentials, unavailable live capture,
or unavailable tooling must be reported as blocked or `NOT_EVALUATED`, never as
passed.

## Acceptance-Criteria Mapping

| Story criterion | Planned evidence |
|---|---|
| Revision-pinned capture | capture contract and identity validation tests |
| Ledger/terminal correlation | normalization and correlation tests |
| Distinct failure classes | terminal-state scenario matrix |
| Provider incomplete is terminal | provider-incomplete fixture and evaluator test |
| Deterministic replay | manifest, fixtures, repeated digest test |
| Correct denominators | `NOT_EVALUATED` eligibility tests and report assertions |
| Dominant bottleneck report | decision report generated from normalized aggregates |
| No sensitive diagnostics | bounded-field and forbidden-field validation |
| No production behavior change | evaluation-only dependency direction and backend regression |

## Risks and Mitigations

- **Existing report compatibility:** extend current validation additively and
  keep existing Story 0158 report fields stable.
- **False provider visibility:** require an explicit attribution field; otherwise
  retain `UNKNOWN`.
- **Revision drift:** reject mismatched source revision, context digest, ledger
  digest, or projection digest before scoring.
- **Overlapping Stories:** reuse 0178 ledger and 0209 fixtures; do not create a
  second production ledger or grounding contract.
- **Unjustified production remediation:** the decision report may recommend a
  later Story but cannot authorize it.

## Documentation Reconciliation Plan

Expected outcome: Story-local documentation only. Existing runtime, REST, MCP,
and architecture documentation should not change because this plan does not
change production behavior or public contracts. The Implementation Report must
record this conclusion and the evidence supporting it.

## Approval Boundary

Implementation was authorized by the human owner after approval of the
Repository Analysis and this plan. Human Story acceptance remains separate.
