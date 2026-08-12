# Story 0038 — Knowledge Duplicate Policy And Detection Model

## Status

Draft

## Priority

High

## Objective

Define the architectural policy for duplicate and near-duplicate knowledge in
DevLog AI before implementing additional prevention or remediation behavior.

The system currently distinguishes technical duplicate handling in several
places:

* repository evidence deduplication;
* deterministic selection;
* callback idempotence;
* prompt / context digests;
* engineering-event duplicate rejection within one result set.

However, DevLog still lacks an explicit policy for **trusted knowledge
duplication**, especially for semantically equivalent or near-equivalent
`Insight` records.

This Story establishes:

* what DevLog considers a duplicate;
* which duplicate forms are acceptable in proposal history;
* which duplicate forms are unacceptable in trusted knowledge;
* whether the system should warn, block, merge, or defer;
* where responsibility belongs between:
  - analysis-time prevention;
  - validation-time safeguards;
  - human reviewer judgment;
  - later remediation.

## Motivation

Without an explicit policy, duplicate handling drifts into ad hoc heuristics or
human vigilance.

That is undesirable because:

* duplicate trusted knowledge degrades DevLog’s project memory;
* relying on the human validator to notice duplicates is weak governance;
* duplicate policy directly influences ADR-050 incremental-evolution behavior;
* remediation stories are unsafe unless the target semantics are already clear.

This Story should therefore answer the policy question first, before adding more
mechanisms.

## Scope

### In Scope

1. Define duplicate categories for DevLog knowledge lifecycle:
   * exact duplicate proposal
   * repeated proposal with distinct lifecycle history
   * exact trusted-knowledge duplicate
   * semantic near-duplicate trusted knowledge
   * legitimate enrichment vs duplicate restatement
   * historically valid successor vs duplicate

2. Distinguish acceptable duplication in:
   * `ValidatableProposal` history
   * trusted `Insight` knowledge

3. Define the intended balance between:
   * upstream prevention during analysis;
   * downstream safeguards during validation / promotion;
   * manual human review;
   * later audit / remediation.

4. Define minimum comparison signals for later implementation stories, such as:
   * project scope
   * proposal / source category
   * normalized knowledge type
   * source semantic type
   * target enrichment identity
   * title / summary / rationale equivalence heuristics
   * evidence overlap

5. Decide the first enforcement policy for obvious duplicates:
   * observe only
   * warn
   * hard block exact duplicates
   * soft block likely semantic duplicates

### Out of Scope

* implementing the enforcement itself
* cleaning existing database duplicates
* introducing embeddings or vector search
* contradiction / supersession lifecycle redesign
* UI redesign

## Constraints

* ADR-006 remains authoritative for proposal history and immutable decisions.
* ADR-050 remains authoritative for incremental knowledge evolution.
* The policy must not make trusted knowledge depend primarily on human
  attentiveness.
* The policy must preserve legitimate historical proposal history.

## Impact

Likely affected later by this Story’s decisions:

* architecture and non-architecture analysis contracts
* validation lifecycle
* promotion logic
* proposal review UX
* duplicate audit / remediation tooling

## Acceptance Criteria

* AC-1: DevLog duplicate categories are explicitly defined and distinguished.
* AC-2: The Story states which duplicate forms are acceptable in proposal
  history and which are not acceptable in trusted knowledge.
* AC-3: The Story defines the intended balance between upstream prevention,
  downstream safeguards, and human review.
* AC-4: The Story defines the minimum comparison signals required for later
  implementation.
* AC-5: The Story recommends an initial enforcement policy for obvious trusted
  duplicates.
* AC-6: The Story clearly identifies what should be deferred to follow-up
  implementation or remediation stories.

## Dependencies

* ADR-006
* ADR-049
* ADR-050
* Story 0037

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
