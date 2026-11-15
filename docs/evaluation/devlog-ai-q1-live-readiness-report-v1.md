# DevLog AI Q1 Live Readiness Report

## Post-Commit Reconciliation Note

This report was written at HEAD `60fae3e0e1104d0ffbe3832c1f578ed532b1268a` before the
production corrections, harness, and documentation were committed. The sections below
are preserved as originally written. The following has been subsequently verified in
integration simulations and committed in the coherent Q1 revision (`7e41f0c` and later):

- **Treatment A**: The Q1-A Python binding (`q1_a_binding.py`) exercises the full
  Core-authorized + Python one-shot callback path. The Java Core Testcontainers harness
  with `q1.core.fullPython=true` now records `Q1_A_FULL_CALLBACK_INTEGRATION_PASS`.
  Simulation only — no live provider called.
- **Treatment C**: Python `StoryContextAnalysisGenerationService._process_investigation`
  is exercised end-to-end in the integration simulation. The C binding records two
  `PYTHON_EVIDENCE_RECEIVED` events before `PYTHON_FINAL`; Core validates the callback
  and persists a non-trusted analysis. Simulation only — no live provider called.
- **Controlled adapters**: `live_adapters.py` now provides real controlled adapters
  (`CONTROLLED_ADAPTER_AVAILABLE`) that delegate to the same integration paths used in
  tests. The static preflight reports `Q1_LIVE_ADAPTERS_AVAILABLE`; this is a technical
  integration milestone and is not a live execution authorization.
- **Pricing contract**: `pricing.py` defines the explicit versioned contract required
  before a live cost ceiling can be approved. No provider rates are bundled.
- **Identity and revisions**: The worktree discrepancy is resolved; the coherent
  production/harness set is committed. Provenance now reports source snapshot, runtime
  HEAD, and harness hash separately. The source snapshot (`60fae3e0`) is preserved as
  the Q1 historical reference.

The blocking statuses remain unchanged:

`Q1_LIVE_TECHNICALLY_BLOCKED` — `Q1_LIVE_NOT_AUTHORIZED` — `Q1_ORACLE_PENDING_HUMAN_REVIEW`

Simulation success is not live readiness. Semantic quality of model responses remains
unevaluated. Human oracle review, pricing approval, budget authorization, and explicit
execution authorization have not been obtained.

---


## Decision

- Technical decision: `Q1_LIVE_TECHNICALLY_BLOCKED`
- Authorization decision: `Q1_LIVE_NOT_AUTHORIZED`
- Provider calls during this audit: `0`
- External network calls during this audit: `0`
- No real benchmark was launched.

The credential-free integration simulation is green, including the Q1 B binding
through the unchanged V4 runtime and the real pinned Git tool adapter. It is
not sufficient for live readiness or semantic evaluation. The current worktree also contains
the production identity fix and the local harness outside the frozen commit.

## Repository State

- Branch: `story/0184-bounded-story-context-investigation`
- HEAD: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- Manifest revision: `60fae3e0e1104d0ffbe3832c1f578ed532b1268a`
- Repository: `git:github.com/Hopeful117/devlog-ai`
- Worktree: modified and contains untracked benchmark/harness files.
- Manifest canonical digest: `984461c60ea59bbd5d9535ae32b2817ab211730388586496b8a94fde74c3ca6c`

### Revision discrepancy

`StoryContextSubmissionService` has an uncommitted production change that
persists the authorized credential binding, provider, and model on `AiTask`.
The frozen HEAD does not contain these three assignments.

This change is necessary for C: Core validates investigation actions against
the task provider and credential binding before executing them. Without the
assignments, the real C action path cannot match the authorized request.

The Java Core harness, Python runner, and Q1 manifest are also currently
untracked or worktree-only. Therefore the effective code executed by the
simulation is not reproducible from the declared frozen revision alone. No
revision was silently changed and no commit was created. A human must review
and commit the coherent correction/harness set, then bind a new reference
revision before live execution.

## Frozen Benchmark Identity

- Case: `Q1`, version `1.0.0`
- Question: `How does a Story Context request move from Core authorization to the final persisted analysis, and which side owns each invariant?`
- Protocol: `END_TO_END_SYSTEM_COMPARISON`
- Observations:
  - `Q1-A-DEVLOG-CURRENT-r1`
  - `Q1-B-DIRECT-INVESTIGATOR-r1`
  - `Q1-C-DEVLOG-ENGINEERING-ANALYST-r1`
- Prompt versions:
  - A: `story-context-agent-protocol/v1`
  - B: `comparative-v4-common-answer-1.0.0`
  - C: `story-context-agent-protocol/v2`
- Q1 manifest provider/model: `openai` / `gpt-5.6-luna`
- Answer contracts:
  - A/C: `story-context-analysis-result-v1`
  - B: `comparative-v4-common-answer-1.0.0`

### B revision distinction

The existing V4 manifest is independently frozen at repository revision
`a6d3d118f87aed995ce326d1bb50e1ce9a084a31`, with its own V4 identity and
execution configuration. The Q1 B binding preserves this historical identity
while injecting the Q1 question and pinned source revision at the runtime input
boundary. The repository tool reads Q1 revision
`60fae3e0e1104d0ffbe3832c1f578ed532b1268a`; the native V4 experiment identity
continues to report its historical revision. This distinction is captured in
the B RAW artifact.

## Treatment Audit

| Treatment | Audit status | Evidence and limitation |
|---|---|---|
| A | `PARTIAL` | Java Core one-shot authorization, preparation, submission, callback validation and persistence are exercised with deterministic transport/provider boundaries. The live adapter is not connected and no real provider callback is executed. |
| B | `Q1_B_RUNTIME_INTEGRATION_PASS` | Unchanged V4 runtime executed through the Q1 binding; scripted provider invoked `read_file`; existing pinned Git tool read `StoryContextSubmissionService.java` from the Q1 commit; native output and tool trace captured. Semantic quality remains unevaluated. |
| C | `PARTIAL` | Java Core investigation action validation/execution, `SEARCH_EVIDENCE`, `READ_EVIDENCE_RANGE`, budgets, callback validation and persistence are exercised. The simulation does not invoke Python `StoryContextAnalysisGenerationService._process_investigation`; it directly calls the Core action service. |

### C PLAN/ACTION/FINAL coverage

The Python loop is covered separately by `test_story_context_investigation.py`
and related service tests, but it was not traversed end-to-end in the current
A/B/C integration simulation. Consequently C is not fully live-ready.

## Oracle Audit

- Oracle version: `1.1.0`
- Proposed digest: `464fcd1b50b785e78ddd76e0b4eea3e65ac6a85fb124b31cea9c355951fcc457`
- Four Q1 claims: present, referenced, and deterministically digest-verified.
- Normalization/scoring: format-neutral and evaluation-only.
- Oracle leakage into treatment manifest: rejected by manifest validation.
- Human semantic validation: `PENDING`.
- Human decision: not recorded.

Decision form:

| Field | Value |
|---|---|
| Oracle | `ACCEPT` / `CORRECT` / `REJECT` |
| Reviewer | `<to be recorded>` |
| Date | `<to be recorded>` |
| Recalculated digest | `464fcd1b50b785e78ddd76e0b4eea3e65ac6a85fb124b31cea9c355951fcc457` |

No provider authorization follows automatically from an oracle decision.

## Providers, Models, and Budgets

- Q1 manifest: `openai` / `gpt-5.6-luna`.
- V4 B manifest: `openai` / `gpt-5.6-luna`.
- A/C production provider identity is authorized by Core at execution time; the
  deterministic harness uses `mock` / `deterministic-v1` and is not evidence of
  the live provider configuration.
- Q1 budgets: 8 model turns, 8 provider calls, 8 action/tool operations,
  64,000 bytes/read, 8,000 tokens/read, 120 seconds, 32,768 output tokens.
- Python default runtime settings inspected: 90-second provider timeout,
  2,000 default output tokens, and up to 2 retries; these are not a complete
  Q1 campaign authorization because the runner has no connected live adapter.
- V4 native configuration: 32,768 final/intermediate output tokens, 30-second
  provider timeout, 15-second repository timeout, zero provider retries, and
  no experiment safety ceilings in its historical manifest.

Cost status: `BLOCKED / NOT CALCULABLE`. The repository contains no approved
pricing schedule for `gpt-5.6-luna`, and no real token usage exists for Q1.
Record estimated A/B/C and total cost only after a human supplies the pricing
source and approves the budget ceiling. Actual cost remains unknown.

## Artifact Audit

Provider-free simulation artifacts contain RAW, DERIVED, trace, status,
identity, revision, prompt/model-related fields where available, hashes,
references, and zero external-call counters. RAW and DERIVED replay passed,
and exclusive file creation rejects overwrites.

The live contract is incomplete: duration, provider token consumption, and cost
are not guaranteed in every observation/status artifact, especially for A/C
Core artifacts and technical failures. This blocks live readiness until the
actual live collection path is defined and verified. Simulation output is under
isolated `/tmp/opencode/q1-integration-3` and is not mixed with a future pilot
directory.

## Guardrails

| Guard | Result |
|---|---|
| `LIVE` closed by default | `PASS`; raises `LIVE_NOT_AUTHORIZED` before adapter construction |
| Oracle acceptance required | `PASS` in validator/preflight |
| Exact manifest/oracle/revision/provider/model/budgets | `PASS` in authorization validator |
| Expiration and secret rejection | `PASS` in provider-free tests |
| Existing output rejection | `PASS` in runner |
| No automatic retry in runner | `PASS`; failed observations are preserved with `retryCount=0` |
| Dirty-worktree/effective-code identity | `BLOCKED`; preflight checks HEAD only and does not detect this worktree discrepancy |
| Executable live adapters | `BLOCKED`; adapters are metadata-only |

The audit did not activate authorization or create an authorization document.

## Deterministic Validations

Executed during this audit:

- Investigation, V4, and live-preflight tests: passed.
- Java `StoryContextCoreIntegrationTest`: `2` tests passed.
- RAW/DERIVED deterministic replay of the existing simulation: passed;
  provider/network calls `0/0`.
- Manifest and oracle JSON validation: passed.
- Python runner/normalizer compilation: passed.
- `git diff --check`: passed.

Historical baseline, not rerun during this audit:

- `53` ABC runner/normalization/capability tests passed.
- Q1 B binding, runner, provenance and V4 targeted tests passed; Q1
  `INTEGRATION_SIMULATION` A/B/C integration passed with zero external
  provider/network calls.

Not executed: real provider calls, live A/B/C benchmark, semantic scoring,
human oracle review, full repository test suite, and production C loop through
the real Python-to-Core callback transport.

## GO / NO-GO Matrix

| Criterion | Status | Proof |
|---|---|---|
| Identity and revisions | `BLOCKED` | HEAD matches manifest, but effective worktree code differs and B has a separate V4 revision |
| Treatment A | `PARTIAL` | Core path tested; no executable live adapter/provider run |
| Treatment B | `Q1_B_RUNTIME_INTEGRATION_PASS` | Q1 runner invokes unchanged V4 with the existing pinned Git adapter on the Q1 snapshot; live provider and semantic scoring remain blocked |
| Treatment C | `BLOCKED` | Python PLAN/ACTION/FINAL not end-to-end connected in the simulation |
| Oracle | `PENDING` | Digest passes; human acceptance absent |
| Models and budgets | `BLOCKED` | Identities/budgets exist, but pricing and final live configuration are not authorized |
| Artifacts | `BLOCKED` | Replay/hashes pass; live duration/token/cost contract is incomplete |
| Guardrails | `PARTIAL` | Closed-by-default validation passes, but effective dirty-code identity is not checked |
| Provider authorization | `NOT_AUTHORIZED` | No campaign authorization supplied or generated |

## Launch Commands

No verified live launch command exists in the current runner. The only safe
commands prepared and executed are provider-free:

```bash
PYTHONPATH=ai-engine uv run --project ai-engine --extra dev python -m evaluations.devlog_ai_abc_runner.runner \
  --mode LIVE_PREFLIGHT \
  --manifest docs/evaluation/devlog-ai-abc-first-test-manifest-v1.json \
  --oracle docs/evaluation/devlog-ai-abc-oracle-proposals-v1.json \
  --repository-root . \
  --output-root data/devlog-ai-q1-live-run-<run-id>
```

The preflight returned `Q1_LIVE_PREFLIGHT_BLOCKED`, zero provider/network
calls, and three identified but non-executable live entrypoints. A future live
command must be defined only after the coherent revision, C transport path,
artifact contract, pricing/budget decision, oracle decision, and authorization
are separately approved.

## Human Decisions Remaining

1. Review and commit the production identity correction plus the benchmark
   harness/runner as one coherent reproducible revision, or explicitly reject
   the correction and stop C.
2. Decide whether B's historical V4 revision is acceptable for this
   end-to-end comparison.
3. Complete and verify the real C Python-to-Core callback path, including
   PLAN/ACTION/FINAL and artifact capture.
4. Complete the live duration/token/cost artifact contract.
5. Supply approved model pricing and an explicit Q1 cost ceiling.
6. Accept, correct, or reject the four Q1 oracle claims.
7. Approve Q1, asymmetric comparison, provider/model, budgets, and artifact
   destinations.
8. Create a separate campaign-scoped authorization with expiry after the
   technical blockers are resolved.

Until then:

`Q1_LIVE_TECHNICALLY_BLOCKED`

`Q1_LIVE_NOT_AUTHORIZED`
