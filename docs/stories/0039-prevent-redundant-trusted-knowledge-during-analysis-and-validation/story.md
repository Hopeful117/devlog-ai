# Story 0039 — Prevent Redundant Trusted Knowledge During Analysis And Validation

## Status

Draft

## Priority

High

## Objective

Implement the first operational safeguards that prevent DevLog from creating new
redundant trusted knowledge, based on the duplicate policy established by Story
0038.

This Story should address both:

* **upstream prevention** during analysis and proposal generation;
* **downstream protection** during validation / promotion.

The goal is not perfect semantic deduplication.

The goal is to stop obvious redundant trusted-knowledge growth and reduce the
system’s dependence on human vigilance.

## Motivation

Story 0037 improved incremental architecture evolution, but the broader problem
of duplicate trusted knowledge remains open.

Without active prevention:

* future analyses may still generate redundant `NEW` proposals;
* validators may still accept obvious duplicates;
* the trusted knowledge base may keep accumulating low-value near-repetitions.

This Story converts policy into behavior.

## Scope

### In Scope

1. Upstream prevention:
   * reuse trusted knowledge context where relevant;
   * bias the system toward:
     * `NO_SIGNIFICANT_DELTA`
     * `ENRICHES`
     * or no proposal
     instead of duplicate `NEW`.

2. Downstream safeguard:
   * add Core-side duplicate detection before acceptance / promotion, according
     to the Story 0038 policy.

3. Support at least obvious duplicate handling for trusted knowledge:
   * exact duplicate
   * strongly equivalent near-duplicate where policy allows enforcement

4. Add regression tests proving:
   * repeated equivalent analysis does not create redundant trusted knowledge;
   * valid enrichments still pass;
   * project isolation is preserved;
   * non-duplicates are not wrongly blocked.

### Out of Scope

* cleaning existing DB duplicates
* embeddings / vector similarity
* contradiction / supersession lifecycle
* large UI redesign

## Constraints

* must follow the duplicate policy from Story 0038
* must preserve ADR-006 lifecycle ownership
* must not weaken quality gates or validation guarantees

## Acceptance Criteria

* AC-1: obvious duplicate trusted knowledge is prevented according to the
  approved policy.
* AC-2: equivalent repeated analysis can resolve to no proposal or a
  non-duplicate delta path instead of creating redundant trusted knowledge.
* AC-3: valid enrichments remain accepted through the normal lifecycle.
* AC-4: false positive blocking is covered by tests and remains bounded.
* AC-5: quality gates pass unchanged.

## Dependencies

* Story 0038
* ADR-006
* ADR-051
* ADR-050

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
