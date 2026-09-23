# Comparative Baseline V4 Final Pilot Readiness

## Scope and Authorization Boundary

The V4 configuration is frozen and technically ready for a separately
authorized pilot. This Story did not execute the pilot, contact OpenAI, make a
network call, or authorize provider use. `officialCollectionAllowed` remains
`false`; it is an internal safety gate, not a substitute for human
authorization.

## Frozen Experimental Configuration

| Configuration | Frozen value |
|---|---:|
| `maxToolOperations` | 12 |
| `maxModelTurns` | 12 |
| `maxProviderCallsPerObservation` | 12 |
| `maxWallClockSeconds` | 120 |
| `maxReadBytesPerOperation` | 65,536 |

The manifest safety status is `HUMAN_APPROVED`. The same five values are copied
into the versioned execution configuration. Runtime construction fails closed
when supplied safety values differ from the manifest. No cumulative repository
byte ceiling was added; repository byte measures remain independent outcomes.

## Recovery Policy

The frozen policy is `NATURAL_MODEL_RECOVERY` (Policy A):

`malformed request -> deterministic contract error -> error returned to model -> natural recovery`

There is no automatic argument repair, hidden retry, evaluator/oracle hint, or
tool-call rewriting.

## Native Technical Liveness

Experimental wall-clock safety and native per-call liveness are separate.

| Component | Timeout | Scope | Reason | Failure state | Retry interaction |
|---|---:|---|---|---|---|
| OpenAI Responses request | 30 s | Each provider request | A single request must not consume the 120 s observation envelope. | `PROVIDER_TIMEOUT` via typed native timeout mapping. | SDK retries frozen at `0`; no automatic retry. |
| OpenAI connect phase | 5 s | Each provider connection attempt | Bound connection establishment separately from response read. | `PROVIDER_TIMEOUT`/transport failure according to client exception. | No retry. |
| OpenAI pool phase | 5 s | Each client pool acquisition | Bound local connection-pool waiting. | `PROVIDER_TIMEOUT`/transport failure according to client exception. | No retry. |
| Git repository operation | 15 s | Each `git` subprocess used by the pinned tool adapter | Git access is subprocess-backed and can block; ordinary in-process path validation is not artificially timed. | `TOOL_TIMEOUT` via typed native tool timeout mapping. | No adapter retry. |
| Java grounding bridge | 60 s | Evaluation-only Maven subprocess | Bound the separate grounding subprocess below the 120 s observation envelope. | Typed native tool timeout at the bridge boundary. | No bridge retry. |

The OpenAI Python SDK supports granular `httpx.Timeout` phase values and
automatic retries for selected transient failures. The configured client uses
`max_retries=0`, so SDK retries cannot exceed or hide the V4 provider-call
ceiling. The adapter has no additional retry layer. An SDK timeout is mapped to
`NativeProviderTimeout`; the V4 runtime maps it to `PROVIDER_TIMEOUT`, not
`CENSORED`.

The pinned repository adapter uses `subprocess.run(..., timeout=15)`. A native
`TimeoutExpired` becomes `NativeToolTimeout`; the V4 runtime records
`TOOL_TIMEOUT`, not `CENSORED`. The V4 worker timeout remains a separate outer
liveness guard.

## Experiment Identity

The final V4 experiment identity is:

```text
identityVersion: comparative-v4-experiment-identity-1.0.0
sha256: 01b42a88a6b94311fd912b38dbfaa4a4ceed975502cab11e80d527f1dfdad7f6
```

It binds the protocol, benchmark/hash, question identities and content hash,
oracle/hash, repository revision, both conditions, provider/model, execution
envelope, grounding and semantic contracts, recovery policy, official 18-slot
assignment plan, repetition count, and all five safety values. The frozen
execution configuration hash is:

```text
74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2
```

The manifest remains bound to repository revision
`18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`, provider `openai`, and model
`gpt-4.1-mini`.

## Pilot Assignment Plan

The pilot is a separate deterministic plan with six cells:

```text
CASE-01-COMPARATIVE:DEVLOG:r1
CASE-01-COMPARATIVE:AGENT_DIRECT_OPEN:r1
CASE-03:DEVLOG:r1
CASE-03:AGENT_DIRECT_OPEN:r1
CASE-04:DEVLOG:r1
CASE-04:AGENT_DIRECT_OPEN:r1
```

The pilot plan identity is:

```text
identityVersion: comparative-v4-pilot-plan-identity-1.0.0
sha256: 5fb719dea51b3debde68b546c1de59ab52030f0e8d76a9a5c483228f30bddeef
```

Validation confirms six unique cells, three frozen questions, two frozen
conditions, one repetition, and no mutation of the separate official plan,
which remains `3 x 2 x 3 = 18`.

## Contamination and Grounding Boundaries

Deterministic preflight and focused tests pass the condition boundaries:

- `DEVLOG` receives frozen DevLog projection data and no direct repository tool
  capability or AGENT_DIRECT history.
- `AGENT_DIRECT_OPEN` receives only the question and open pinned repository
  access; no DevLog context, expected evidence, oracle, evaluator output, or
  semantic hint is included.
- Oracle and evidence authority remain post-generation evaluation concerns.
- The direct condition has no cumulative DEVLOG-derived repository-byte budget.

Status: `PASS`.

## Replay and Persistence

Offline fixtures pass immutable RAW writing, separate DERIVED projection writing,
deterministic evaluation/projection replay, identity verification, write-once
protection, and isolated pilot storage checks. Replay reports zero provider and
zero network calls.

Status: `PASS`.

## Verification

Executed:

```bash
python -m evaluations.comparative_baseline_v4.preflight
python -m pytest tests/test_comparative_baseline_v4.py tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py -ra
python -m compileall -q evaluations/comparative_baseline_v4 evaluations/comparative_baseline/live_adapters.py
git diff --check
```

Results:

```text
preflight: PASS
configurationReady: true
pilotAuthorized: false
officialCollectionAllowed: false
official assignment count: 18
pilot assignment count: 6
provider calls: 0
network calls: 0
focused tests: 77 passed in 0.46s
compileall: PASS
```

## Remaining Defects and Uncertainties

- No live provider request has been authorized or executed; provider behavior
  beyond the documented SDK timeout/retry contract remains unmeasured.
- Daemon worker threads cannot be forcibly cancelled after an outer V4 timeout;
  native provider, Git, and bridge timeouts are therefore mandatory and remain
  separately configured.
- The 120-second value is an experimental ceiling, not a provider or tool
  timeout and not an expected usage target.
- Pilot observations may still be censored by the approved ceilings; that is a
  measured pilot outcome and must not be repaired post hoc.

## Readiness

```text
READY_FOR_HUMAN_PILOT_AUTHORIZATION
```

The only remaining human decision is explicit authorization to execute the
separate six-slot Comparative Baseline V4 pilot. The pilot itself was not run.
