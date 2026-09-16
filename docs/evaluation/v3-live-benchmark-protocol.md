# V3 Live Benchmark Protocol Design

## Status

```text
PHASE = V3_LIVE_BENCHMARK_PROTOCOL_DESIGN
MODE = PAIR
STATUS = PRE_SMOKE_INFRASTRUCTURE_IMPLEMENTED
NEW_PROVIDER_CALLS_ALLOWED = 0
V3_SMOKE_RUN_AUTHORIZED = NO
V3_FULL_RUN_AUTHORIZED = NO
STORY_0132_REOPENED = NO
STORY_0133_AUTHORIZED = NO
```

This document defines a separate evaluation protocol. The evaluation-only
pre-smoke primitives described near the end are implemented, but no live V3
execution has occurred. This phase does not alter production behavior or
change the Story0132 contract, prompt, model, provider, retrieval, RAG, ground
truth, or thresholds.

## Purpose

V3 must establish whether the Story Context Analysis causal interpretation path
has measurable semantic capability under a frozen, inspectable input. It must
separate engineering correctness, provider execution, structural validity,
evidence validity, and semantic interpretation. A semantic score is permitted
only after the lower-level gates pass.

The protocol is evaluation infrastructure. It is not a generalized experiment
platform and does not become a production persistence model.

## V2 Lessons

The minimum material lessons are:

1. A provider response is not a valid semantic answer merely because it is
   captured. Transport, schema, Core, reference, and evidence gates must run
   before scoring.
2. Historical context identity cannot be reconstructed safely from a later
   selected-knowledge projection. The exact task identity and selected snapshot
   must be captured with the response.
3. Repository identity and revision are insufficient by themselves. The exact
   provider-visible evidence content must be frozen.
4. Locator resolution and digest calculation must use the same textual bytes in
   the provider snapshot and the authoritative Core snapshot. Newline behavior
   is part of the contract.
5. Raw provider response, finish reason, token usage, parsed response, retry
   attempts, and validation outcomes are required to diagnose truncation and
   contract failures.
6. Invalid structural/evidence slots must not become semantic zeroes. They can
   invalidate the benchmark denominator while remaining useful diagnostics.
7. A replay must consume frozen artifacts. It must not need provider access,
   current repository state, or historical reconstruction.

The V2 cases remain useful as candidate smoke material, but they are not
automatically accepted as the V3 full benchmark. Each case requires a fresh
human review of discriminating value, exact evidence availability, and
provider-visible snapshot completeness before the V3 manifest is frozen.

## Evaluation Invariants

```text
PROVIDER_OUTPUT_IS_UNTRUSTED = YES
JAVA_CORE_REMAINS_AUTHORITATIVE = YES
SEMANTIC_SCORING_REQUIRES_ALL_PRIOR_GATES = YES
INVALID_SLOTS_ARE_NOT_SEMANTIC_WRONG_ANSWERS = YES
HISTORICAL_ARTIFACTS_ARE_APPEND_ONLY = YES
PROVIDER_CALLS_ARE_PRECOMPUTABLE = YES
MODEL_SELECTION_BY_HARNESS = NO
```

V3 uses the existing Java Core validation boundary. It does not weaken context
digest validation, typed reference authorization, evidence locator validation,
or causal contract validation.

## EvaluationRun

`EvaluationRun` is an evaluation-only immutable record. It must not be added to
the production database or domain model merely to support V3.

### Run and Slot Identity

The run manifest freezes the benchmark and execution configuration. Each slot
is identified by:

```text
(evaluationRunId, caseId, questionId, repetition)
```

The identity is unique and validated before provider execution. A duplicate,
missing, or unknown slot invalidates the run manifest before live execution.

### Field Contract

| Field | Classification | Requirement |
|---|---|---|
| `evaluationRunId` | Required, frozen | UUID/opaque run identity; generated before execution. |
| `benchmarkVersion` | Required, frozen | Manifest version and benchmark suite version. |
| `caseId`, `questionId`, `repetition` | Required, frozen | Slot identity; repetition is one-based. |
| `capturedAt` | Required | UTC timestamp; diagnostic, not an input identity. |
| `provider`, `model`, safe provider configuration | Required, frozen | Provider/model identifiers and non-secret generation settings. |
| `repositoryIdentity`, `repositoryRevision` | Required, frozen | Repository identity and exact revision. |
| `aiTaskId`, `correlationId` | Required, captured | Exact task/callback identity; correlation IDs are transport metadata. |
| `contextDigest` | Required, captured | Core-owned task context digest. |
| `selectedKnowledgeSnapshot` | Required, captured | Exact Core-owned task snapshot, not a later reconstruction. |
| `providerProjection` | Required, captured | Deterministic projection actually passed to prompt construction. |
| `providerVisibleEvidence` | Required, captured | Exact evidence records and text visible to the provider. |
| `promptRepresentation` | Required, captured | Exact system message, user message, schema representation, and rendering metadata. |
| `promptDigest` | Required, derived | SHA-256 of the canonical rendered prompt representation. |
| `schemaIdentifier`, `schemaVersion`, `schemaDigest` | Required, frozen | Output contract identity used for the provider request. |
| `mappingVersion`, `mappingHash` | Required, frozen | Human-approved benchmark mapping identity. |
| `rawProviderRequestMetadata` | Required, redacted | Safe request ID, endpoint/provider operation, settings, and timing. No auth headers or credentials. |
| `rawProviderResponse` | Required, captured | Exact provider response body/structured payload after the defined safe capture boundary. |
| `rawResponseSha256` | Required, derived | Integrity hash of the captured raw response representation. |
| `finishReason` | Required when supplied | Provider termination reason; absent is an explicit `NOT_REPORTED` value. |
| `tokenUsage` | Required when supplied | Input/output/total tokens and output limit; missing values are explicit nulls. |
| `parsedResponse` | Derived, captured | Result of parsing the raw response against the frozen schema. |
| `structuralValidation` | Required, derived | Parse/schema/required-field result and diagnostics. |
| `coreValidation` | Required, derived | Java Core result and error classification. |
| `evidenceValidation` | Required, derived | Reference, locator, content, and digest result. |
| `semanticScoringEligibility` | Required, derived | Boolean plus gate summary and reason. |
| `finalSlotStatus` | Required, derived | Terminal status from the validity model below. |
| `attempts` | Required, captured | Ordered technical/semantic attempt records; never only the selected attempt. |
| credentials, auth headers, API keys, secrets | Secret/redacted | Never persisted. Secret detection fails closed before provider execution. |

The run artifact stores the exact `SelectedKnowledge` domain snapshot, the
provider projection, and provider-visible evidence separately. Equality or
transformation relationships between them are explicit fields, not assumptions.

## Provider-Visible Context

The frozen context chain is:

```text
Core SelectedKnowledge snapshot
        ↓ deterministic projection
provider projection
        ↓ evidence extraction without content rewriting
provider-visible evidence snapshot
        ↓ deterministic prompt rendering
exact system/user prompt representation
        ↓ provider request
captured response and metadata
```

Every node is captured. Each transformation records its source digest and
output digest. The canonical prompt digest covers the exact system message,
user message, schema representation, and rendering version. The selected
knowledge digest is not treated as a prompt digest.

V3 should pass raw authoritative evidence content to the provider projection.
If a presentation projection is necessary, it must preserve a one-to-one map to
the captured provider-visible content and declare that representation as the
locator domain. V3 must not silently locate against a different normalized,
trimmed, or reconstructed text.

## Evidence Snapshot Contract

Each provider-visible evidence item contains:

```text
reference
sourceType
repositoryIdentity
repositoryRevision
path/resource identity or immutable commit identity
exact content text
contentEncoding = UTF-8
contentByteLength
contentSha256
locatorContractVersion
stable ordering index
```

Repository files preserve the exact textual representation supplied to the
provider, including terminal newline bytes. Commit evidence preserves an
immutable commit identity and the exact rendered commit evidence text supplied
to the provider, plus its digest. A later checkout is verification metadata,
not the source of replay content.

The evidence snapshot is frozen before provider execution and copied into the
slot artifact. It is not inferred from a repository path after the run.

## Locator Authority

The current Java locator contract is retained unchanged:

```text
LINE_RANGE = one-based inclusive
SECTION = exact Markdown heading bounded by the next heading of equal or lower level
CONTENT = raw selected repository evidence content
```

V3 alignment rule: the content used by the provider-visible evidence snapshot,
the task selected-knowledge snapshot, the Python diagnostic resolver, and the
Java evaluation bridge must be byte-equivalent UTF-8 content. No fuzzy heading
matching, automatic trimming, newline conversion, or excerpt repair is
allowed. Java remains the authority for acceptance. Python may diagnose a
failure, but may not replace authoritative content.

## Structural Gate

The provider response pipeline is:

```text
provider response
      ↓
GATE 1 — transport completeness
      ↓
GATE 2 — JSON/schema/required-field validity
      ↓
GATE 3 — task/context identity
      ↓
GATE 4 — reference authorization
      ↓
GATE 5 — evidence assertion validity
      ↓
GATE 6 — business/causal contract validity
      ↓
semantic scoring eligibility
      ↓
semantic metrics
```

The first failing gate is recorded. Later gates do not run for that slot, and
the slot cannot enter semantic scoring. A structural failure is never converted
to a semantic incorrect answer.

The structural record captures finish reason, input/output/total token usage,
configured output limit, observed response byte length, provider request ID,
provider error, and retry history where supplied. Output limits are frozen in
the manifest only after measuring representative valid output sizes; V3 does
not blindly increase a token limit.

### Gate Definitions

| Gate | Authority | Pass condition | Failure | Continue? |
|---|---|---|---|---|
| Transport completeness | Evaluation runner/provider adapter | Response received, request identity matches, no provider execution error | `INVALID_EXECUTION` | No semantic processing. Technical retry only under policy. |
| Structural/schema | Python frozen schema contract | Complete parse, required fields, schema digest matches | `INVALID_STRUCTURAL_OUTPUT` | No later gates. |
| Context identity | Core task metadata and run artifact | `AiTask`, context digest, selected snapshot, and prompt traceability agree | `INVALID_CONTRACT` | Stop slot; run circuit breaker may stop run. |
| Reference authorization | Java Core mapping/grounding contract | Every reference is authorized and in scope | `INVALID_CONTRACT` | Stop slot. |
| Evidence validity | Java Core snapshot resolver | Locator resolves, content/digest/excerpt assertions are valid | `INVALID_EVIDENCE` | Stop slot. |
| Business/causal contract | Java Core authoritative validator | Question identity, cardinality, roles, classification, and causal constraints pass | `INVALID_CONTRACT` | Stop slot; not semantic scoring. |
| Semantic scoring | Evaluation scorer | All prior gates pass and benchmark remains valid | `NOT_EVALUATED` if not eligible | No fallback score. |

## Retry Policy

### Technical Retry

Technical retry is permitted only when no valid semantic output exists because
of network transport, provider availability, timeout, or rate-limit execution
failure. It is not permitted after a response has been received and parsed.

The V3 default is at most one technical retry per slot, with the same frozen
input and provider configuration. Each attempt is captured in order. The retry
does not replace the failed attempt; both remain auditable. The run manifest
precomputes the maximum call count.

### Semantic Retry

The benchmark harness must not request a corrective semantic answer or select a
preferred answer. The default V3 semantic retry policy is `DISABLED` so that a
slot measures one semantic attempt without hidden answer selection.

If a future authorized protocol explicitly evaluates the production corrective
retry path, every attempt must be captured, the retry reason must be Core/Python
contract output rather than harness preference, and the manifest must define
eligibility before execution. The harness may never choose the best attempt.

## Benchmark Validity

The smallest coherent state model is:

```text
VALID
INVALID_STRUCTURAL_OUTPUT
INVALID_CONTRACT
INVALID_EVIDENCE
INVALID_EXECUTION
INCOMPLETE
NOT_EVALUATED
```

Slot status is independent from benchmark status. The benchmark becomes invalid
when any frozen slot is missing, duplicated, structurally invalid, contract
invalid, evidence invalid, or execution-invalid in a way that prevents the
predeclared semantic denominator. A benchmark with complete slots that are
semantically wrong remains valid; semantic failure is not infrastructure
failure.

`INCOMPLETE` is used when execution did not reach all frozen slots without a
single gate classification establishing a complete run. `NOT_EVALUATED` is a
metric/slot eligibility state, not a successful answer.

## Semantic Denominators

Denominators are frozen in the benchmark manifest before any provider call.

| Metric | Expected denominator | Eligible observations | Invalid-slot behavior |
|---|---:|---|---|
| Positive causal accuracy | Number of positive question/repetition slots | Gated slots with valid causal assessments | No substitution with zero; benchmark invalid if expected denominator cannot be met. |
| CASE-04 abstention | Frozen CASE-04 repetitions | Valid CASE-04 assessments | Structural/evidence failures are invalid, not `NOT_ESTABLISHED` or semantic wrong answers. |
| Causal stability | Frozen question groups with all required repetitions valid | Valid assessment signatures only | Group is ineligible; benchmark invalid if frozen completeness is lost. |
| Causal overclaim rate | Frozen negative-control slots | Valid negative-control assessments | Invalid slots do not count as overclaims. |
| Evidence validity | All slots requiring evidence assertions | Gate-5 results | Diagnostic gate metric; not a semantic score. |
| Reference authorization | All cited references in parsed valid responses | Gate-4 results | Diagnostic gate metric; unauthorized output cannot score semantically. |
| Role admissibility | Valid causal assessment slots | Valid role/classification combinations | Invalid slots excluded and reported separately. |

Every semantic metric is reported with:

```text
value
eligible
observedDenominator
expectedDenominator
benchmarkValid
reasonIfInvalid
```

Mechanical diagnostic values are under a separate `mechanicalDiagnostics`
namespace and cannot be presented as semantic model scores.

## Frozen Benchmark Mapping and Ground Truth

Before execution, the manifest freezes:

```text
cases
question identities
source and target
expected relation
positive/negative classification
expected repetitions
semantic thresholds
schema identity and digest
mapping version and hash
repository identity and revision
```

The V2 mapping can be reused as a draft or smoke candidate only. It cannot be
silently reused as the V3 full mapping. A human must reapprove whether each
question has sufficient discriminating value and whether its evidence snapshot
supports an inspectable answer.

Ground-truth authority is ordered as follows:

1. Human-approved causal relation and expected classification.
2. Accepted ADR and Engineering Story authority for project claims.
3. Repository factual evidence at the frozen revision.
4. Deterministically derived metadata such as content digests and locator
   resolution.

The evaluated model never generates ground truth. If AI assists with draft
preparation later, human approval, provenance, and final mapping hash are
required before any provider call.

## Smoke Protocol

The smoke test is an infrastructure qualification, not a semantic score. The
minimum candidate is three slots, one repetition each:

| Slot | Boundary exercised | Reason |
|---|---|---|
| One positive V2 causal question, preferably CASE-01 | Positive causal assessment, authorized evidence, bounded locator, Core acceptance | Proves the affirmative path is inspectable without using a previously passing result as proof. |
| CASE-04 negative control | Explicit `NOT_ESTABLISHED` abstention, fixed question identity, non-causal evidence handling | Detects causal overclaim and negative-control contract failures. |
| One evidence-heavy positive question, preferably CASE-03 | Multiple evidence assertions, ordering, digest/excerpt fidelity, structural output size | Exposes truncation, assertion, and evidence snapshot defects with one additional call. |

The exact positive candidates must be selected during manifest review after
fresh evidence-snapshot validation. The choices above are candidates, not a
frozen oracle.

Smoke success requires every selected slot to pass transport, schema, context,
reference, evidence, and Core validation; the complete artifact must pass
replay without provider/network/current-repository access; and the smoke
manifest must remain hash-consistent. Any infrastructure, structural,
identity, reference, evidence, or Core failure means:

```text
SMOKE_PASS_GATE = FAIL
FULL_V3_RUN_AUTHORIZED = NO
```

With one technical retry permitted and semantic retry disabled:

```text
SMOKE_SLOT_COUNT = 3
SMOKE_PROVIDER_CALLS_EXPECTED = 3
SMOKE_PROVIDER_CALLS_MAXIMUM = 6
```

## Full-Run Authorization Gate

The full run requires all of the following, explicitly approved before
execution:

1. Smoke infrastructure gate passes.
2. Smoke artifacts replay successfully offline.
3. Artifact completeness and redaction tests pass.
4. Frozen V3 mapping, evidence snapshots, schema, and thresholds are human
   approved and hashed.
5. No unresolved production contract defect exists.
6. Expected and maximum provider calls, retry policy, and cost estimate are
   recorded.
7. A human explicitly authorizes the full run.

This design does not grant that authorization.

## Cost Controls and Circuit Breaker

Before execution the harness reports:

```text
slot count
technical retry maximum
semantic retry policy
expected provider calls
maximum provider calls
estimated input bytes/tokens
configured output limit
estimated maximum output bytes/tokens
```

The circuit breaker stops execution immediately on:

- benchmark/schema/mapping hash mismatch;
- first smoke transport, structural, context, reference, evidence, or Core
  failure;
- provider-visible evidence or repository revision mismatch;
- Core bridge unavailable;
- repeated identical infrastructure failures in a full run, with the threshold
  frozen in the manifest.

The circuit breaker does not stop merely because a valid model answer is
semantically incorrect.

## Replay Guarantee

```text
V3_REPLAY_GUARANTEE = COMPLETE_OFFLINE_REPLAY_FROM_FROZEN_ARTIFACTS
```

A completed slot can be replayed without provider access, network access,
current repository or branch state, reconstructing provider-visible evidence,
or guessing task/context identity. Replay reads the manifest, run/slot record,
exact prompt representation, exact evidence snapshot, parsed/raw response, and
then invokes the real deterministic validators and scorer. The replay must
prove its input hashes before validating the response.

## Artifact Hierarchy

The minimal V3 layout is:

```text
evaluation/v3/<benchmark-version>/
  benchmark-manifest.json
  smoke/<evaluation-run-id>.json
  live/<evaluation-run-id>.json
  summaries/<evaluation-run-id>.json
```

The run artifact contains ordered slots and attempts, including raw response
representations. A separate raw capture file is allowed only when size or
storage policy requires it; it must be content-addressed and referenced by the
run artifact. The summary contains derived metrics and validity, never the only
copy of raw or provider-visible data.

Artifacts are immutable after finalization. Corrections produce a new artifact
with a new identity and an explicit relationship to the prior artifact; prior
artifacts are never overwritten.

## Hash Hierarchy

Only hashes with distinct boundaries are retained:

| Hash | Protects | Produced/validated by |
|---|---|---|
| Benchmark mapping hash | Cases, questions, oracle, repetitions, thresholds | Manifest builder / loader |
| Schema digest | Required output schema | Schema generator / structural gate |
| Context digest | Core task context identity | Java Core; compared at callback/replay |
| Evidence content SHA-256 | Exact provider-visible source text bytes | Snapshot builder; evidence gate |
| Prompt digest | Exact rendered system/user/schema representation | Prompt builder; run artifact |
| Raw response SHA-256 | Captured provider response representation | Capture adapter; replay integrity check |
| Run artifact SHA-256 | Final immutable run record | Artifact finalizer; offline verifier |

No hash is introduced solely to duplicate another integrity boundary.

## Security and Redaction

The capture boundary excludes authorization headers, API keys, credentials,
environment variables, and unrelated provider configuration. Safe metadata may
include provider/model identity, request ID, finish reason, token usage, timing,
and non-secret generation settings.

The harness performs a deterministic secret scan before provider execution. A
secret-like value in the proposed prompt/evidence snapshot fails closed rather
than being silently redacted, because silent redaction would invalidate the
exact provider-visible context. Request metadata is redacted defensively using
the existing interaction-trace rules. The artifact records the redaction policy
version and failure reason without persisting the secret.

## Existing Infrastructure Classification

| Existing component | Classification | V3 use |
|---|---|---|
| `loader.py` benchmark/oracle validation | `REUSE_AS_IS` with V3 manifest adapter | Preserve frozen-input and human-oracle checks; add V3 manifest completeness around them. |
| `repository_ground_truth.py` | `EXTEND` | Keep exact revision checks, but freeze exact content into the provider snapshot rather than relying on later checkout. |
| `causal_mapping.py` | `EXTEND` | Reuse identity mechanics; require a separately approved V3 mapping hash. |
| `v2_live_runner.py` | `REPLACE` for V3 orchestration | Its V2-specific provider/path assumptions and replay normalization do not become V3 policy. Reuse pure resolver/diagnostic logic only where proven equivalent. |
| `core_validate_capture.py` and `CoreV2EvaluationBridgeTest` | `EXTEND` | Reuse the real Core bridge with a V3 artifact input and no identity rewrite. |
| `interaction_trace.py` / `AiInteractionTrace` | `EXTEND` | Preserve ordered attempts, validation status, finish/token metadata, and safe redaction. |
| `interpretation.py`, `causal_evaluation.py`, `scorer.py` | `EXTEND` | Separate gate diagnostics from semantic metrics and require eligibility metadata. |
| `live_capture.py` | `NOT_RELEVANT` for causal V3 execution | It captures context-only DevLog output and does not contain provider-visible causal evidence. |
| ADR-066 scenario loader/replay harness | `REUSE_AS_IS` for general artifact discipline | Its schema/replay checks are useful, but its architecture-overview scenario is not the V3 causal manifest. |

## Implementation Delta

### Required Before Smoke

| Change | Area | Reason | Boundary | Complexity | Pedagogical suggestion |
|---|---|---|---|---|---|
| Add V3 benchmark manifest model/validator | `ai-engine/evaluations/product_value/` | Freeze slots, mapping, hashes, retry policy, denominators, and call budget before execution | Evaluation | Medium | Pair |
| Add immutable run/slot artifact serializer | Evaluation capture runner | Capture exact task, snapshots, prompt, response, metadata, attempts, and gate results | Evaluation | Medium | Delegate after contract review |
| Capture provider-visible evidence bytes before execution | Repository/context capture path | Eliminate V2 evidence reconstruction ambiguity | Evaluation using existing Core data | Medium | Pair |
| Add offline replay verifier | Evaluation + existing Core bridge | Prove no provider/network/current repository dependency | Evaluation | Medium | Pair |
| Add fail-fast gate composition and benchmark validity | Existing scorer/runner | Prevent structural/evidence failures from becoming semantic scores | Evaluation | Medium | Pair |
| Add secret preflight and artifact redaction tests | Interaction trace/evaluation capture | Prevent unsafe or non-reproducible artifacts | Evaluation | Small | Delegate |
| Add deterministic newline/content fidelity tests | Resolver/evidence snapshot tests | Protect provider/Core byte equivalence | Evaluation and existing Core test boundary | Small | Pair |
| Reapprove V3 case mapping and smoke candidates | Frozen benchmark documentation | V2 mapping cannot be silently promoted | Documentation/evaluation | Small | Pair |

### Optional Before Full Run

- Add a provider adapter that records safe provider request IDs and finish
  metadata consistently across supported providers.
- Add a report renderer that displays mechanical gate diagnostics and semantic
  metrics in physically separate sections.
- Add a full-run circuit-breaker threshold for repeated identical infrastructure
  failures after smoke has passed.
- Add a content-addressed raw-response sidecar when one JSON run artifact is too
  large for the repository policy.

### Deferred

- Production `EvaluationRun` persistence or API.
- Model comparison orchestration.
- New provider/model selection.
- Prompt semantic changes.
- Retrieval, RAG, embeddings, vector databases, or new services.
- Human workflow/product utility study.
- Any changes to Trading OS.

## Tests Required Before Smoke

The implementation must add tests for:

- complete manifest and unique slot identity;
- immutable artifact finalization and hash verification;
- exact provider-visible evidence and UTF-8/newline fidelity;
- repository identity and revision binding;
- exact task/context identity;
- schema digest and mapping hash mismatch rejection;
- raw response and finish/token metadata preservation;
- offline replay without provider, network, or current repository;
- structural fail-fast and circuit-breaker behavior;
- semantic scoring blocked on every invalid prior gate;
- explicit denominator and benchmark-invalid behavior;
- secret redaction/preflight failure.

## Production and Architecture Boundary

```text
PRODUCTION_CODE_CHANGE_REQUIRED = NO
PRODUCTION_SEMANTICS_CHANGE_REQUIRED = NO
V3_ADR_REQUIRED = NO
```

The protocol is an evaluation artifact contract and does not create a durable
production architecture decision. If implementation later requires changing
Core task persistence, callback identity, or production semantic behavior, stop
and request a separate ADR/authorization decision rather than extending this
protocol implicitly.

## Utility Boundaries

### AI Utility

V3 can measure whether the configured model, under the frozen context and
contract, produces sufficiently reliable grounded causal interpretations to be
a candidate for controlled exposure in an engineering workflow. A passing
benchmark is evidence of capability under that benchmark, not automatic product
acceptance or a model-selection mandate.

```text
AI_UTILITY_MEASURABLE_AFTER_V3 = PARTIALLY
```

The result should be reported as capability evidence plus limitations, not as a
guarantee of general engineering usefulness.

### Human Utility

V3 cannot establish human utility. Human utility requires a later workflow
evaluation with engineers using the DevLog Agent/Kiko Engineering Story
Discuss/Plan flow, measuring comprehension, decision quality, review burden,
trust calibration, and useful outcomes. It must preserve the existing human
validation boundary and must not promote AI output directly to trusted
knowledge.

```text
HUMAN_UTILITY_MEASURABLE_AFTER_V3 = NO
```

## LEARN / PAIR / DELEGATE Suggestions

- `LEARN`: benchmark validity, semantic denominator design, and the distinction
  between provider execution, deterministic validation, and model capability.
- `PAIR`: V3 manifest/artifact contract, provider-visible evidence alignment,
  retry policy, gate semantics, mapping approval, and any change touching Java
  Core validation.
- `DELEGATE`: mechanical serializer implementation, JSON hash verification,
  redaction tests, fixture completeness tests, and deterministic replay plumbing
  after the contract is approved.

These are suggestions for the next authorized implementation slice, not an
authorization to implement them now.

## Pre-Smoke Implementation Status

The authorized pre-smoke slice implements the evaluation-only candidate manifest
and artifact primitives at:

```text
ai-engine/evaluations/product_value/v3/benchmark-manifest.json
ai-engine/evaluations/product_value/v3_protocol.py
ai-engine/tests/test_v3_protocol.py
```

The candidate manifest carries forward the V2 human-approved oracle without
semantic changes, but the new V3 smoke selection remains
`MECHANICALLY_MIGRATED` with `mappingHumanApproval = PENDING`. The candidates
are `CASE-01::CL-01`, `CASE-04::CASE-04`, and `CASE-03::CL-09`.

Implemented before smoke: manifest validation, exact UTF-8 evidence capture,
SelectedKnowledge/prompt/raw-response/attempt capture, secret preflight,
technical-retry restrictions, ordered fail-fast gates, benchmark denominator
validity, immutable artifact writing, and offline replay through the existing
Java Core evaluation bridge. No provider execution is part of this slice.

The pre-smoke readiness decision is recorded separately in
`docs/evaluation/v3-pre-smoke-readiness.md`.

## Next Action After Approval

After human approval of this design, the exact next action is to implement the
`REQUIRED_BEFORE_SMOKE` evaluation-only manifest, artifact, snapshot, replay,
and gate tests, then run only offline deterministic tests. The smoke remains
unauthorized until those changes and their tests are reviewed and a human
explicitly authorizes the smoke run.
