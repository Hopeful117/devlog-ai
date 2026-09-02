# Post-0109 Investigation — `NEW` versus `ENRICHES`

## Status

**COMPLETE — IMPLEMENTATION DEFECT FOUND; PRODUCT GATE REMAINS FAILED**

## Scope and controls

This investigation explains why the three post-fix `architecture-overview-v2`
executions did not produce the expected `ENRICHES` delta for the explicit
`backend -> ai-engine` Docker Compose dependency.

Controls preserved throughout the investigation:

```text
PRODUCTION_CODE_CHANGED = NO
PROMPT_TEMPLATE_CHANGED = NO
PRODUCTION_MODEL_CHANGED = NO
PRODUCTION_CONFIGURATION_CHANGED = NO
TRUSTED_KNOWLEDGE_CREATED_OR_PROMOTED = NO
COMMIT = NO
PUSH = NO
PR = NO
```

Only this investigation report was added. Model experiments used temporary
files outside the repository and the already configured OpenAI provider; they
did not alter the running service configuration.

## Executive conclusion

The observed behavior is not primarily a lack of model capability and is not a
missing-target problem. It is a combination of three defects or limitations:

1. **Deterministic schema defect:** the Intent contract declares `deltaType`
   required, but the Python response schema makes it optional and silently
   defaults omission to `NEW`.
2. **Repair-loop defect:** the corrective retry says only that an uncovered
   relationship requires “a delta proposal”. It does not require `ENRICHES`,
   identify the supplied target, or reject a semantically wrong `NEW` proposal.
3. **Context-salience limitation:** the exact relationship appears once inside
   an initial prompt of about 123.7k characters. The same target and unrelated
   knowledge are repeated across several overlapping projections. With the
   production context, `gpt-4.1-mini` produced two `NEW` repairs and one second
   no-delta answer. With a minimal context containing the same Fact,
   Observation, trusted Insight and unchanged prompt template/model, it produced
   valid `ENRICHES` proposals in 5/5 executions.

The formal ADR distinction is broad and therefore partially ambiguous for some
real-world knowledge boundaries. The exact canonical benchmark is not ambiguous
under the current versioned prompt: it explicitly classifies a directional
component dependency as `ENRICHES` when trusted knowledge already establishes
those components or their containerization.

## 1. Formal delta contract

### 1.1 Semantic definitions

ADR-050 defines:

- `NEW`: “A genuinely new knowledge statement was discovered”
  (`docs/decisions/ADR-050.md:133-136`).
- `ENRICHES`: an existing trusted statement remains valid, but meaningful
  additional information extends or refines it
  (`docs/decisions/ADR-050.md:137-140`).
- no material change: no proposal and no new trusted record
  (`docs/decisions/ADR-050.md:99-115`).

ADR-051 reinforces the intended duplicate-control behavior: prefer `ENRICHES`
when prior trusted knowledge remains valid but incomplete, and create a new
trusted record only for genuinely new knowledge
(`docs/decisions/ADR-051.md:145-160`).

The implemented Python enum contains only `NEW` and `ENRICHES`
(`ai-engine/app/schemas/insight.py:8-10`). Java persists the value in the
proposal JSON payload rather than in a dedicated enum-backed column.

### 1.2 Target contract

```text
CAN_NEW_TARGET_EXISTING_KNOWLEDGE = NO
MUST_ENRICHES_TARGET_EXISTING_KNOWLEDGE = YES
CAN_ENRICHES_EXIST_WITHOUT_TARGET = NO
HOW_TARGET_IS_IDENTIFIED = exact existingArchitectureKnowledge[].insightId UUID
HOW_TARGET_IS_VALIDATED = schema rule + selected-snapshot membership + project ownership on promotion
```

Evidence:

- Python requires a target for `ENRICHES` and forbids one for `NEW`
  (`ai-engine/app/schemas/insight.py:64-70`).
- The prompt requires the model to copy the exact target UUID
  (`ai-engine/app/prompts/insight.py:180-195`).
- Python validates the target against the submitted
  `existingArchitectureKnowledge` list
  (`ai-engine/app/services/insight_generation_service.py:263-273`).
- Java parses the UUID and validates membership against the AI task's immutable
  selected-knowledge snapshot
  (`backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java:234-276`).
- Promotion re-fetches the target and verifies project ownership
  (`backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPromotionService.java:94-114`).

Both accepted `NEW` and accepted `ENRICHES` proposals create a new immutable
Insight. `ENRICHES` additionally creates a new-Insight-to-target-Insight
`DERIVED_FROM` relation; it does not overwrite the target
(`InsightPromotionService.java:40-60,94-114`).

### 1.3 Canonical benchmark classification

The trusted target is:

```text
insightId = 0c4f1e1d-2437-43b6-bab3-6fec65593954
title = Project Containerization with Docker and Docker Compose
content = The project is containerized using Docker, with Docker Compose
          configured for orchestrating multi-container environments...
```

The selected new Fact is:

```text
type = DOCKER_SERVICE_DEPENDS_ON
content = from=backend,to=ai-engine
```

The selected Observation is:

```text
type = ARCHITECTURE_MODULARIZATION
ruleId = DOCKER_SERVICE_WIRING
content = The project defines multiple Docker Compose services with an explicit
          service dependency or runtime reference.
```

The trusted target remains valid and the new directed edge adds material detail
to the already trusted Docker Compose architecture. This satisfies the ADR
meaning of `ENRICHES`. More importantly, the versioned prompt resolves this
exact case explicitly:

> An explicit directional component dependency or runtime reference is a
> material ENRICHES delta when existing knowledge only states that the
> components or their containerization exist and does not already describe that
> relationship.

Source: `ai-engine/app/prompts/insight.py:186-195`.

Therefore:

```text
EXPECTED_CLASSIFICATION_FOR_CANONICAL_CASE = ENRICHES
EXPECTED_TARGET = 0c4f1e1d-2437-43b6-bab3-6fec65593954
```

## 2. Concrete implementation defect

The versioned Intent output contract includes `deltaType` in
`requiredProposalFields`
(`backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java:139-157`).
The Python response schema contradicts it:

```python
delta_type: KnowledgeDeltaType = Field(
    default=KnowledgeDeltaType.NEW, alias="deltaType"
)
```

Source: `ai-engine/app/schemas/insight.py:41-49`.

The generated JSON Schema omits `deltaType` from its `required` list and exposes
`default: "NEW"`. A direct local validation proved that an object with no
`deltaType` is accepted and serialized as `NEW`.

```text
OMITTED_DELTA_TYPE_ACCEPTED = YES
IMPLICIT_SERIALIZED_DELTA_TYPE = NEW
INTENT_REQUIRES_EXPLICIT_DELTA_TYPE = YES
```

This is a deterministic contract defect and a structural `NEW` bias. The raw
provider response is not retained, so the repository cannot prove whether runs
1 and 2 explicitly emitted `NEW` or omitted `deltaType` and received the
Python default. The defect is real either way, but its contribution to those two
specific outputs is not directly observable.

## 3. Corrective retry semantics

`InsightPromptBuilder.corrective_retry` appends the validation message to the
complete original prompt (`ai-engine/app/prompts/insight.py:230-245`). For this
case, the appended instruction is effectively:

```text
The previous response was invalid. Correct these errors and return the complete
output again:
An explicit selected component relationship absent from existing architecture
knowledge requires a grounded architecture delta proposal
```

The complete retry still contains the original classification contract and all
trusted target objects. However, the newly salient repair instruction asks only
for proposal existence.

```text
CORRECTIVE_RETRY_EXISTENCE_AWARE = YES
CORRECTIVE_RETRY_CLASSIFICATION_AWARE = NO
CORRECTIVE_RETRY_TARGET_AWARE = NO
```

The Python and Java uncovered-relationship validators have the same semantic
gap: they reject `hasDeltas == false`, but once any proposal exists they do not
verify that the uncovered edge is represented as `ENRICHES` against the relevant
trusted target (`InsightGenerationService.java-equivalent Python lines 228-241`;
`AiProposalContractValidator.java:71-83`). Normal proposal validation checks a
target only if the proposal already says `ENRICHES`.

This explains the asymmetric benchmark behavior:

- initial no-delta output -> correctly rejected;
- retry with any valid `NEW` proposal -> accepted;
- retry with no proposal -> rejected again.

The retry is therefore capable of forcing existence but not semantic delta
correctness.

## 4. Exact production snapshots and outputs

All three executions had the same material context shape:

```text
selectedFacts = 40
selectedObservations = 5
selectedInsights = 10
existingArchitectureKnowledge = 5
repositoryContext.evidence = 60
semanticSections.items = 202
selectedHumanContextInputs = 3
selected relationship Fact = exactly 1
selected wiring Observation = exactly 1
expected trusted target = exactly 1
```

Fact and Observation UUIDs changed because collection was rerun, but the
canonical relationship, target Insight UUID and material content were stable.

| Run | Analysis | Task | Retry result | Classification | Target |
|---|---|---|---|---|---|
| 1 | `11699ad1-d01c-4434-b1ae-8c9b925011cf` | `842c3955-a75d-4b69-b08f-20bec8aacdf8` | one proposal | `NEW` | omitted |
| 2 | `85331848-74d6-4cff-ba96-125b02c41654` | `4f5051be-310a-43e3-834d-6340424e9f6e` | one proposal | `NEW` | omitted |
| 3 | `e490bd9c-d040-472e-85a0-cd4e7c14b68f` | `d93d3fb5-2032-4ca1-b136-ff258c06cdc6` | no proposal | none | none |

Each execution made two successful OpenAI HTTP calls: one initial generation and
one corrective retry. Run 3 then failed with `INVALID_LLM_OUTPUT` because its
second response still omitted the required relationship delta.

Run 1 described startup/deployment ordering but labeled the delta `NEW`. Run 2
labeled it `NEW` and additionally overclaimed runtime communication. Neither
output supplied the required target UUID.

Initial raw model responses were not persisted. Exact initial and retry prompts
were therefore reconstructed deterministically from the immutable task snapshots
and current versioned builder. The successful-run logs confirm final retry
prompt sizes of 123,899 and 123,902 characters.

## 5. Context size, duplication and salience

For run 1:

```text
CANONICAL_SELECTED_KNOWLEDGE_CHARS = 94,234
INITIAL_USER_PROMPT_CHARS = 123,651
RETRY_USER_PROMPT_CHARS = 123,899
SEMANTIC_SECTION_SLOTS = 202
SEMANTIC_SECTION_UNIQUE_IDS = 121
SEMANTIC_SECTION_DUPLICATE_SLOTS = 81
```

The 202 semantic-section entries are an index-like projection, but 81 slots
repeat an ID already present in another semantic section. Every selected Fact,
Observation, prior Insight, existing architecture Insight and human-context ID
is also represented in semantic sections.

Additional prompt duplication:

- all five `existingArchitectureKnowledge` objects occur inside the complete
  selected-knowledge JSON and are then serialized again in a dedicated trusted
  architecture block;
- trusted Insights also overlap `selectedInsights`, architecture semantic
  sections and repository-context summaries;
- the expected target title occurs six times and its UUID ten times;
- the exact edge `from=backend,to=ai-engine` occurs only once;
- the exact explicit ENRICHES classification rule occurs only once, near the end
  of the 123k-character prompt.

The target is therefore available and highly visible by identifier. The weak
point is the ratio and positional salience of the single new edge and its
classification rule relative to broad, repeated project context.

## 6. Controlled model experiments

### 6.1 Minimal context construction

The minimal experiment retained:

- the same `architecture-overview-v2` Intent and output contract;
- the same production prompt builder, system prompt and provider path;
- the exact `DOCKER_SERVICE_DEPENDS_ON` Fact;
- the exact `ARCHITECTURE_MODULARIZATION` Observation;
- the exact trusted containerization Insight and target UUID;
- exact allowed grounding references.

It removed unrelated Facts, Insights, semantic indices, repository evidence and
human context. No classification hint was added beyond the unchanged production
prompt.

### 6.2 Results

| Model | Context | Attempts | Valid structured outputs | `ENRICHES` | exact target | `NEW` | no delta |
|---|---:|---:|---:|---:|---:|---:|---:|
| `gpt-4.1-mini` | minimal | 5 | 5 | 5 | 5 | 0 | 0 |
| `gpt-4.1` | minimal | 5 | 5 | 5 | 5 | 0 | 0 |
| `gpt-4.1-mini` | production benchmark | 3 | 2 | 0 | 0 | 2 | 1 |
| `gpt-4.1` | full run-1 snapshot | 1 attempted | 0 | — | — | — | — |

The full-context `gpt-4.1` comparison was attempted through the same provider but
was rejected before generation by the model-specific 30,000 tokens/minute limit:
41,778 tokens were requested. It was not retried and is not counted as a model
classification result.

The minimal experiment establishes:

```text
GPT_4_1_MINI_CAN_CLASSIFY_THE_CASE = YES (5/5)
STRONGER_MODEL_IMPROVES_MINIMAL_CLASSIFICATION = NO OBSERVABLE DIFFERENCE (both 5/5)
FULL_STRONGER_MODEL_COMPARISON = BLOCKED_BY_MODEL_TPM_LIMIT
```

Classification and target selection were perfect in the minimal experiment.
Grounding quality remained a separate issue: several responses from both models
used phrases such as “runtime relationship” or “runtime wiring”, which are not
proved by Docker Compose `depends_on`. Correct delta typing therefore does not
by itself guarantee a STRONG product result.

## 7. Root-cause assessment

### 7.1 Supported causal findings

1. **Model incapacity is rejected as the primary cause.** The same
   `gpt-4.1-mini` classified and targeted correctly in 5/5 minimal runs.
2. **Missing target representation is rejected.** The exact trusted UUID is in
   `existingArchitectureKnowledge`, passes both Python and Java membership
   checks, and occurs ten times in the full prompt.
3. **The exact benchmark contract is sufficiently explicit in the base prompt.**
   It directly names this case as `ENRICHES`.
4. **Context salience is a major causal factor.** Holding model, template and
   material evidence constant while removing unrelated context changed observed
   classification from 0/3 to 5/5.
5. **The repair loop is semantically incomplete.** It makes proposal existence
   salient but not the expected class or target, and validators accept any
   proposal once one exists.
6. **The output schema contains a deterministic `NEW` bias.** Missing
   `deltaType` is silently converted into `NEW` despite the formal required-field
   contract.

### 7.2 What remains unproved

- Raw responses were not retained, so it is unknown whether runs 1 and 2
  explicitly selected `NEW` or omitted `deltaType`.
- The experiment isolates total context reduction, not every individual noisy
  projection. It proves a context/salience effect but does not rank each removed
  section causally.
- The full stronger-model comparison could not run under its TPM limit, so no
  claim is made about its full-context accuracy.

## 8. Recommended next action

### Primary — F: fix the deterministic schema-contract mismatch

Make `deltaType` genuinely required in the Python structured-output schema by
removing the implicit `NEW` default, and add regression coverage proving that an
omitted classification is rejected rather than silently normalized.

Why first:

- it is a confirmed implementation defect, independent of stochastic model
  behavior;
- it contradicts the versioned Intent contract;
- it is the smallest change that removes a structural `NEW` bias and restores
  observability of model omission;
- it should be fixed before interpreting another production benchmark.

This fix alone is not claimed to guarantee `ENRICHES` if the model explicitly
chooses `NEW`.

### Secondary — B: make the edge retry classification- and target-aware

After the schema fix, make the uncovered-edge repair instruction state the
expected semantic operation and exact candidate target, for example at the
contract level rather than as repository-specific wording:

```text
The selected directed edge is absent from the supplied trusted architecture
item that already describes its containerized components. Return an ENRICHES
proposal targeting that item's exact insightId; NEW is not valid for this
relationship-to-baseline comparison.
```

The validator should then reject a semantically incompatible `NEW` repair for
that uncovered edge rather than treating any proposal as success. This requires
a deterministic edge-to-target association; it must not guess among unrelated
trusted Insights.

### Follow-up — D: reduce duplicated prompt projections

Run ablations before redesigning selection. The first low-risk candidates are:

1. serialize `existingArchitectureKnowledge` once, not both inside selected
   knowledge and in a repeated dedicated block;
2. avoid sending semantic-section index entries that merely repeat every
   already serialized selected item;
3. separate synthesis-wide context from the small delta-comparison surface so
   the edge and candidate target are adjacent and bounded.

Do not remove evidence categories blindly; preserve synthesis quality and trust
boundaries with before/after benchmarks.

### Deferred

- **A — clarify global NEW/ENRICHES identity semantics:** useful as a future ADR
  refinement, but not the immediate blocker for this exact case because the
  versioned prompt already disambiguates it.
- **C — improve target representation:** not supported as primary; target UUID
  and content are present and repeated. A compact explicit edge-to-target
  comparison surface belongs with B/D.
- **E — change model:** not justified. Both tested models were perfect in the
  minimal classification task, while the stronger model could not accept the
  full prompt under its TPM limit.
- hard-code repository-specific names, weaken trust boundaries, or silently
  promote proposals: rejected.

## Required verdict

```text
NEW_SEMANTICS = Genuinely new standalone knowledge; targetInsightId forbidden.
ENRICHES_SEMANTICS = Meaningful extension/refinement of a still-valid supplied trusted Insight; exact target required.
DELTA_CONTRACT_AMBIGUITY = PARTIAL (broad ADR identity boundary), but NOT ambiguous for the canonical benchmark under the versioned prompt.
IMPLEMENTATION_DEFECT_FOUND = YES
MODEL_CAPABILITY_LIMIT_FOUND = NO for classification; grounding overclaims remain. Full stronger-model production comparison was quota-blocked.
CONTEXT_SIZE_OR_REDUNDANCY_LIMIT_FOUND = YES
TARGET_REPRESENTATION_LIMIT_FOUND = NO as primary cause; target is present, exact and over-repeated rather than absent.
CORRECTIVE_RETRY_EXISTENCE_AWARE = YES
CORRECTIVE_RETRY_CLASSIFICATION_AWARE = NO
CORRECTIVE_RETRY_TARGET_AWARE = NO
PRIMARY_NEXT_ACTION = F — remove implicit NEW default and require explicit deltaType, with regressions.
SECONDARY_NEXT_ACTION = B — make uncovered-edge retry/validation classification- and target-aware; then D via measured prompt ablations.
PRODUCT_GATE_STATUS = FAILED
```

## Validation and repository integrity

```text
REPORT_MARKDOWN_CREATED = docs/investigations/post-0109-new-vs-enriches-investigation.md
PRODUCTION_TESTS_RERUN = NO (no production code changed)
MODEL_EXPERIMENTS = 10 successful minimal calls + 1 full-context quota-rejected call
SOURCE_WORKTREE_PRESERVED = YES; pre-existing status and protected-file checksums match the investigation baseline
```
