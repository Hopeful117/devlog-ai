# Story 0131 - DevLog Product Value Evaluation Harness

## Status

`TECHNICAL MEASUREMENT LOOP PARTIAL - ORACLE FROZEN`

## Authorization

This story implements evaluation infrastructure only. It does not authorize a
Historical Engineering Synthesis feature or any production behavior change.

## Scope

- Load and validate the frozen Story 0130 benchmark without changing it.
- Validate an externally produced artifact-resolution manifest at the pinned
  repository revision.
- Score captured case outputs using canonical set intersections.
- Measure context quality and interpretation quality as independent dimensions.
- Support comparable `DIRECT_REPOSITORY`, `DEVLOG_CONTEXT` and controlled
  `GROUND_TRUTH_CONTEXT` conditions.
- Score structured causal, constraint, grounding and change-impact interpretation
  fields without an LLM judge or fuzzy matching.
- Attribute measurable misses to context, interpretation, both, or neither when
  the available evidence is insufficient for attribution.
- Report negative-control, grounding, typed-reference, context-efficiency,
  follow-up-search and stability results.
- Refuse acceptance scoring until the candidate oracle has explicit human
  approval.
- Keep Human Utility unset until the human experiment is performed.

## Non-Goals

- No Java Core, AI Engine production service, prompt, schema, MCP, Angular,
  database or repository-collection changes.
- No LLM judge, fuzzy matching, keyword claim checker, RAG or provider change.
- No fabricated Trading OS artifact resolutions, timings or human scores.
- No oracle answers, scoring IDs, thresholds or human decisions in model-facing
  Ground-Truth Context.
- No production prompt, retrieval, context-selection or domain-contract change
  to improve evaluation results.

## Acceptance Criteria

1. [x] Frozen benchmark validation is deterministic and fail-closed.
2. [x] Revision mismatch and unresolved artifact manifests are rejected.
3. [x] Evidence, constraint, causal-link and impact metrics use exact canonical
   identifiers.
4. [x] Negative control and mandatory threshold results are reported separately.
5. [x] Human Utility cannot be declared by the harness.
6. [x] Existing AI Engine tests remain green.
7. [x] A real DevLog context capture was executed for all four cases.
8. [x] Independent repository ground truth verifies all 19 expected artifacts;
   the separate DevLog manifest retains its 10/19 retrieval result.
9. [x] An exhaustive measured artifact manifest was produced with explicit
   `RESOLVED`/`NOT_FOUND` outcomes.
10. [x] Human validator approves/corrects and freezes the Story 0130 oracle.
11. [ ] A direct repository baseline is executed from an isolated pinned checkout.
12. [ ] A formal RED baseline is captured against the approved oracle.
13. [x] Evaluation-only three-condition contracts and leakage checks exist.
14. [x] Structured interpretation metrics distinguish supplied evidence from
    reasoning over supplied evidence.
15. [ ] Comparable live interpretation captures exist for all three conditions.

## Result

The live DevLog context path remains measured at 10/19 artifacts, with 9 true
retrieval misses. The human oracle is frozen and the real evidence-only
Ground-Truth Context passed leakage checks. Using the README-configured OpenAI
provider, the same `gpt-4.1-mini` interpreter ran three repetitions for all four
cases in both `DEVLOG_CONTEXT` and `GROUND_TRUTH_CONTEXT` (24 calls total).
The Direct Repository agent/tool condition remains blocked, so the formal
three-condition RED baseline is not declared complete. The partial RED result
shows causal accuracy `0.433` for DevLog and `0.483` for Ground Truth; Ground
Truth also fails the CASE-04 negative control in one of three repetitions.

## Experimental Model

The three conditions use the same interpretation response contract while
varying only the evidence source:

```text
DIRECT_REPOSITORY       agent reconstruction + interpretation
DEVLOG_CONTEXT          real POST /api/projects/{projectId}/engineering-story-context?detail=AGENT
GROUND_TRUTH_CONTEXT    complete approved evidence, without oracle answers
```

The Ground-Truth Context contains only artifact references, types and content.
It excludes expected fields, causal classifications, constraint/component/test
scoring identifiers, thresholds and human decisions. Its leakage checker is
mechanical and fails closed.

Context recall is reported separately from causal reasoning accuracy,
constraint interpretation recall, change-impact interpretation, exact
evidence-reference grounding and the structured unsupported-inference measure.
Human / agent utility remains a separate human protocol and cannot be inferred
from these metrics.
