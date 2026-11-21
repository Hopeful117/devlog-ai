# Story 0222 - Abstention Correctness and Confidence Calibration

## Status

IMPLEMENTED - READY FOR HUMAN REVIEW.

This Story defines an evaluation-only slice for abstention correctness and
confidence calibration. It does not authorize production contract changes,
prompt changes, budget changes, retrieval changes, commits, or pushes.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Objective

Determine whether DevLog responses:

- abstain when the authorized evidence is insufficient;
- avoid abstaining when the evidence supports an answer;
- declare confidence consistently with grounded semantic correctness;
- preserve specificity while handling uncertainty honestly.

Confidence is an evaluation signal only. It is never evidence and never overrides
grounding, revision, freshness, truncation, or acceptability constraints.

## Problem

The existing response contracts expose abstention and ordinal confidence
(`HIGH`, `MEDIUM`, `LOW`), and existing evaluation code measures specificity
and acceptability. However, the repository does not yet provide a dedicated,
reviewed evaluation that distinguishes:

- correct abstention from generic or empty output;
- missed abstention from unsupported answers;
- overconfidence from grounded correctness;
- underconfidence from an otherwise well-supported answer;
- semantic outcomes from technical or provider failures.

Without this distinction, a high-confidence response may appear stronger than
its evidence warrants, while a correct abstention may be incorrectly treated as
a failed answer.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Persist a Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0158 - DevLog Story Agent Response Specificity
- Story 0210 - Agent Response Acceptability Contract
- Story 0220 - Revision-Pinned Runtime Loss and Provider Outcome Evaluation
- Story 0221 - Bounded Live Provider Captures and Attribution
- `docs/evaluation/answer-quality-benchmark-specification.md`

## Architectural Constraints

1. Java/Core remains authoritative for scope, revision, context, grounding,
   acceptance, and durable trace ownership.
2. Python remains an evaluation and defensive execution boundary.
3. Evaluation artifacts remain outside trusted project knowledge and production
   proposal workflows.
4. Confidence never substitutes for evidence.
5. `NOT_ESTABLISHED` remains the correct outcome when a relationship or
   conclusion is not established by authorized evidence.
6. Technical failures, incomplete provider results, invalid captures, and
   missing evaluation metadata are `NOT_EVALUATED`.
7. Confidence remains ordinal (`HIGH`, `MEDIUM`, `LOW`); this Story does not
   introduce a numeric calibration mapping.
8. Existing specificity scenarios remain regression inputs.
9. No prompt, retrieval, budget, transport, persistence, or production contract
   changes are included.

## In Scope

- Define a versioned evaluation contract for abstention and confidence.
- Reuse explicit `abstention`, `confidence`, grounding, evidence, freshness,
  revision, truncation, and acceptability metadata.
- Evaluate:
  - correct abstention;
  - missed abstention;
  - unnecessary abstention;
  - confidence-level correctness;
  - overconfidence;
  - underconfidence;
  - specificity regression.
- Exclude technical failures and provider-incomplete results from semantic
  quality denominators.
- Reuse Story 0158 scenarios and Story 0210 provider-free cases.
- Add bounded scenarios for insufficient evidence, contradiction, stale
  current-state claims, truncation, and technically invalid output.
- Require human-reviewed oracles before reporting quality claims.
- Produce deterministic provider-free reports with stable identities and
  digests.

## Out of Scope

- Changing production response schemas or persistence.
- Changing prompts or generation instructions.
- Changing confidence semantics in Core or Python contracts.
- Introducing numeric confidence calibration such as Brier score or ECE.
- Adding RAG, embeddings, vector search, retrieval, ranking, or autonomous loops.
- Increasing budgets or changing provider policy.
- Adding an LLM judge.
- Promoting evaluation results or AI output to trusted knowledge.
- Declaring Story 0158, 0210, 0220, or 0221 human-accepted.

## Acceptance Criteria

1. Every evaluated observation has a verifiable scenario, repository revision,
   and capture identity, or is marked `NOT_EVALUATED`.
2. Technical failures, provider-incomplete results, invalid captures, and
   incomplete metadata are excluded from semantic denominators.
3. Correct abstention is evaluated against explicit, reviewed scenario oracles.
4. The evaluation distinguishes:
   - correct abstention;
   - missed abstention;
   - unnecessary abstention;
   - non-evaluable abstention.
5. An unsupported high-confidence answer cannot pass the grounding or
   acceptability gates.
6. Confidence-level results are reported only for semantically eligible
   observations.
7. Confidence is reported as conditional correctness/support by ordinal level
   and is not converted into an unapproved numeric score.
8. Existing Story 0158 specificity scenarios remain deterministic and pass as
   regression coverage.
9. Truncation, stale or unknown freshness, contradiction, insufficient evidence,
   and `NOT_ESTABLISHED` cases are covered.
10. Human-reviewed oracles and thresholds are clearly distinguished from
    proposed or unreviewed expectations.
11. No production contract, prompt, budget, retrieval, trust boundary, or
    persistence model changes.
12. No capture or report contains secrets, full prompts, repository payloads, or
    raw incomplete provider output.

## Expected Deliverables

- Versioned abstention and confidence evaluation contract.
- Provider-free scenario manifest and oracle format.
- Deterministic evaluator and bounded diagnostics.
- Focused tests for abstention, confidence, acceptability, technical exclusion,
  and specificity regression.
- Structured and human-readable evaluation report.
- Documentation reconciliation report.
- Repository Analysis and Implementation Plan before implementation.

## Human Decisions Required

1. Review the proposed scenario oracles and confidence reporting results.
2. Approve the use of ordinal confidence reporting without numeric calibration.
3. Confirm that unreviewed live captures remain `NOT_EVALUATED`.
4. Accept or reject the Story based on the implementation evidence.

## Gates

1. Story definition approval.
2. Repository Analysis.
3. Repository Analysis approval.
4. Implementation Plan.
5. Explicit implementation authorization.
6. Implementation and independent review.
7. Separate human Story acceptance.

## References

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)
- [Story 0158](../0158-devlog-story-agent-response-specificity/story.md)
- [Story 0210](../0210-agent-response-acceptability-contract/story.md)
- [Story 0220](../0220-revision-pinned-runtime-loss-evaluation/story.md)
- [Story 0221](../0221-bounded-live-provider-captures/story.md)
- [Answer Quality Benchmark](../../evaluation/answer-quality-benchmark-specification.md)
