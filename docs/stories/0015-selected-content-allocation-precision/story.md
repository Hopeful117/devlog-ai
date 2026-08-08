# Story 0015 — Selected Content Allocation Precision

## Story ID
0015

## Title
Prioritize the most useful selected files for bounded content enrichment

## Status
Draft

## Priority
High

## Date
2026-08-09

---

## User Story

As Kiko preparing an Engineering Story,
I want DevLog to allocate its limited source-content slots to the most useful selected files,
So that bounded content helps me understand the central implementation instead of being consumed by
alphabetical tiebreaks or secondary files.

---

## Context

Stories 0012 and 0013 improved Repository Context precision and multi-module file diversity. Story
0014 added safe, revision-traceable text for at most six selected source/test files.

The first real Story 0014 benchmark initially timed out during concurrent local I/O, then succeeded
unchanged in 2552 ms. It returned 59 candidates, 49 selected items, and 27 selected source/test
files. Six source files received content, while 21 selected source/test files were skipped by the
content limit.

All selected source/test evidence had the same final relevance score (`49`).
`SelectedFileContentEnricher` orders equal scores by evidence reference, so alphabetical path order
became the effective content-allocation policy. Two low-value analysis files consumed scarce content
slots, while `SelectedFileContentEnricher` itself—central to the Story—was selected but received no
content.

The previously proposed Java symbol Story would inherit this defect and describe the wrong files
more precisely. Content allocation precision must therefore be corrected and validated first.

---

## Objective

Introduce a deterministic, bounded, explainable policy that chooses which already selected
`SOURCE_FILE` and `TEST_FILE` evidence receives content.

The capability must improve allocation when final ranking scores tie or saturate, without replacing
the global ranker or selector, reading content before allocation, or increasing existing content and
token budgets.

Repository Analysis and Implementation Planning must determine the smallest policy representation
and discriminating path-level signals. The implementation must not special-case Story 0015 filenames
or encode repository-specific paths.

---

## Acceptance Criteria

### AC-1: Allocation remains selected-only

Only evidence already selected by `BudgetedDiverseEvidenceSelector` may be considered for content.
The correction must not read rejected candidates or bypass global selection authority.

### AC-2: Scarce content slots use meaningful deterministic precision

When eligible files exceed the content limit, allocation must use deterministic, story-specific,
path-level evidence that distinguishes stronger matches from weaker ones even when their final
weighted scores are equal.

Alphabetical reference order may remain only a final reproducibility tiebreaker after meaningful
allocation signals are exhausted. It must not silently act as the primary relevance policy.

### AC-3: Ranking and selection ownership remains unchanged

`DeterministicEvidenceRanker`, `BudgetedDiverseEvidenceSelector`, and Context Intelligence retain
their current responsibilities unless Repository Analysis demonstrates that a minimal shared
ranking explanation is required.

The content allocator may consume existing deterministic score criteria, ranking explanations,
selection order, story-term evidence, or a focused versioned allocation policy. It must not create a
second opaque global ranker.

### AC-4: Allocation is explainable

For every eligible selected source/test item, the response must allow Kiko to determine whether
content was allocated, skipped by the file limit, skipped by remaining character/token budget, or
unavailable for another reason.

When allocation priority changes, expose a bounded versioned reason or policy identity sufficient to
explain the ordering without leaking internal paths or content.

### AC-5: Existing bounds remain authoritative

The existing limits for enriched files, per-file characters, aggregate characters, file size,
duration, and total Repository Context tokens must remain enforced.

The policy must not increase default limits merely to hide poor allocation. Final `usedTokens` must
remain within `maximumTokens`.

### AC-6: Content remains post-allocation input

Allocation must use path-level metadata available before file reads. It must not read every selected
file and then use its content to decide which files deserve content.

This Story must preserve Story 0014 security, revision, configuration-exclusion, and failure
boundaries.

### AC-7: Adversarial regression covers equal scores

Add a deterministic regression scenario containing:

* more eligible files than content slots;
* equal final relevance scores;
* a central story-specific file whose reference sorts after distractors;
* low-value files that sort earlier alphabetically.

The central file must receive content before the distractors. Renaming or reordering fixtures without
changing their semantic relevance must not change the intended allocation outcome.

### AC-8: Representative outcome validation is mandatory

Tests must verify not only safety and accounting but the user-facing allocation objective:

* expected high-value files receive content;
* weaker selected files are skipped first under scarcity;
* reasons accurately describe the outcome;
* deterministic inputs produce deterministic allocation;
* no repository-specific filename is hardcoded into the policy.

### AC-9: Existing evidence behavior remains compatible

Source/test/configuration candidate diversity, global selection precision, path-only evidence,
content states, provenance, warnings, decisions, digest, GET/POST serialization, unavailable
workspace behavior, and non-file evidence must remain compatible.

### AC-10: Final accounting and digest remain truthful

Allocation outcomes, returned content, token estimates, `usedTokens`, warnings, decisions, policy
metadata, and digest must describe the final response consistently. Changing a meaningful allocation
input or result must affect the deterministic digest input.

### AC-11: Quality baseline remains healthy

Run focused allocator/enricher, ranker, selector, Repository Context, workspace/reader, and API
tests; the complete backend suite; JaCoCo verification; authenticated SonarQube analysis with the
pinned scanner; and Quality Gate wait.

Completion requires no new unresolved Sonar issue and a passing Quality Gate.

### AC-12: Pre-implementation benchmark remains external

Preserve the existing disposable Story 0014 benchmark outside Git. Its baseline is:

* 59 candidates and 49 selected items;
* 14 selected source and 13 selected test files;
* six content-bearing files, five complete and one truncated;
* 21 selected source/test files skipped by the content limit;
* equal score `49` for all selected source/test evidence;
* central selected enricher skipped while lower-value alphabetical predecessors received content.

Do not convert benchmark output into repository documentation or a production fixture containing
repository-specific expectations.

### AC-13: Post-implementation benchmark verifies effectiveness

After implementation and before Code Review, rerun the same normal DevLog-assisted Repository
Analysis request once with the complete revised Story.

Store results outside Git and compare them factually with the baseline:

* request success, duration, evidence count, tokens, digest, and warnings;
* content-bearing and skipped files;
* whether the central allocation implementation receives content;
* whether low-value distractors consume fewer scarce slots;
* broad and targeted repository reads still required;
* stale, conflicting, noisy, or unexpected evidence.

The benchmark must not replace deterministic tests, alter Human Approval Gates, or claim general
productivity gains from one run. If the allocation objective is not demonstrated, the Story must not
be reported as completed merely because unit tests and Quality Gate pass.

### AC-14: Documentation reconciliation captures the policy

Update canonical architecture, configuration, API, and operational documentation when affected.
Documentation must distinguish global evidence selection from bounded content allocation and record
the practical validation outcome without committing disposable benchmark data.

---

## Scope

### In Scope

* A deterministic versioned priority policy for already selected source/test content.
* Story-specific path-level discrimination under equal or saturated final scores.
* Explicit allocation reasons and compatible metadata.
* Equal-score, filename-order, scarcity, bounds, accounting, digest, API, and regression tests.
* Preservation of Story 0014 safety and content limits.
* Disposable pre/post implementation benchmarks outside Git.
* Documentation reconciliation.

### Out of Scope

* Java symbols, AST parsing, classes, methods, or annotations.
* Content-based ranking or reading all candidates before allocation.
* Changes to default content limits solely to improve benchmark results.
* New global ranking algorithms unrelated to bounded content allocation.
* Dependency graphs, source-test inference, embeddings, or AI interpretation.
* Configuration-file content or secret redaction.
* Persistence changes or trusted knowledge promotion.
* Automatic project resolution or a DevLog Repository Analyst agent.
* Engineering-Skills timeout changes or workflow prompt changes.
* Changes to Kiko ownership or Human Approval Gates.

---

## Architectural Ownership

### DevLog

Owns deterministic selected-content allocation, evidence metadata, provenance, budgets, warnings,
decisions, digest correctness, tests, and API compatibility.

### Engineering-Skills / engineering-story

Owns DevLog invocation, timeout/fallback configuration, benchmark discipline, workflow sequencing,
and Human Approval Gates.

### Kiko

Owns evaluation of context usefulness, targeted repository verification, and Repository Analysis.

### Repository

Remains authoritative for exact implementation behavior.

### Human

Owns the three workflow approvals and decides whether the practical outcome is acceptable.

---

## Risks

### R1: A second hidden ranker is introduced

An independent opaque scoring model would make global selection and content allocation disagree.
The allocator must reuse explainable deterministic signals and keep its narrower responsibility
explicit.

### R2: Tests overfit the benchmark repository

Hardcoded filenames could make the benchmark pass without improving general behavior. Fixtures must
express semantic relevance through generic Story/path relationships and adversarial renaming.

### R3: Better allocation destabilizes budgets or digests

Changing which files receive text changes tokens, warnings, and digest. Final reconciliation must
remain deterministic and truthful.

### R4: One benchmark is mistaken for general proof

The post-implementation run validates this concrete regression only. It cannot establish global
productivity, token savings, or cross-repository quality.

### R5: The consumer timeout remains narrow

The successful retry completed in 2552 ms against a 3000 ms deadline. Timeout configuration belongs
to Engineering-Skills and should be handled separately if failures recur.

---

## Dependencies

* Story 0012 — Engineering Story Evidence Precision — completed.
* Story 0013 — Multi-Module File Candidate Diversity — completed.
* Story 0014 — Bounded Selected File Content — completed.
* ADR-037, ADR-038, ADR-039, ADR-040, and ADR-044.

---

## Definition of Done

* [ ] All acceptance criteria are satisfied.
* [ ] Pre/post benchmark observations remain outside Git.
* [ ] Repository Analysis and Implementation Plan receive explicit human approval.
* [ ] Equal-score allocation regression is demonstrated by deterministic tests.
* [ ] Existing content safety, budgets, and API compatibility remain intact.
* [ ] Complete tests, JaCoCo, SonarQube analysis, and Quality Gate pass.
* [ ] Post-implementation benchmark demonstrates the intended concrete allocation correction.
* [ ] Documentation Reconciliation is complete.
* [ ] Code Review receives explicit human approval.
* [ ] Engineering Report is produced.
