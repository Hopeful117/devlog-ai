# Story 0106 Engineering Decision Benchmark Variance Investigation

## Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `INVESTIGATION_ONLY`
- Date: `2026-08-31`

## Scope

This investigation explains the engineering-decision benchmark variance observed after Story 0106 implementation.

Explicit non-actions:

- no production code changes
- no prompt changes
- no test changes
- no schema changes
- no Java or frontend changes
- no temperature/model/provider changes
- no Story 0107

## Baseline

- Baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Branch: `story/0106-intent-aware-context-utilization`
- HEAD SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`

Worktree at investigation start contained uncommitted Story 0106 implementation files plus pre-existing unrelated untracked files.

## Governing Context

Read and preserved:

- Story 0106 lifecycle artifacts
- `docs/investigations/post-0105-prompt-utilization-and-analysis-synthesis.md`
- `docs/investigations/intent-aware-structured-context-utilization-prompt-architecture-design.md`
- `docs/decisions/ADR-064.md`

Preserved measured diagnosis:

- `CONTEXT_QUALITY = SUFFICIENT_V1`
- `SELECTION_PRIMARY_BOTTLENECK = NO`
- `SEMANTIC_SECTION_COMPOSITION = SUFFICIENT_V1`
- `RESULT_PROJECTION = SUFFICIENT`

## Benchmark Variance

Observed Story 0106 engineering-decision results:

### Run 1

- Analysis ID: `ba91569f-4a82-435c-9ed8-8034c101230f`
- Proposals: `1`
- Output quality: materially more specific and conservative than baseline
- Focus: ADR-backed architectural decision documentation

### Run 2

- Analysis ID: `0b16b32d-204c-40ad-98c6-55796a8d8176`
- Proposals: `4`
- Output quality: regressed toward generic technology narratives

## Input Equality Audit

### Identical

- intent ID and version
- analysis type
- provider: `openai`
- model: `gpt-4.1-mini`
- system prompt text
- output schema
- `selectedInsights` semantic content
- `selectedHumanContextInputs`
- `selectedObservations` semantic content
- `repositoryContext.evidence` semantic layer mix
- `semanticSections` structure and counts
- `relationshipHighlights = []`
- `existingArchitectureKnowledge = []`
- no evolution context

### Semantically Equivalent

- `selectedKnowledge` overall
- `semanticSections`
- rendered prompt intent and decision-relevant semantics

### Different

- analysis IDs / timestamps
- project profile IDs / analysis linkage / generatedAt timestamps
- selection digest and prompt digest
- current-analysis repository evidence reference
- some selected fact identities and a few selected fact contents:
  - one `SPRING_BOOT_DETECTED` fact referenced `backend/pom.xml` in one run and `mcp-server/pom.xml` in the other
  - three markdown-document facts differed between runs
  - `discardedKnowledgeCount` differed (`412` vs `414`)

### Classification

- `INTENT_EQUAL = IDENTICAL`
- `MODEL_EQUAL = IDENTICAL`
- `PROVIDER_EQUAL = IDENTICAL`
- `GENERATION_CONFIG_EQUAL = IDENTICAL_OR_NOT_AVAILABLE`
- `SELECTED_CONTEXT_EQUAL = SEMANTICALLY_EQUIVALENT`
- `SEMANTIC_SECTIONS_EQUAL = SEMANTICALLY_EQUIVALENT`
- `RENDERED_PROMPT_EQUAL = DIFFERENT_BUT_SEMANTICALLY_EQUIVALENT`

## Prompt Equality Audit

### Measurement

- system prompt: byte-identical
- user prompt: not byte-identical
- user prompt length: `112135` vs `112127` bytes

### Meaningful Differences

- current analysis IDs and timestamps
- regenerated fact IDs and profile IDs
- one current-analysis repository evidence reference
- a small number of incidental fact-content substitutions

### Conclusion

The prompts are not byte-identical, but no meaningful difference was found in the decision-relevant semantic surfaces needed to explain the large behavioral divergence.

## Model Configuration

From runtime code and Docker configuration:

- provider: `openai`
- model: `gpt-4.1-mini`
- max output tokens: `2000`
- retries: `2`
- response format: OpenAI `responses.parse()` with structured Pydantic model
- temperature: not explicitly set
- top_p: not explicitly set
- seed: not explicitly set

### Runtime Observations

- no retry warnings found in AI-engine logs for either decision run
- no invalid-output retries found in logs
- both runs completed successfully on first successful callback

## Run 1 Proposal Analysis

### Proposal

`Utilisation des Architecture Decision Records (ADR) pour la documentation des décisions d'architecture`

### Classification

`EXPLICIT_DECISION_EVIDENCE`

### Supporting Evidence

- `DECISIONS` Semantic Section contained:
  - `ADR_DIRECTORY_PRESENT`
  - repository evidence item `decision:ae47a47d-65fa-4a30-810c-f114b37755bd`
- repository evidence layer `ADR` included explicit decision summary:
  - `Document Architectural Decisions Using Architecture Decision Records (ADRs)`
  - explicit choice: maintain ADRs as the standard practice
  - explicit rationale: traceability, clarity, historical tracking, onboarding, audits
- validated insights also confirmed ADR documentation practice

### Rationale Support

`STRONG`

The rationale text in Run 1 is substantially aligned with the explicit ADR repository evidence. It is not merely generic framework knowledge; it closely matches an already-documented architectural decision.

### Why Threshold Passed

- explicit decision evidence existed
- explicit choice existed
- rationale was strongly supported by project evidence
- multiple evidence surfaces converged legitimately:
  - DECISIONS section
  - ADR repository evidence
  - validated knowledge
  - fact for ADR directory presence

Run 1 is the positive reference case for Story 0106.

## Run 2 Proposal Analysis

### Decision 1

`Develop REST API Using Spring Boot Controllers`

- Classification: `GENERIC_FRAMEWORK_INTERPRETATION`
- Observed choice support: `PARTIAL`
- Known rationale support: `ABSENT`
- Inferred rationale present: `YES`
- Inferred rationale presented as fact: `YES`
- Unsupported causality: `YES`

Support actually present:

- facts showing Spring Boot presence and REST exposure
- validated insights about Spring Boot REST API application
- repository evidence derived from those same architectural observations

Failure:

- evidence demonstrates current implementation state
- evidence does not demonstrate a historical engineering decision plus project-specific rationale
- rationale is generic framework knowledge, not project evidence

### Decision 2

`Containerize the Project Using Docker and Docker Compose`

- Classification: `GENERIC_FRAMEWORK_INTERPRETATION`
- Observed choice support: `PARTIAL`
- Known rationale support: `ABSENT`
- Inferred rationale present: `YES`
- Inferred rationale presented as fact: `YES`
- Unsupported causality: `YES`

Support actually present:

- Dockerfiles, Docker Compose fact, containerization observation, validated insight

Failure:

- evidence strongly shows the project is containerized
- evidence does not show why it was chosen in this project
- rationale is generic platform knowledge about containerization benefits

### Decision 3

`Implement Multi-Module Maven Build System`

- Classification: `GENERIC_FRAMEWORK_INTERPRETATION`
- Observed choice support: `PARTIAL`
- Known rationale support: `ABSENT`
- Inferred rationale present: `YES`
- Inferred rationale presented as fact: `YES`
- Unsupported causality: `YES`

Support actually present:

- build-module declarations and build-system detection
- validated insight on multi-module Maven structure

Failure:

- evidence shows the project is multi-module and uses Maven
- evidence does not independently show a reconstructible engineering decision and rationale
- rationale is generic build-system benefit language

### Decision 4

`Adopt Architecture Decision Records (ADR) for Documenting Architectural Choices`

- Classification: `EXPLICIT_DECISION_EVIDENCE`
- Observed choice support: `STRONG`
- Known rationale support: `STRONG`
- Inferred rationale present: `NO_MATERIAL_DIFFERENCE`
- Inferred rationale presented as fact: `NO`
- Unsupported causality: `NO`

Support actually present:

- same explicit ADR evidence as Run 1

### Run 2 Summary

Run 2 contains one legitimate ADR-backed decision and three technology-presence decisions that would not satisfy the intended Story 0106 threshold under a strict reading.

## Strong Convergence Analysis

### Observation

The current prompt uses the phrase:

```text
explicit or strongly convergent evidence of a concrete project-specific choice
```

but does not define what qualifies as strong convergence.

### Demonstrated Failure Mode

For Spring Boot, Docker, and Maven, the model appears to treat the following as convergence:

- multiple facts about implementation state
- validated insights restating the same implementation state
- repository evidence derived from those same observations

This is convergence about:

```text
the project uses the technology
```

not convergence about:

```text
the project made a reconstructible engineering decision with supported rationale
```

### Conclusion

`STRONGLY_CONVERGENT_IS_SUFFICIENTLY_DEFINED = NO`

## Evidence Independence

### Observation

Run 2 demonstrates an evidence-independence problem.

Multiple signals existed, but they were not independent decision signals. They were mostly repeated observations of the same implementation state across:

- facts
- validated insights
- repository evidence
- Semantic Section memberships

### Conclusion

`EVIDENCE_INDEPENDENCE_PROBLEM = YES`

## Generic Knowledge Leakage

Run 2 rationales for Spring Boot, Docker, and Maven include generic benefits such as:

- rapid development
- robust framework ecosystem
- deployment consistency
- improved build efficiency
- maintainability and scalability

Equivalent project-specific motivation was not present in the supplied evidence for those decisions.

### Conclusion

`GENERIC_KNOWLEDGE_LEAKAGE = YES`

The model is not merely using generic knowledge for wording. It is using generic knowledge as rationale evidence for project-specific decision proposals.

## Causality Audit

### Run 1

- observed choice support: `STRONG`
- known rationale support: `STRONG`
- inferred rationale present: `LIMITED`
- inferred rationale presented as fact: `NO_MATERIAL_PROBLEM`
- unsupported causality: `NO`

### Run 2

- Spring Boot / Docker / Maven decisions each convert implementation state into decision intent and generic benefits into implied project motivation
- unsupported causal reconstruction is present for those three decisions

### Conclusion

`UNSUPPORTED_CAUSALITY = YES_IN_RUN_2_TECHNOLOGY_DECISIONS`

## Output-Contract Pressure

### Audit

- schema allows `0..10` decisions
- schema does not require non-zero output
- schema does not require coverage of major technologies
- no examples were found that force broad decision coverage

### Conclusion

`OUTPUT_CONTRACT_PRESSURE = LOW`

The primary pressure comes from prompt semantics, not the schema itself.

## Proposal Count Dynamics

### Observation

The current prompt tells the model to identify engineering decisions and says zero is valid, but it does not clearly state:

- prefer fewer well-supported decisions over broader speculative coverage
- stop after the strongest legitimate decisions are found

### Conclusion

`PROPOSAL_COUNT_GUIDANCE_GAP = YES`

This is an amplifier of the threshold ambiguity.

## Ordering / Attention Effects

### Measurement

In both rendered prompts, ADR decision evidence appears early in the prompt and the DECISIONS section appears much later.

Examples from both runs:

- ADR evidence reference at about byte `13067`
- DECISIONS section at about byte `7715x`
- technology-related validated insights also appear later in large repeated clusters

### Conclusion

`ORDERING_OR_ATTENTION_EFFECTS = AMPLIFIER`

Reason:

- ordering did not materially differ between runs
- therefore it cannot explain the divergence directly
- but the overall prompt still contains many technology-state signals that can support a broader speculative generation mode once the threshold is interpreted loosely

## Context Sufficiency Audit

### Conclusion

`CONTEXT_SUFFICIENCY = SUFFICIENT`

Reason:

- Run 1 proves the supplied context was sufficient to isolate a legitimate ADR-backed decision
- the context already distinguishes explicit ADR evidence from generic technology presence
- no missing context was demonstrated that would require ADR-064 resumption or retrieval changes

## Selection Audit

### Measurement

- `COMMIT_DIFF = 12/60 = 20%`
- same repository evidence layer counts across both runs

### Conclusion

`SELECTION_DEFECT_DEMONSTRATED = NO`

Selection may amplify technology visibility, but the evidence does not show a selection defect. The prompt interprets reasonable selected evidence too aggressively.

## Semantic Sections Audit

### Measurement

- same `7` Semantic Sections in both runs
- same section counts in both runs
- same decision-relevant DECISIONS section structure in both runs

### Conclusion

`SEMANTIC_COMPOSITION_DEFECT_DEMONSTRATED = NO`

Semantic Sections remain intact. The problem is how decision reconstruction interprets repeated signals across sections and evidence types.

## Model Variance Role

### Conclusion

`MODEL_VARIANCE_ROLE = AMPLIFIER`

Reason:

- materially similar effective input produced two different semantic outcomes
- but the prompt semantics permit both outcomes because the decision emission gate is under-specified
- nondeterminism explains which mode the model chose, not why both modes are allowed

## Prompt Robustness Assessment

- `DIRECTIONAL_GUIDANCE = STRONGER_THAN_BASELINE`
- `ROBUST_EMISSION_CONTROL = WEAK`

Reason:

- the prompt clearly points toward project-specific and conservative decisions
- but it does not provide an operationally robust emission gate that separates explicit decision evidence from repeated technology-state evidence

## Failure-Layer Diagnosis

- `PRIMARY_FAILURE_LAYER = EMISSION_THRESHOLD`
- `SECONDARY_FAILURE_LAYER = PROMPT_SEMANTICS`
- `AMPLIFIERS = MODEL_VARIANCE, TECHNOLOGY_SIGNAL_DENSITY, MISSING_SELECTIVITY_GUIDANCE`

### Root Cause

The Story 0106 engineering-decision prompt improves directional guidance but does not robustly operationalize its positive and negative emission thresholds. The phrase `explicit or strongly convergent evidence` is under-defined, lacks an evidence-independence rule, and is not paired with a strong selectivity principle. As a result, the model can either:

- correctly collapse onto the explicit ADR-backed decision, or
- incorrectly treat repeated signals of technology usage as sufficient convergence for multiple decision proposals.

## Correction Options

### Option A — Stronger negative rule

Assessment: `HELPFUL_BUT_INSUFFICIENT_ALONE`

Reason:

- would directly reject technology-presence-only decisions
- does not fully define what qualifies as acceptable positive evidence

### Option B — Explicit positive emission gate

Assessment: `HIGH_VALUE`

Reason:

- addresses the missing operational question: what must be true before a decision is emitted?

### Option C — Define strong convergence

Assessment: `HIGH_VALUE`

Reason:

- directly addresses the demonstrated confusion between multiple independent decision signals and repeated implementation-state observations

### Option D — Selectivity / proposal-count guidance

Assessment: `MEDIUM_TO_HIGH_VALUE`

Reason:

- would reduce broad speculative coverage once at least one strong decision is found
- likely useful as a complement, not a standalone fix

### Option E — Output-contract/schema change

Assessment: `NOT_REQUIRED_FOR_FIRST_CORRECTION`

Reason:

- schema limitation around known vs inferred rationale is real but did not cause the variance

### Option F — Generation configuration change

Assessment: `NOT_PRIMARY`

Reason:

- nondeterminism amplifies the issue, but configuration changes would treat the symptom rather than the weak emission gate

### Option G — Context/composition change

Assessment: `REJECTED_BY_EVIDENCE`

Reason:

- Run 1 demonstrates context sufficiency
- no selection or composition defect was shown

## Recommended Minimal Correction

- `RECOMMENDED_OPTION = B + C + D, with A as supporting negative guardrail`

### Rationale

The smallest credible corrective boundary stays within Story 0106 prompt semantics and tightens decision eligibility, not context composition. The prompt needs:

- a clearer positive emission gate
- a definition of strong convergence that requires independent decision signals rather than repeated implementation-state evidence
- explicit selectivity guidance favoring fewer well-supported decisions over broader speculative coverage
- a clearer negative rule that technology presence alone is never enough

### Correction Layer

- `CORRECTION_LAYER = PROMPT_ONLY_CORRECTION`
- `CORRECTION_SCOPE = ENGINEERING_DECISION_PROMPT_SEMANTICS_ONLY_OR_PREDOMINANTLY`

## Story Boundary Decision

- `CORRECTION_BELONGS_TO_STORY_0106 = YES`

Reason:

- Story 0106 is uncommitted
- the primary quality target is not yet demonstrated consistently
- the demonstrated issue sits directly inside the Story 0106 decision prompt behavior

## ADR Assessment

- `ADR_REQUIRED = NO`

Reason:

- no new durable architecture boundary was discovered
- the correction remains inside existing prompt behavior within ADR-064

## Verification Contract For Correction

Required after a corrective implementation:

- targeted prompt tests
- full AI-engine `pytest`
- prompt-size remeasurement
- canonical engineering-decision benchmark
- repeated engineering-decision benchmark
- describe-project regression check if any shared contract wording changes
- architecture zero-delta regression check if any shared contract wording changes

### Recommended repeat count

- `ENGINEERING_DECISION_REPEAT_COUNT_RECOMMENDED = 3 total runs`

Reason:

- one good run plus one bad run already exists
- three total post-correction engineering-decision runs is a reasonable balance between evidence and API/runtime cost

## Corrective Success Criteria

- ADR-backed decision remains eligible
- technology-presence-only decisions are rejected
- generic framework benefits are not converted into project rationale
- proposal count does not inflate for coverage
- repeat runs are materially consistent in decision eligibility semantics
- unsupported causality does not increase
- architecture zero-delta behavior remains unchanged
- describe-project does not regress

## Non-Actions

This investigation does not authorize:

- prompt edits
- schema changes
- grounding changes
- selection changes
- Semantic Section changes
- transport changes
- persistence changes
- model/provider changes
- ADR creation
- Story 0107 creation

## Conclusion

The variance between Run 1 and Run 2 is not explained by a different intent, model, provider, or materially different decision-relevant context. The effective context was semantically equivalent and already sufficient. The variance is caused by a prompt-level decision-threshold ambiguity: the current Story 0106 prompt points the model in the right direction but does not robustly control when an engineering decision should or should not be emitted. Model nondeterminism amplifies that ambiguity, allowing either a narrow ADR-backed interpretation or a broad technology-presence interpretation. The minimal correction should remain inside Story 0106 and tighten the engineering-decision prompt’s emission gate, convergence definition, and selectivity behavior.

Terminal state:

`STORY_0106_ENGINEERING_DECISION_VARIANCE_INVESTIGATION_READY_FOR_HUMAN_REVIEW`
