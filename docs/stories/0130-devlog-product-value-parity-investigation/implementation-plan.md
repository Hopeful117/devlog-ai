# Story 0130 - Evaluation Harness and Recommended Next Slice (Not Authorized)

## Authorization

`NOT_AUTHORIZED_FOR_IMPLEMENTATION`

This document is an investigation recommendation only. It does not authorize
production code, prompt, schema, MCP, device or migration changes.

The evaluation design itself is complete as documentation. A later, separately
authorized harness implementation may be proposed after a human validates the
oracle. That implementation must not change the frozen cases or thresholds.

`EVALUATION_HARNESS_IMPLEMENTATION_AUTHORIZED = NO`
`PRODUCTION_ANALYSIS_IMPROVEMENT_AUTHORIZED = NO`

## Recommendation

The strongest next vertical slice is a **bounded historical engineering
synthesis for one concrete change question**, delivered through the existing
Core-owned context boundary and made inspectable by both consumers.

Example question for the proven benchmark case:

> Why did PAPER account execution and settlement evolve across Stories 0039-
> 0042, which ADR/Story constraints govern the current implementation, and
> what components/tests are affected by changing it?

This is not a generic architecture summary, a global typed migration, RAG,
vector search, a new provider, a generic agent runtime, or device redesign.

## Smallest Coherent Scope

1. Define one explicit historical/change-impact intent with bounded input:
   project, file/component scope, story/commit window, and human question.
2. Produce a Core-authorized evidence set that contains chronology, selected
   commit diffs, relevant ADR/Story artifact bodies or explicit absence,
   current source/test references, freshness and typed references.
3. Produce one structured non-trusted synthesis distinguishing factual
   extraction, grounded interpretation, uncertainty and recommendation.
4. Expose the same canonical result through the human REST/UI projection and
   the agent/MCP projection, with consumer-appropriate presentation.
5. Preserve ADR-006: synthesis remains an analysis snapshot or proposal as
   currently governed; it is never silently promoted to trusted knowledge.
6. Preserve ADR-068: all citable output uses Core-owned typed references and
   fail-closed validation.

## Explicit Non-Goals

- No generic ContextPack.
- No retrieval/vector/RAG infrastructure.
- No broad typed-reference migration.
- No automatic trusted-knowledge promotion.
- No proactive device notification requirement.
- No OpenCode-specific integration.
- No claim that human and agent payloads must be byte-identical.

## Human Success Criteria

- For the Trading OS PAPER execution question, a human can locate the relevant
  ADRs, Stories, commits, changed files and tests from one DevLog result without
  manually reconstructing the chronology from more than three separate
  repository searches.
- The result contains at least one non-obvious, evidence-linked relationship
  between an earlier decision, a later implementation change and a current
  constraint, or explicitly reports that such a relationship is unavailable.
- A human reviewer can distinguish fact, interpretation, uncertainty and
  recommendation, and can open every cited artifact or see a deterministic
  missing-evidence reason.
- In a timed comparison over three predefined questions, median answer time is
  at least 30% lower than direct repository reconstruction, with no unsupported
  factual claim accepted as a success.

## AI Success Criteria

- For the same three predefined questions, the agent receives the relevant ADR,
  Story, commit/diff and source/test references in one bounded response, or an
  explicit missing-evidence result.
- At least one question that requires chronology and causal linkage is answered
  with fewer than three additional direct repository searches, while preserving
  typed references and freshness.
- Every factual/interpretative statement in the structured result resolves to a
  Core-authorized reference; unsupported claims fail validation.
- The result is no larger than the agreed context budget and reports candidate
  truncation, freshness and exclusions instead of silently implying completeness.
- In a three-run replay over fixed input and provider settings, the evidence set
  and grounding references are stable; only clearly classified interpretation
  wording may vary.

## Parity Gate

The slice is not successful if only the human UI improves or only MCP improves.
For the same project, scope and question, both consumers must be able to find
and inspect the canonical historical evidence and the same trust/freshness
metadata. Presentation may differ.

## Required Evaluation Before Implementation

- Freeze three Trading OS questions and their direct-repository answers.
- Capture direct steps, elapsed time and context size for each answer.
- Capture DevLog candidate/selected counts, freshness, warnings, references,
  prompt/result classification and follow-up steps.
- Include a negative case where DevLog must say that the causal relationship is
  not established.
- Do not use device interruption as a success metric until content quality
  passes the human and AI gates.

## Future Harness Contract

The future executable harness must consume the versioned JSON case definitions
and produce machine-readable results per case, including evidence/constraint/
causal/change-impact recall and precision, unsupported claim rate, negative
control status, context efficiency and evidence stability. It must exercise the
real Core/imported-project path where possible. A deterministic suite may score
references, identifiers, grounding, size and stability; a live suite must score
provider-backed output and persistence separately. Neither suite may mark
Human Utility PASS.

The human protocol is a required companion experiment, not a fixture and not an
LLM-as-judge substitute.

## Exact Next Authorized Action

1. Human validator resolves and approves/corrects `evaluation/ground-truth-v1.md`
   against the pinned Trading OS revision.
2. Human evaluator executes the direct-repository and DevLog baseline protocols
   for CASE-01 through CASE-04 and records real counts/timings.
3. Human reviews the resulting RED baseline and explicitly authorizes a separate
   evaluation-harness implementation Story if the oracle is sound.

No Historical Engineering Synthesis implementation is authorized by this
refinement.
