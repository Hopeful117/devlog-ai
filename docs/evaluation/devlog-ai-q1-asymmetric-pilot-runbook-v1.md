# DevLog AI Q1 Asymmetric Pilot Runbook

## Status

`Q1_LIVE_ADAPTERS_AVAILABLE - Q1_LIVE_PREFLIGHT_BLOCKED - Q1_LIVE_AUTHORIZATION_REQUIRED`

This runbook prepares one observation for each of A, B, and C. Live execution
requires a separate campaign authorization document and runtime configuration.
The authorization document never contains credentials.

## Comparison Mode

The pilot is an `END_TO_END_SYSTEM_COMPARISON`:

- A uses the current DevLog one-shot Story Context path.
- B uses the direct investigator/runtime path with its actual repository tools
  through the Q1 binding.
- C uses the Core-authorized engineering analyst path with its actual context
  projection and investigation capabilities.
- Capability differences are recorded, not hidden or artificially equalized.
- The normalized claim/evidence contract is evaluation-only.
- The controlled comparison that isolates only DevLog context value is deferred.

## Frozen Observation Set

All observations use case `Q1`, repetition `1`, the frozen repository revision
`60fae3e0e1104d0ffbe3832c1f578ed532b1268a`, and the exact question:

> How does a Story Context request move from Core authorization to the final
> persisted analysis, and which side owns each invariant?

Observation IDs:

1. `Q1-A-DEVLOG-CURRENT-r1`
2. `Q1-B-DIRECT-INVESTIGATOR-r1`
3. `Q1-C-DEVLOG-ENGINEERING-ANALYST-r1`

The oracle is evaluator-only and must not enter any treatment input.

## Runner Status and Live Entry Points

`devlog_ai_abc_runner` executes the provider-free Q1 chain and captures native
outputs, bounded metadata, and normalized artifacts under one manifest. The
integration simulation also runs the local Java Core A/C harness, but it is not
a live adapter and does not use an external provider.

The actual production entry points remain separate from the provider-free
runner. Controlled adapters reuse the same application boundaries for technical
integration proof:

- A: Java Core `AnalyzeStoryContextUseCase` and
  `StoryContextSubmissionService`, followed by the existing AI callback path.
- B: the unchanged V4 direct runtime and existing pinned Git repository tool
  adapter, connected by the Q1 B binding to the DevLog AI snapshot. Its frozen
  manifest remains historical and is not repointed.
- C: Python `StoryContextAnalysisGenerationService` with Core-authorized
  investigation callbacks over loopback HTTP/HMAC in the integration harness.

- A and C are exercised by the real Java Core/Testcontainers harness; C also
  launches the real Python service path. B uses the unchanged V4 runtime and
  pinned Git tools. The static preflight reports only
  `Q1_LIVE_ADAPTERS_AVAILABLE`; integration verification is reported by an
  executed `INTEGRATION_SIMULATION` artifact, never by adapter construction.
- External live execution remains `Q1_LIVE_NOT_AUTHORIZED`. No provider
  transport is imported or constructed by the provider-free runner.

## Dry-Run Command

Run from the repository root after choosing a fresh output directory:

```bash
PYTHONPATH=ai-engine uv run --project ai-engine python -m evaluations.devlog_ai_abc_runner.runner \
  --mode DRY_RUN \
  --manifest docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json \
  --repository-root . \
  --output-root data/devlog-ai-q1-dry-run-<run-id>
```

The command refuses an existing output directory. It creates one isolated
directory per observation and writes `integrity-report.json` and
`execution-summary.json` at the run root.

## Preflight

Record PASS/FAIL without printing secret values:

```bash
git rev-parse HEAD
git status --short
python3 -m json.tool docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json >/dev/null
test -n "${LLM_API_KEY:-}" && printf '%s\n' 'LLM_API_KEY=present' || printf '%s\n' 'LLM_API_KEY=absent'
```

Verify the selected A, B, and C entry points using the approved deployment
procedure. Do not print environment files, bearer tokens, cookies, full prompts,
or unbounded repository payloads.

## Dry-Run Verification

```bash
uv run --project ai-engine --extra dev pytest \
  ai-engine/tests/test_devlog_ai_abc_runner.py -q
```

Check that the summary reports `DRY_RUN`, `providerCalls=0`,
`networkCalls=0`, and `replay=READY`. Inspect each observation's `status.json`,
then inspect `integrity-report.json`. The `derived/normalized.json` files are
the only evaluation projections; they do not replace the native RAW answers.

The dry-run artifacts from the completed validation are under:
`data/devlog-ai-q1-dry-run-001/`.

## Live Preflight Without Provider

The preflight is static and does not load credentials, instantiate a provider,
or contact a remote service:

```bash
PYTHONPATH=ai-engine uv run --project ai-engine python -m evaluations.devlog_ai_abc_runner.runner \
  --mode LIVE_PREFLIGHT \
  --manifest docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json \
  --oracle docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json \
  --repository-root . \
  --output-root data/devlog-ai-q1-live-run-<run-id>
```

The current result is expected to be `Q1_LIVE_PREFLIGHT_BLOCKED` with
`Q1_LIVE_ADAPTERS_AVAILABLE`, `Q1_LIVE_NOT_AUTHORIZED`, and zero provider and
network calls when the reviewed revision is clean. A fresh artifact target is
checked but not created by the preflight. Source snapshot, runtime HEAD,
harness identity, and effective worktree state are reported separately; live
execution must use a reviewed coherent revision, not a dirty worktree.

The preflight identifies the existing entry points and reports their controlled
integration status; it does not authorize external provider execution:

- A: `AnalyzeStoryContextUseCase` -> `StoryContextSubmissionService` -> Core
  callback/persistence.
- B: `comparative_baseline_v4.runtime` and its direct repository tools. Its
  current pilot remains Trading OS-specific and is not repointed.
- C: `StoryContextAnalysisGenerationService` -> Core investigation callback
  actions -> Core validation/persistence.

## Integration Simulation Without Provider

The runner also supports a credential-free `INTEGRATION_SIMULATION` mode. It
does not contact a provider or external network. B executes the unchanged V4
direct runtime with a scripted provider and the existing pinned Git repository
tools against the Q1 snapshot. A and C
run the local Java Core integration harness with deterministic transport and
provider boundaries. C directly exercises the Core action service; it does not
invoke Python `StoryContextAnalysisGenerationService._process_investigation`.
The Python PLAN/ACTION/FINAL loop is tested separately, so this remains
integration evidence only, not live end-to-end evidence or semantic quality.

```bash
PYTHONPATH=ai-engine uv run --project ai-engine python -m evaluations.devlog_ai_abc_runner.runner \
  --mode INTEGRATION_SIMULATION \
  --manifest docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json \
  --repository-root . \
  --output-root data/devlog-ai-q1-integration-simulation-<run-id>
```

Expected status: A/B/C integration `PASS`, `externalProviderCalls=0`,
`externalNetworkCalls=0`, `semanticQualityEvaluated=false`, and `LIVE` remains
closed with `Q1_LIVE_AUTHORIZATION_REQUIRED`. Overall readiness remains
`Q1_LIVE_PREFLIGHT_BLOCKED` until the reviewed revision, oracle, pricing,
budget, and campaign authorization gates are satisfied.

## Live Authorization Boundary

`LIVE` is a separate execution path. The authorization validator requires a
campaign-scoped document containing the exact manifest digest, accepted Q1
oracle digest/status, three observation identities and conditions, frozen
revision, provider, model, exact budgets/limits, approved pricing version and
digest, cost ceiling, approver identity, and a future expiration timestamp.
Credentials are never part of that document. Missing, expired, mismatched, or
over-broad authorization fails closed.

Pricing is an explicit external contract. The repository contains no provider
rates and therefore cannot calculate or approve a live cost ceiling. The
`PricingTable` contract requires a version, SHA-256 digest, currency, and the
applicable input/output rates before estimated or observed cost can be
computed.

No authorization or pricing document is generated by the runner. They must be
reviewed and supplied by the campaign owner.

## Authorized Live Execution

Use a clean worktree at the frozen revision and a fresh output directory. The
following variables are required and their values must not be printed:

```bash
export LLM_PROVIDER=openai_chatgpt
export LLM_MODEL=gpt-5.6-luna
export DEVLOG_CORE_BASE_URL=http://127.0.0.1:8080
export DEVLOG_CORE_BEARER_TOKEN=...
export DEVLOG_LIVE_PROJECT_SLUG=...
export DEVLOG_LIVE_STORY_ID=...
export DEVLOG_LIVE_FILES=docs/stories/0223-agentic-quality-real-story-pilot/story.md
export DEVLOG_LIVE_B_TASK_ID=...
```

Run the three bounded observations only with the separately approved files:

```bash
PYTHONPATH=ai-engine uv run --project ai-engine python -m evaluations.devlog_ai_abc_runner.runner \
  --mode LIVE \
  --manifest docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json \
  --oracle docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json \
  --authorization /secure/path/q1-authorization.json \
  --pricing /secure/path/q1-pricing.json \
  --repository-root /path/to/clean/frozen/devlog-ai \
  --output-root data/devlog-ai-q1-live-<run-id>
```

Treatment A and C use the Core HTTP submission/result boundary. Treatment B
uses the existing V4 OpenAI Responses transport and pinned Git tools. A
failure is captured as a technical failure, never retried by the runner, and
never counted as semantic quality evidence.

## Execution Contract Once Unblocked

For each observation, capture:

- manifest and observation identity;
- repository revision and runtime identity;
- prompt/schema versions;
- actual capability envelope and bounded action/tool metadata;
- provider status, latency and token metadata when exposed;
- native answer artifact;
- normalized evaluation artifact;
- SHA-256 hashes linking derived artifacts to raw inputs;
- explicit technical failure when no terminal answer is produced.

Use isolated processes and fresh artifact directories. Do not reuse conversation
state, tool caches, or provider retries across conditions.

To clean only a temporary local dry-run, remove that specific run directory
after preserving any report needed for review. Never remove historical V4
artifacts or another run directory.

## Stop Conditions

Stop before semantic scoring on revision mismatch, oracle leakage, missing
artifact identity, provider timeout without terminal classification, missing
native output, failed normalization, or a runtime mismatch with the declared
condition. An incomplete observation is not a semantic failure.

## Human Gates

Before execution, a human must approve the oracle, asymmetric mode, exact
runner/manual procedure, provider/model and cost limits, artifact destinations,
and Q1 as the first low-risk case. After execution, a human must approve the
normalized artifacts for scoring. The agent must not authorize calls or declare
semantic superiority.

The live command is intentionally separate from dry-run and integration
simulation. Supplying `--mode LIVE` without the authorization, pricing,
OAuth binding, Core boundary, or frozen clean worktree fails closed. No
`LLM_API_KEY` is required for `openai_chatgpt`; the provider resolves the
encrypted OAuth credential through the Core-issued task binding.
