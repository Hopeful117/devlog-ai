# Story 0130 - DevLog Product Value and Human/AI Utility Parity Investigation

## Status

`PHASE 2 REFINED - EVALUATION DESIGN COMPLETE - HUMAN ORACLE VALIDATION REQUIRED`

## Scope

Investigation and refinement only. This story evaluates whether the current
DevLog product creates meaningful value for a human engineer and for an
AI/coding agent, and whether those values are materially comparable. It does
not implement a feature, change production prompts, or alter the grounding and
trust model.

## Baseline

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `9cc3f9488a13db1002cdda7044292cc93d6cbc46` |
| ORIGIN_MAIN | `9cc3f9488a13db1002cdda7044292cc93d6cbc46` |
| Worktree at start | Clean |
| Latest completed story | Story 0129, accepted |
| Latest accepted ADR | ADR-068 |
| Governing ADRs | ADR-006, ADR-063, ADR-067, ADR-068 |
| Production authorization | `NO` |

## Product Questions

1. Does DevLog materially reduce human engineering effort?
2. Does DevLog give a coding agent context that is better or cheaper than Git,
   search, repository files, ADRs and Stories?
3. Does each intended consumer receive useful capability, even where the
   presentation differs?
4. Is the device communication path real and useful enough to justify
   interruption?

## Case Selected

The live DevLog database contains `trading-os`, so the proposed Trading OS
case was used. The case is the multi-Story evolution around persisted account
identity, PAPER execution, position valuation and full exit, including Stories
0039-0042 and ADR-042/ADR-043. It contains architectural decisions, several
large implementation/review/documentation commits, later dependent changes,
and real imported repository history.

The case is suitable because it requires answering why identity, risk,
execution, settlement and PAPER behavior evolved, not merely describing the
current framework stack.

## Acceptance Criteria

1. [x] Current remote-backed repository state and real production entry points
   are recorded.
2. [x] Human and AI paths are traced independently.
3. [x] A real complex imported project evolution is exercised without
   fabricating unavailable data.
4. [x] Actual human-facing analysis results are classified against product
   value, not correctness alone.
5. [x] Actual AI-facing interfaces are inspected and at least one existing
   interface is exercised.
6. [x] Direct repository reconstruction is used as the comparison baseline.
7. [x] Device architecture is distinguished from proven end-to-end delivery.
8. [x] Human/AI parity and source-to-output reachability are documented.
9. [x] A smallest next vertical slice and separate human/AI success criteria
   are recommended.
10. [x] No Java, Python, Angular, migration, MCP implementation or production
    prompt is modified.
11. [x] Four benchmark cases, including a negative causal control, are frozen
    in a versioned machine-readable suite.
12. [x] Candidate ground truth, human validation requirements and artifact
    resolution rules are documented.
13. [x] Deterministic metrics, live evaluation, human protocol and thresholds
    are defined before any future implementation.
14. [x] Engineering correctness, AI Utility and Human Utility are independent
    mandatory gates.
15. [x] Benchmark leakage, versioning, direction-rethink and RED-baseline rules
    are documented.

## Decision

### Phase 1 - Product Value Investigation

Phase 1 is human-reviewed and remains the historical baseline:

```text
CURRENT_HUMAN_UTILITY = LOW
CURRENT_AI_UTILITY = LOW
HUMAN_AI_PARITY = LOW / FAILING
```

### Phase 2 - Evaluation Harness Refinement

Phase 2 freezes `benchmark-suite-v1`, defines candidate ground truth, direct
repository and human protocols, deterministic metrics, thresholds, leakage
guards and the three-gate acceptance rule. The candidate oracle still requires
explicit human validation before it can be acceptance truth. No executable
harness implementation is authorized in this pass.

```text
CURRENT_HUMAN_UTILITY = LOW
CURRENT_AI_UTILITY = LOW
HUMAN_AI_PARITY = LOW / FAILING
NEXT = HUMAN_AND_AGENT_HISTORICAL_CONTEXT_SLICE
NEW_ADR_REQUIRED = NO (unless architecture review chooses to amend ADR-063)
STORY_0130_PHASE_1_STATUS = HUMAN_REVIEWED
STORY_0130_PHASE_2_STATUS = REFINED; ORACLE_VALIDATION_REQUIRED
BENCHMARK_VERSION = benchmark-suite-v1 / 1.0.0
EVALUATION_HARNESS_IMPLEMENTATION_AUTHORIZED = NO
IMPLEMENTATION_AUTHORIZED = NO
PRODUCTION_CODE_CHANGED = NO
```

The investigation does not justify migrating another typed contract, adding
RAG, adding a provider, or redesigning device communication before a useful
question can be demonstrated end to end.
