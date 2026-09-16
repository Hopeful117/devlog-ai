# Story 0132 - Repository Analysis

## Baseline

Story0131 is merged on `main` as `8b2be9f`. Its Ground-Truth Context, oracle,
benchmark questions, repository revision and thresholds are frozen. Story0132's
live RED artifact is:

`evaluation/ground-truth-live-2026-09-15.json`

Measured RED values:

- Ground Truth: `openai` / `gpt-4.1-mini`, four cases, three repetitions.
- Positive causal accuracy: `0.3777777777777777`.
- Interpretation stability: `0.3333333333333333`.
- CASE-04: `0/3 NOT_ESTABLISHED`.
- Grounding: pass for all 12 runs.
- Evidence-removal tests: pass.
- Unsupported inference average: `0.5833`; historical Story0131 metric semantics remain unchanged.

## Relevant Production Infrastructure

- `devlog-contracts/.../StoryContextAnalysisResult.java` owns the shared Story Context result contract.
- `ai-engine/app/schemas/story_context_analysis.py` owns the Python structured-output contract.
- `ai-engine/app/prompts/story_context_analysis.py` owns the current model-facing Story Context instructions.
- `ai-engine/app/services/story_context_analysis_generation_service.py` performs Python parsing, defensive validation and one corrective retry.
- `backend/.../AnalyzeStoryContextUseCase.java` performs Java-authoritative callback validation.
- `backend/.../AiReferenceResolver.java`, `AiReferenceRegistry` and mapping snapshots provide the existing typed-reference and authorization infrastructure.
- `ai-engine/evaluations/product_value/interpretation.py` and `causal_evaluation.py` provide evaluation-only adaptation and scoring.

## Current Contract Finding

Story0132 added a first-class `causalClaims` collection with source, target,
classification, evidence basis, evidence references and explanation. This is a
necessary structural boundary, but the RED run proves it is not sufficient:
the model can label entity, chronology, compatibility and related-document
evidence as `DIRECT_DOCUMENTATION`.

Existing `relationType` remains separate and must not be reused as causal
strength. The missing concept is the support role of each cited reference for
the relationship itself.

## CASE-04 Evidence Analysis

The frozen CASE-04 context establishes:

- ADR-043 exists and defines account identity and mode-specific risk facts.
- Story0042 exists and lists ADR-043 as a related ADR.
- `ExecutionConfiguration.java` exists.
- Commit `18f9d997` changes `ExecutionConfiguration.java` during Story0042.
- The artifacts are temporally and topically compatible.

The context does not contain a direct statement that ADR-043 caused the
specific `ExecutionConfiguration` refactor. The current result contract has no
way to distinguish relationship evidence from context evidence, so the model
constructs a plausible narrative and promotes it.

## Recommended GREEN Boundary

Use one evidence-led causal ledger:

```text
candidate source/target
  -> authorized evidence references with support roles
  -> maximum structurally defensible level
  -> model classification and bounded explanation
```

Use the smallest role vocabulary:

- `DIRECT_RELATIONSHIP_STATEMENT`
- `MATERIAL_RELATIONSHIP_SUPPORT`
- `NON_CAUSAL_CONTEXT`
- `CONTRADICTORY_EVIDENCE`

Roles annotate existing typed evidence references. They do not create a second
evidence identity system.

## Boundary and Non-Goals

- Java remains authoritative for context membership, scope, typed references,
  grounding authorization and callback validation.
- Python remains responsible for structured generation and defensive validation.
- The LLM remains responsible for semantic interpretation of evidence content.
- No retrieval, ranking, MCP, RAG, embeddings, model upgrade, Direct Repository
  runner, Trading OS change or benchmark/oracle change is justified.

## Readiness

`GREEN_IMPLEMENTED - GROUND_TRUTH_EVALUATED_RED`
