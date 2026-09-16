# V3 Design C Offline Implementation

Status: corrected live smoke partial; full V3 run not authorized.

The first live smoke request was rejected before model output because the
initial schema used an untyped `list[Any]` item for the exactly-zero
`causalClaims` field. That failed run remains immutable historical evidence.
The corrected schema reuses Story0132's canonical `CausalClaim` model, inlines
array item references in the provider-facing schema, and is preflighted
recursively before any future request.

## Capability Boundary

Design C measures one Core-owned causal question over deterministic evidence
projections. The provider does not receive the complete CASE evidence universe.
The authorized universe remains available to benchmark and replay artifacts.

The provider-visible identities are a strict subset of the authorized
identities. Actual output references are validated against the provider-visible
set, while benchmark context completeness remains evaluated against the full
authorized set.

## Identities

| Identity | Revision | Digest |
|---|---|---|
| Context selection | `story0132-v3-design-c-context-selection-1.0.0` | `de3d7236b83f752c68347c9a3bf03ec2f97c15f8869ba170c9b83ecdad1dbf90` |
| Provider schema | `story0132-v3-design-c-causal-provider-schema-1.0.1` | `6b291e77e1708b0bc3a745869544f8ba9215389d469811e1205b9cd3e129dd24` |
| Execution configuration | `story0132-v3-design-c-execution-config-1.2.1` | `b47bb5205d7757b3998ebdb2aa88f85989fb7f23a5bd921767d8fe0c46aaa2cf` |

The semantic task, benchmark case identity, mapping, and mapping hash remain
unchanged. The existing mapping hash is
`67474f09e41c07899c8c7117754b21e794f285380ffd50675e701a0a3c2c40c0`.

## Projections

Projection definitions and expected byte digests are frozen in
`ai-engine/evaluations/product_value/v3/projection-manifest.json`.

| Slot | Visible items | Evidence bytes | User prompt chars | Total prompt chars |
|---|---:|---:|---:|---:|
| CASE-01 / CL-01 | 4 | 32,401 | 43,609 | 44,187 |
| CASE-04 | 4 | 13,504 | 22,419 | 22,997 |
| CASE-03 / CL-09 | 3 | 31,683 | 41,231 | 41,809 |

The system prompt is 578 characters for each slot. Input token counts are not
reported because the installed environment has no deterministic provider
tokenizer. The provider-visible evidence is never silently truncated. Locator,
byte-length, digest, revision, and ordering mismatches fail closed.

Commit projections contain metadata and explicit changed-file/hunk selections;
the complete commit patch is not exposed. Document, source, and test evidence
use exact section or line-range projections.

## Provider Contract

The evaluation-only `CausalProviderResult` contains:

- Exactly one `causalAssessment`.
- Zero to four `evidenceAssertions`, bounded by the four projected identities.
- Exactly zero `causalClaims` for V2.
- String confidence enum `HIGH`, `MEDIUM`, or `LOW`.
- Provenance for task/context/provider/model/prompt identity.
- Exactly one output-classification entry.

Generic StoryContext collections are not part of the provider contract. The
adapter mechanically creates a Core-shaped `StoryContextAnalysisResult` with
those collections empty, preserving the existing Java/Core validation bridge.
It performs no semantic, reference, evidence, or confidence repair.

Valid abstention permits zero evidence assertions. Affirmative assessments
require at least one assertion. CASE-04 and CASE-03 use the same schema and
different deterministic projection manifests; no per-case semantic fields are
introduced.

## Offline Size Fixtures

These are serialized-size fixtures, not model outputs:

| Fixture | UTF-8 bytes |
|---|---:|
| Minimum affirmative | 1,024 |
| Maximum contract-valid affirmative | 6,919 |
| Valid abstention | 745 |
| Evidence-heavy valid positive | 6,919 |

The maximum fixture uses four assertions and the existing 5,000-character
causal explanation bound. Exact provider tokenization is not available
offline. `2500` was approved for the corrected smoke as the frozen output
envelope.

## Historical Comparability

The prior 2000- and 3000-token V3 artifacts remain valid for infrastructure and
output-pressure diagnostics. They are not semantically comparable with Design
C because context-selection and provider-schema identities changed.

No production code, production semantics, production contract, benchmark
mapping, ground truth, or Story0132 acceptance semantics were changed.

The corrected smoke was authorized and executed partially. The provider
accepted the corrected schema and CASE-01 completed within the 2500-token
envelope, but Core rejected its generated evidence excerpt. The response was
preserved without repair or retry; CASE-04 and CASE-03 were not executed.
