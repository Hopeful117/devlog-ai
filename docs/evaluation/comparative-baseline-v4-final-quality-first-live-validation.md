# Comparative Baseline V4 Final Quality-First Live Validation

## 1. Authorization and Scope

One live observation was authorized and attempted:

```text
condition = AGENT_DIRECT_OPEN
question = CASE-04@1.0.0
repetition = r1
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
```

The six-slot pilot plan was not executed. DEVLOG, other questions,
repetitions, retries, and official collection were not executed.

The first preflight attempt was blocked because the shell environment did not
contain `LLM_API_KEY`. After the user confirmed that the key was in the root
`.env`, the same authorized single-slot execution was run with that `.env`
loaded in-process. The secret was not printed or persisted.

## 2. Attempt and Observation

- Attempt ID: `v4-final-quality-first-live-validation-20260918T120821Z`
- Observation ID: `v4-final-quality-first-live-validation-20260918T120821Z:CASE-04:AGENT_DIRECT_OPEN:r1`
- RAW artifact: `ai-engine/data/comparative-baseline-v4/live-pilot/v4-final-quality-first-live-validation-20260918T120821Z/artifacts/CASE-04:AGENT_DIRECT_OPEN:r1.json`
- DERIVED projection: `ai-engine/data/comparative-baseline-v4/live-pilot/v4-final-quality-first-live-validation-20260918T120821Z/derived/CASE-04:AGENT_DIRECT_OPEN:r1.json`
- Ledger: `ai-engine/data/comparative-baseline-v4/live-pilot/v4-final-quality-first-live-validation-20260918T120821Z/ledger.json`

The attempt is isolated under the non-baseline `live-pilot` namespace.

## 3. Identities and Frozen Configuration

| Identity/configuration | Observed value |
|---|---|
| Execution configuration | `f72225f4bb0d7e062dab8eb4b1120f600734fac2e51a9573e910d306c29e6ea8` |
| Experiment | `073b51104c49430f949407710aa98719e45af375c78a9ec92c69c078eadc1a62` |
| Runtime | `comparative-v4-live-runtime-contract-5.0.0` |
| Runtime digest | `a3211f44aad4633ca1d725b7a48506a6dd8725ab96becf182e867f1794e80edd` |
| Instrumentation | `comparative-v4-instrumentation-3.0.0` |
| Typed repository schema | `comparative-v4-typed-repository-tools-2.0.0` |
| Provider/model | `openai / gpt-4.1-mini` |
| Output policy | `finalOutputTokens=32768`, `intermediateOutputTokens=32768` |
| Provider retries | `0` |

Emergency guards were unchanged:

```text
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
```

## 4. Preflight

The provider-free V4 preflight passed before the live attempt:

```text
status = PASS
configurationReady = true
officialCollectionAllowed = false
providerCalls = 0
networkCalls = 0
```

The live transport preflight also passed after loading `.env`:

```text
provider = openai
model = gpt-4.1-mini
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
pilotStorageConfigured = true
```

## 5. Natural Execution Trajectory

The runtime constructed the single authorized DIRECT request with the frozen
question and typed repository tool schema. The provider returned an incomplete
Responses API object before yielding a usable final message or tool call.

Observed trajectory:

```text
one DIRECT provider request
  -> provider status=incomplete
  -> incomplete_details.reason=max_output_tokens
  -> runtime-5 response-state handling
  -> parseBoundary=NOT_REACHED
  -> no tool interaction
  -> no final answer
  -> PROVIDER_FAILURE
```

No artificial turn or tool request was injected.

## 6. Provider Response State

- Provider response status: `incomplete`
- Provider incomplete reason: `max_output_tokens`
- Provider request ID: `req_702daeec475e4a3a8ee55b7654713d01`
- Provider response ID: `resp_0406ce90f6f59d33006aad29b7aca087d2972f37dcc4843f98`
- Request phase: `RESPONSE_STATE`
- Parse boundary: `NOT_REACHED`
- Retryable: `false`
- Runtime category: `PROVIDER_INCOMPLETE_RESPONSE`
- Runtime execution status: `PROVIDER_FAILURE`
- Primary diagnostic: `PROVIDER_FAILURE`
- Stopping reason: `PROVIDER_FAILURE`

The incomplete response was not parsed, repaired, retried, or continued.

## 7. Tool and Evidence Trajectory

No usable provider tool call was produced.

```text
tool attempts = 0
valid requests = 0
invalid requests = 0
executed requests = 0
runtime tool failures = 0
skipped requests = 0
repository searches = 0
repository reads = 0
Git operations = 0
repository bytes delivered = 0
```

No DIRECT DevLog context or provider-visible DevLog evidence was supplied.

## 8. Final Answer and Quality Evaluation

No final answer was produced:

```text
final answer = NO
structural validity = NOT_EVALUATED
semantic correctness = NOT_EVALUATED
primary outcome = NOT_EVALUATED
```

This is not a semantic failure and not evidence about CASE-04 correctness.

## 9. Resource Usage

The provider transport was invoked exactly once, evidenced by the provider
request/response IDs. No retry occurred.

| Metric | Value |
|---|---:|
| Actual provider transport requests | 1 |
| Network model calls | 1 |
| Response-bearing model turns | 0 |
| Runtime `totalProviderCalls` field | 0 |
| Attempted provider requests | 1 |
| Technical provider retries | 0 |
| Input tokens | `NOT_MEASURED` |
| Output tokens | `NOT_MEASURED` |
| Total tokens | `NOT_MEASURED` |
| Tool attempts | 0 |
| Serialized request bytes | 671 |
| Repository bytes | 0 |
| Provider latency | `NOT_MEASURED` |
| Assignment latency | 2551 ms |
| Cost | `NOT_AVAILABLE` |

The runtime's `totalProviderCalls=0` reflects that `record_provider()` is only
called after a usable `ProviderResponse` is returned. It does not erase the
fact that one transport request was attempted; the provider request ID is
persisted in the failure diagnostics. This accounting distinction is recorded
as a limitation and was not modified during the authorized validation.

Configured capacity was not recorded as consumed usage.

## 10. Output Ceiling and Emergency Guards

```text
QUALITY_FIRST_OUTPUT_CEILING_DID_TERMINATE_OBSERVATION
reason = max_output_tokens
```

The observation was not terminated by an emergency guard:

```text
emergency guard termination = NO
```

No tool, model-turn, provider-call, wall-clock, or read-byte guard was hit.

## 11. Replay and Integrity

Deterministic replay passed:

```text
replayed = true
replay provider calls = 0
replay network calls = 0
```

Integrity checks passed:

- Exactly one RAW artifact exists in the attempt.
- Exactly one DERIVED projection exists in the attempt.
- Ledger contains exactly one finalized assignment.
- Assignment is exactly `CASE-04:AGENT_DIRECT_OPEN:r1`.
- Ledger summary is complete with no missing, unexpected, or duplicate assignments.
- RAW output hash is valid.
- Artifact hash is valid.
- DERIVED projection hash is valid.
- RAW-to-DERIVED observation and output-hash links are valid.

Isolation passed: the observation is in `live-pilot`, no DevLog context was
provided to DIRECT, and no official artifact was created.

## 12. Historical Preservation

Previous pilots, validations, official attempts, historical RAW/DERIVED
artifacts, reports, notebooks, and historical identities were not modified.

## 13. Technical Validity Assessment

The token policy and runtime identities were correct. The runtime-5 incomplete
response contract behaved as specified. However, the provider independently
terminated the only request before any usable DIRECT treatment interaction.

Therefore this validation does not establish a technically valid natural
treatment trajectory and does not authorize official collection.

The result is not a semantic failure. No protocol tuning, retry, second
validation, or additional observation is authorized by this report.

## 14. Mandatory Answers A-Z

- A: `1` authorized.
- B: `1` executed after `.env` correction; the earlier missing-key attempt made no provider call and no observation.
- C: `AGENT_DIRECT_OPEN`.
- D: `CASE-04@1.0.0`.
- E: `r1`.
- F: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.
- G: `f72225f4bb0d7e062dab8eb4b1120f600734fac2e51a9573e910d306c29e6ea8`.
- H: `073b51104c49430f949407710aa98719e45af375c78a9ec92c69c078eadc1a62`.
- I: `comparative-v4-live-runtime-contract-5.0.0`.
- J: `comparative-v4-instrumentation-3.0.0`.
- K: `comparative-v4-typed-repository-tools-2.0.0`.
- L: `32768`.
- M: `YES` for deterministic preflight; live transport preflight also `YES` after loading `.env`.
- N: `NO` usable interaction; one provider response was received as incomplete.
- O: `NO`.
- P: `0`.
- Q: `0 / 0 / 0` valid / invalid / executed.
- R: `NO`.
- S: `PROVIDER_FAILURE`.
- T: `NOT_EVALUATED`.
- U: `NOT_EVALUATED`.
- V: `NOT_EVALUATED`.
- W: `YES`, provider status `incomplete`, reason `max_output_tokens`.
- X: `NO`.
- Y: `YES`; replay, RAW/DERIVED integrity, ledger, hash links, and isolation all passed.
- Z: `NO`; the provider outcome was inconclusive before usable treatment interaction, with an accounting limitation recorded separately.

## 15. Technical Validity Answers AA-AH

- AA: `YES`.
- AB: `YES` as a configured/runtime accounting invariant; actual usage was not measurable in this incomplete response.
- AC: `YES`; runtime-5 classified the response as `PROVIDER_INCOMPLETE_RESPONSE`, did not parse it, and did not retry or continue.
- AD: `NOT_EXERCISED`; no usable tool request was emitted.
- AE: `NO`.
- AF: `NO`.
- AG: `NO`.
- AH: `NO`; no additional protocol-hardening iteration is justified by this provider outcome.

## 16. Classification and Readiness

```text
QUALITY_FIRST_LIVE_VALIDATION_PROVIDER_OUTCOME_INCONCLUSIVE
NOT_READY_FOR_OFFICIAL_COLLECTION
```

Exact next human decision:

> Review the single recorded provider-incomplete outcome and decide whether the protocol remains stopped for human review. No second validation, retry, tuning, or official collection is proposed by this report.

QUALITY_FIRST_LIVE_VALIDATION_PROVIDER_OUTCOME_INCONCLUSIVE
NOT_READY_FOR_OFFICIAL_COLLECTION
STOP_FOR_HUMAN_REVIEW
