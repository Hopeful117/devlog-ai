# Story 0132 - Evidence-Grounded Causal Interpretation

## Status

`DETERMINISTIC_IMPLEMENTATION_COMPLETE - HISTORICAL_LIVE_BENCHMARK_INVALID`

## Authorization

Human decision: `AUTHORIZE STORY0132 IMPLEMENTATION`.

The approved GREEN production implementation and the required Ground-Truth
evaluation are complete within this Story. Interpretation reliability remains
RED; the prior RED artifact remains frozen. Commit, push and merge remain
separate gates.

## Baseline

- Story 0131 is merged on `main` as `8b2be9f`.
- Story 0131's oracle and Ground-Truth Context remain frozen.
- Repository ground truth resolves `19/19` expected artifacts.
- DevLog context retrieves `10/19` artifacts, with `9` true retrieval misses.
- The same `gpt-4.1-mini` interpreter was run three times for four cases in
  both DevLog and Ground Truth conditions.
- Causal reasoning accuracy is `0.433` for DevLog and `0.483` for Ground
  Truth.
- Ground Truth fails the CASE-04 negative control in one of three runs.
- Direct Repository comparison is blocked because no comparable agent/tool
  capture exists.
- Human Utility is `NOT_YET_ESTABLISHED`.

The measured Ground-Truth result means that complete evidence is insufficient
for reliable causal interpretation. This Story therefore targets interpretation
semantics and contracts first; it does not assume that retrieval is the only
cause of the observed failures.

## Problem

The evaluation benchmark measures causal classifications with three semantic
outcomes: `EXPLICITLY_DOCUMENTED`, `STRONGLY_SUPPORTED`, and
`NOT_ESTABLISHED`. The production Story Context Analysis contract instead
represents relationships through `relationType` values such as `EXPLICIT`,
`TEMPORAL_PROXIMITY`, `POSSIBLE_RELEVANCE`, and `INFERRED_HYPOTHESIS`.

The two contracts do not provide an explicit, deterministic mapping for a
causal claim. In particular, the production result has no first-class causal
claim with a required source, target, classification, evidence set, and
negative/abstention outcome. The prompt asks for conservative causality, but
the Python validator primarily checks reference membership, classification
syntax, grounding flags, and selected relation-type presence. It does not
itself establish that a causal conclusion is supported by the cited evidence.

This allows a structurally valid interpretation to overstate causality or to
turn temporal proximity and shared topic into an affirmative causal answer.
CASE-04 demonstrates the required failure mode: the evidence is complete, but
the answer must remain `NOT_ESTABLISHED`.

## Goal

Make bounded causal interpretation explicit, evidence-grounded, abstention-safe
and mechanically measurable without treating AI output as trusted knowledge.

For the frozen Story0131 questions, a complete Ground-Truth Context must be
able to produce a causal answer that:

1. identifies the exact source and target of the relationship;
2. distinguishes direct documentation, strong support, co-occurrence,
   possible relevance and unsupported inference;
3. cites only authorized evidence references;
4. returns `NOT_ESTABLISHED` when the evidence does not establish causality;
5. preserves the distinction between evidence extraction, interpretation and
   recommendation; and
6. remains a proposal/snapshot subject to existing human validation rules.

## Scope

### In Scope

- Define one canonical causal-claim contract for the Story Context Analysis
  interpretation path, including source, target, classification, evidence
  references and an explanation bounded by the cited evidence.
- Define the canonical mapping, if any, between causal classification and the
  existing relationship metadata. A relation type must not silently become a
  causal strength.
- Represent `NOT_ESTABLISHED` as an explicit safe outcome rather than as an
  omitted claim or an empty section.
- Make evidence references mandatory for affirmative causal classifications and
  validate every reference against the Java-authored grounding contract.
- Define deterministic structural validation for causal claims, including:
  - no unknown references;
  - no missing source or target;
  - no duplicate or conflicting claim identity;
  - no affirmative causal classification based only on
    `TEMPORAL_PROXIMITY`, `POSSIBLE_RELEVANCE` or `INFERRED_HYPOTHESIS`;
  - no use of confidence as evidence;
  - explicit evidence insufficiency for `NOT_ESTABLISHED`.
- Preserve Java Core as the authoritative owner of context membership,
  grounding authorization and callback validation.
- Add an evaluation-only adapter from the production-shaped result to the
  frozen Story0131 scorer without changing the benchmark or oracle.
- Add deterministic replay tests using complete evidence, including CASE-04,
  and tests proving that incomplete DevLog context is attributed separately
  from interpretation failure.
- Define a controlled comparison protocol for Ground Truth and DevLog context
  before any live production evaluation is repeated.

### Explicit Non-Goals

- No retrieval, ranking, context-selection, MCP, RAG, embeddings or vector
  search changes.
- No Direct Repository agent/tool runner in this Story.
- No change to the Story0131 benchmark, frozen oracle, expected classifications,
  scoring identifiers or thresholds.
- No generic causal inference system, graph inference, probabilistic causal
  model or domain-independent ontology.
- No promotion of AI output to Insight, Decision, EngineeringEvent or any
  other trusted knowledge.
- No weakening of grounding, typed-reference integrity or human validation.
- No Human Utility claim or product-value acceptance claim.
- No frontend, database, device communication or Trading OS changes.

## Authority Model

- Java Core remains the sole authority for EngineeringContext construction,
  scope, grounding authorization and authoritative callback validation.
- Python performs structured generation and defensive validation only.
- The provider cannot authorize evidence, establish a relationship, or promote
  a causal interpretation to trusted knowledge.
- `confidence` is metadata about the generated interpretation; it never
  changes the causal classification or establishes evidence.
- A persisted analysis remains a non-trusted analysis snapshot until the
  existing human validation/promotion flow applies.

## Causal Semantics

The implementation plan must propose and obtain approval for one explicit
mapping between the benchmark vocabulary and the production contract. At
minimum, the mapping must preserve these invariants:

| Situation | Required outcome |
|---|---|
| Evidence directly states the relationship | `EXPLICITLY_DOCUMENTED` |
| Multiple evidence items materially support the relationship without a direct statement | `STRONGLY_SUPPORTED` |
| Evidence shows chronology, co-occurrence, compatibility or shared topic only | `NOT_ESTABLISHED` |
| Evidence is conflicting or insufficient | `NOT_ESTABLISHED` plus an uncertainty/missing-evidence explanation |

`TEMPORAL_PROXIMITY`, `POSSIBLE_RELEVANCE` and `INFERRED_HYPOTHESIS` may
describe the strength or status of an interpretation only if the approved
mapping makes that distinction explicit. None may be promoted to an affirmative
causal conclusion by confidence, wording, or model preference.

## Validation Boundary

The implementation plan must identify validation in both layers:

- Python: schema and defensive semantic checks sufficient to reject malformed
  or internally contradictory causal claims before callback.
- Java Core: authoritative subset, scope, typed-reference and causal-contract
  validation against the immutable task context and grounding contract.

Validation must fail closed. Corrective retry may repair malformed output once,
but it must not invent missing evidence or change the authorized context.

## Evaluation Protocol

Before implementation acceptance:

1. Replay the frozen benchmark against a complete evidence-only context.
2. Demonstrate CASE-04 `NOT_ESTABLISHED` across all required repetitions.
3. Demonstrate that affirmative causal outputs cite authorized evidence and
   satisfy the approved semantic mapping.
4. Demonstrate that removing causal-supporting evidence produces either
   `NOT_ESTABLISHED` or a deterministic validation failure, never an
   affirmative answer based on remaining chronology/topic evidence.
5. Score Ground Truth and DevLog conditions with identical interpretation
   instructions and the existing deterministic scorer.
6. Report context failure and interpretation failure independently.
7. Do not declare Direct Repository parity, Human Utility, or product-value
   acceptance from this protocol.

Acceptance thresholds must be approved before the implementation run. They
must not be embedded in model-facing context and must not be inferred from
confidence.

## Acceptance Criteria

1. [x] A canonical causal-claim contract is documented and approved without
   changing Story0131's frozen benchmark or oracle.
2. [x] The contract distinguishes explicit documentation, strong support and
   non-establishment, including an explicit CASE-04 abstention outcome.
3. [x] Relation metadata and causal classification have a documented mapping;
   no relation type is silently treated as causal proof.
4. [x] Python defensive validation rejects malformed, contradictory,
   ungrounded or unsupported causal claims.
5. [x] Java Core authoritative validation rejects unknown, out-of-scope or
   unauthorized causal evidence references.
6. [x] Corrective retry remains bounded to one attempt and cannot add evidence
   outside the Java-authored grounding contract.
7. [x] Deterministic tests cover affirmative, strongly supported, temporal-only,
   conflicting, insufficient-evidence and CASE-04 negative cases.
8. [ ] Evaluation replay proves complete Ground-Truth Context is scored as an
   interpretation condition rather than a retrieval condition.
9. [x] DevLog misses remain separately measurable and are not hidden by
   interpretation scoring.
10. [x] Existing Story0131 tests and all relevant AI Engine/Core tests pass.
11. [x] No trusted knowledge is persisted directly from AI output.
12. [x] Human Utility, Direct Repository parity and product acceptance remain
   explicitly unset.

## Governing Constraints

- ADR-006: AI output is untrusted until human validation.
- ADR-063: structured context and deterministic Core-owned context authority.
- ADR-066: replayable, evidence-based evaluation discipline.
- ADR-067: Java/Core context and authorization boundary; Python as probabilistic
  execution boundary.
- ADR-068 and Stories0126-0129: typed-reference and fail-closed grounding
  integrity.

## Implementation Authorization

`GREEN_IMPLEMENTED - EVALUATED - INTERPRETATION RED`

The approved GREEN implementation completed deterministic Python and Java
validation. The frozen Ground-Truth rerun executed with `openai`/
`gpt-4.1-mini` for four cases and three repetitions. Interpretation remains
RED: positive causal accuracy `0.15555555555555556`, CASE-04 `0/3
NOT_ESTABLISHED`, grounding PASS, stability `0.7983058608058609`, and
historical unsupported-inference gate FAIL. The prior RED result remains
frozen. DevLog Context was not rerun because the available Story0131 capture
contains references but not model-facing evidence content; its `10/19` context
baseline remains unchanged.

## Deterministic Closure

The deterministic Story0132 implementation is complete. Production contracts,
defensive Python validation, authoritative Java validation, typed evidence
locators, context-digest authority, confidence serialization,
`outputClassification` normalization, and `GroundingMetadata.relationType`
semantics are covered by the implementation and relevant tests.

The historical live benchmark is invalid for semantic model-capability
assessment. It contains `27/33` structurally invalid provider outputs and six
parseable outputs rejected by strict Core evidence assertions. The immutable
historical provider-visible evidence snapshot required to distinguish provider
selection from later source reconstruction was not preserved. Therefore
semantic model capability, baseline validity, AI utility, and human utility are
not established. Mechanical zero values from invalid slots are diagnostic only,
not semantic model scores.

Replay-only provenance normalization remains confined to the evaluation runner:
`interactionTrace.selectedKnowledgeFingerprint` is used only to reconstruct the
historical task identity for offline replay. Production Core context-digest
validation remains strict and unchanged in authority.

The complete closure record, change classification, future benchmark
requirements, and verification results are in
`deterministic-implementation-closure-report.md`.
