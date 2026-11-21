# Story 0223 - Agentic Quality Evaluation and Real Engineering Story Pilot

## Status

IMPLEMENTED - human acceptance pending.

This Story defines the final evaluation and bounded pilot slice of the
`agent-response-reliability` epic. It does not authorize provider calls, paid
experiments, production changes, prompt changes, budget changes, retrieval
changes, commits, or pushes.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Objective

Evaluate whether DevLog provides useful, specific, grounded, and honest
briefings for real engineering questions when compared with bounded alternative
treatments under the same frozen questions, repository revision, model policy,
and reviewed oracle.

The evaluation must distinguish:

- context and evidence availability;
- direct repository investigation;
- bounded Story Context investigation;
- synthesis and grounding quality;
- abstention and uncertainty handling;
- provider and runtime failure;
- human utility.

The Story must produce evidence for a human architectural or product decision.
It must not declare a treatment superior automatically.

## Problem

The epic now has contracts for acceptability, freshness, runtime loss,
provider attribution, specificity, abstention, and ordinal confidence. These
contracts have mostly been validated through provider-free fixtures or
historical pilot artifacts.

The remaining question is whether the bounded DevLog and investigation paths
help answer real engineering questions better than a direct repository
inspection, while preserving Core authority and making incomplete execution
visible.

Existing comparative evidence is not sufficient to answer this question:

- historical provider failures and incomplete observations must not be counted
  as semantic failures;
- the current one-shot DevLog path and direct-agent path are not the same as the
  bounded Story 0184 investigation path;
- passing runtime or schema tests does not establish answer quality;
- a small pilot cannot support broad population claims.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Persist a Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0133 - Comparative Baseline Offline Evaluation Infrastructure
- Story 0158 - DevLog Story Agent Response Specificity
- Story 0184 - Bounded Story Context Investigation
- Story 0210 - Agent Response Acceptability Contract
- Story 0220 - Revision-Pinned Runtime Loss and Provider Outcome Evaluation
- Story 0221 - Bounded Live Provider Captures and Attribution
- Story 0222 - Abstention Correctness and Confidence Calibration
- `docs/evaluation/answer-quality-benchmark-specification.md`
- `docs/investigations/engineering-analyst-implementation-roadmap-proposal.md`
- `docs/investigations/current-state-quality-bottleneck-investigation.md`

## Dependencies

The following must be human-accepted or explicitly classified as prerequisites
before official pilot conclusions:

- Story 0210 acceptability contract and baseline;
- Story 0220 runtime loss evaluation;
- Story 0221 bounded live captures;
- Story 0222 abstention and confidence evaluation;
- benchmark questions and their human-reviewed oracles;
- the selected repository revision and source identity;
- the provider/model/configuration policy used for the campaign.

Technical implementation may prepare provider-free readiness before every
dependency is accepted, but it must not publish official semantic conclusions
from unaccepted contracts or oracles.

## Experimental Treatments

The initial pilot compares exactly three distinct treatments:

### `DEVLOG_CONTEXT`

The current DevLog-mediated one-shot Story Context path using the frozen
authorized context projection.

### `AGENT_DIRECT`

A direct repository inspection treatment using only bounded, read-only,
revision-pinned repository operations. It must not access DevLog, MCP context,
oracle data, other treatment outputs, web resources, or external memory.

### `STORY_CONTEXT_INVESTIGATION`

The bounded Story 0184 investigation path using only Core-authorized search and
read actions. It must remain distinct from `AGENT_DIRECT`; their traces,
budgets, context strategies, and failure modes must not be silently merged.

The treatment identity, policy version, model configuration, repository
revision, question identity, and repetition are part of every observation.

## Benchmark Population

The full pilot uses the eight versioned benchmark questions `Q1` through `Q8`
from the Answer Quality Benchmark Specification. They cover:

- architecture and ownership;
- code behavior and idempotency;
- historical architectural decisions;
- impact analysis;
- code/document inconsistency;
- evidence-loss diagnosis;
- insufficient evidence and abstention;
- contradiction and historical interpretation.

The initial technical validation uses one approved question per treatment before
any campaign expansion.

The complete campaign matrix is:

```text
8 questions x 3 treatments x 3 repetitions = 72 assigned observations
```

Missing assignments remain missing. They are never converted into semantic
failures.

## In Scope

### Phase 0 - Freeze the Investigation Record

- Freeze question text, question versions, repository revision, source identity,
  oracle version, expected evidence, forbidden claims, expected abstention, and
  confidence review rules.
- Record treatment policy identities and contamination restrictions.
- Define capture retention, redaction, and artifact identity rules.
- Confirm whether live provider validation is required for Story acceptance or
  remains a follow-up.

### Phase 1 - Provider-Free Readiness

- Validate the assignment matrix and deterministic ordering.
- Validate treatment isolation and oracle exclusion from treatment inputs.
- Validate revision, model, configuration, policy, question, repetition, and
  scoring identities.
- Validate bounded raw captures, hashes, replay, missing states, and failure
  attribution without provider calls.
- Validate assignment states:
  `ASSIGNED`, `EXECUTED`, `SEMANTIC_ELIGIBLE`, `INVALID`, `MISSING`.

### Phase 2 - Technical Validation

- Execute at most one approved question per treatment after separate live
  authorization.
- Establish whether each treatment can reach a usable, attributable terminal
  result.
- Stop on provider-incomplete output, identity mismatch, tool-contract failure,
  timeout, or capture-integrity failure.
- Do not tune, retry, or expand the campaign without a new human decision.

### Phase 3 - Bounded Quality Pilot

- Run the 72-cell campaign only after technical validation passes or its
  limitations are explicitly accepted.
- Randomize isolated treatment order where practical.
- Preserve the same question, repository revision, model/configuration, oracle,
  and scoring identities across comparable treatments.
- Blind primary human scoring to treatment labels.
- Keep technical eligibility separate from semantic quality.
- Preserve all provider failures, timeouts, incomplete outputs, and missing
  assignments as explicit non-quality outcomes.

### Phase 4 - Human Review and Decision Input

Produce a neutral report containing:

- assignment, execution, eligibility, invalid, and missing counts;
- paired complete and incomplete counts;
- execution completeness per treatment;
- grounded correctness for eligible observations;
- claim precision/recall and evidence support;
- correct abstention, failed abstention, and overclaim rates;
- confidence-level conditional results;
- specificity and next-step utility;
- provider/runtime failure attribution;
- latency, token, operation, and cost data when available;
- reviewer agreement, disagreements, and adjudication status;
- limitations and follow-up decisions.

Human utility must remain a separate protocol. It cannot be inferred from
automated scores, token usage, citation count, or treatment identity.

## Out of Scope

- Production Java, Python service, MCP, REST, frontend, database, or schema
  changes.
- Prompt tuning during the campaign.
- Budget, retry, provider, model, or retrieval changes.
- RAG, embeddings, vector search, or a new retrieval architecture.
- Python access to the repository, database, or filesystem.
- Autonomous loops or unbounded delegation.
- LLM-as-judge scoring.
- Oracle leakage into treatment inputs.
- Mixing `AGENT_DIRECT` with `STORY_CONTEXT_INVESTIGATION`.
- Treating historical V4 artifacts as new baseline observations.
- Automatic architecture selection, product superiority claims, or production
  rollout decisions.
- Promotion of output or evaluation results to trusted knowledge.
- Provider calls, network access, paid experiments, commits, pushes, or merges
  without their separate explicit approvals.

## Architectural Constraints

1. Java/Core remains authoritative for scope, revision, context construction,
   grounding, action authorization, and callback acceptance.
2. Python remains a probabilistic execution and defensive validation boundary.
3. Direct repository access is limited to the explicitly isolated
   `AGENT_DIRECT` treatment policy.
4. Story 0184 investigation actions are executed only by the authorized Core
   capability.
5. AI output remains non-trusted and read-only.
6. Technical failure, provider-incomplete output, invalid capture, and missing
   assignment remain distinct from semantic incorrectness.
7. Confidence never replaces evidence or raises an acceptability status.
8. Execution completeness is reported alongside every eligible-quality result.
9. No treatment can see oracle data or another treatment's answer, trace, or
   diagnostics during execution.
10. Historical artifacts remain explicitly excluded from official denominators.

## Raw Observation Contract

Every raw observation must preserve immutable, bounded, hash-bound fields for:

- observation, benchmark, question, and treatment identities;
- question version and repetition;
- repository identity and revision;
- policy, model, and configuration identities;
- exact treatment input and bounded raw output;
- tool/action trace where applicable;
- provider outcome and terminal status;
- oracle reference without embedding oracle answers in treatment input;
- raw input/output digests;
- bounded usage, latency, and cost metadata;
- capture timestamp and run identity.

Raw artifacts must not contain credentials, bearer tokens, full unbounded
repository payloads, or raw incomplete provider output outside the approved
bounded redacted contract.

## Evaluation Rules

- A missing or invalid observation is not a semantic failure.
- A provider failure or incomplete output is `NOT_EVALUATED` for semantic quality.
- A response-bearing semantic failure remains a captured semantic observation.
- A high eligible-only score is invalid as a quality conclusion when execution
  completeness is poor.
- Human-reviewed oracles are required before official quality claims.
- Negative, neutral, and mixed comparative results are valid outcomes.
- The pilot cannot authorize a follow-up production Story by itself.

## Acceptance Criteria

1. The eight benchmark questions and their oracle identities are versioned and
   human-reviewed before official semantic reporting.
2. The three treatment policies are distinct, versioned, bounded, and isolated.
3. Oracle answers, expected evidence, scoring fields, and treatment outputs
   cannot cross the treatment boundary.
4. The 72-cell assignment matrix is deterministic and each cell has an explicit
   assignment state.
5. Repository, question, model, configuration, policy, repetition, and oracle
   identity mismatches fail closed.
6. Provider-free preflight and replay pass without provider or network calls.
7. Technical validation stops on provider, identity, tool-contract, timeout, or
   capture-integrity failure rather than silently expanding scope.
8. Live observations are stored in an isolated, bounded, redacted, immutable,
   hash-bound pilot area and cannot enter official baselines without explicit
   classification.
9. Technical failures, incomplete outputs, invalid captures, and missing
   assignments are excluded from semantic denominators and remain visible.
10. Every quality result includes execution completeness and all relevant
    assignment/eligibility counts.
11. Primary human scoring is blinded to treatment labels and uses the reviewed
    claim-level oracle.
12. Human utility is not declared by the evaluator and remains a separate
    protocol result.
13. The final report distinguishes observed evidence, interpretation,
    recommendation, unknowns, and unresolved disagreements.
14. No production contract, prompt, budget, retrieval policy, trust boundary,
    or authority boundary changes.
15. No superiority claim, architecture decision, rollout, commit, or push is
    performed automatically.

## Human Decisions Required

1. Confirm that the three-treatment comparison is the intended final pilot:
   `DEVLOG_CONTEXT`, `AGENT_DIRECT`, and `STORY_CONTEXT_INVESTIGATION`.
2. Approve the eight-question, three-repetition, 72-assignment campaign size.
3. Approve the human oracle review and blinded scoring protocol.
4. Decide whether live provider validation is required for Story acceptance or
   remains an explicitly documented follow-up.
5. Approve retention and redaction rules for pilot artifacts.
6. Confirm whether a human-utility session is available and governed by a
   separate protocol.

## Gates

1. Story definition approval.
2. Human acceptance or explicit prerequisite classification for Stories 0210,
   0220, 0221, and 0222.
3. Repository Analysis.
4. Repository Analysis approval.
5. Implementation Plan.
6. Explicit implementation authorization.
7. Provider-free readiness validation.
8. Separate explicit authorization for any live provider validation.
9. Independent review of implementation and pilot artifacts.
10. Human review of the final report.
11. Separate human Story acceptance.

## Expected Deliverables

- Versioned pilot protocol and treatment-policy manifests.
- Frozen question and oracle compatibility manifest.
- Provider-free assignment, isolation, replay, and completeness fixtures.
- Bounded raw-observation and redacted capture validation.
- Technical-validation and pilot-run interfaces with explicit authorization
  checks.
- Human scoring sheets and adjudication records.
- Neutral structured and human-readable final report.
- Decision record identifying measured conclusions, unknowns, and follow-up
  options without self-authorizing implementation.
- Repository Analysis, Implementation Plan, and Implementation Report.

## References

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)
- [Story 0133](../0133-comparative-baseline-offline-evaluation-infrastructure/story.md)
- [Story 0158](../0158-devlog-story-agent-response-specificity/story.md)
- [Story 0184](../0184-bounded-story-context-investigation/story.md)
- [Story 0210](../0210-agent-response-acceptability-contract/story.md)
- [Story 0220](../0220-revision-pinned-runtime-loss-evaluation/story.md)
- [Story 0221](../0221-bounded-live-provider-captures/story.md)
- [Story 0222](../0222-abstention-confidence-calibration/story.md)
- [Answer Quality Benchmark](../../evaluation/answer-quality-benchmark-specification.md)
- [Comparative Baseline V4 Official Collection](../../evaluation/comparative-baseline-v4-official-collection-report.md)
- [Engineering Analyst Roadmap](../../investigations/engineering-analyst-implementation-roadmap-proposal.md)
- [Current-State Quality Bottleneck Investigation](../../investigations/current-state-quality-bottleneck-investigation.md)
