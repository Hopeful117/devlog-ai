# Repository Analysis

## Story Understanding

Story 0015 now requests a deterministic, bounded, explainable policy for deciding which already
selected `SOURCE_FILE` and `TEST_FILE` evidence receives Story 0014 content when eligible files
outnumber the six available content slots.

The objective is to prevent equal or saturated final scores from turning alphabetical reference
order into the effective relevance policy. The most story-specific selected files should receive
content before weaker selected distractors, while global ranking/selection authority, workspace
safety, revision provenance, content/token limits, and API compatibility remain intact.

The Story explicitly includes adversarial equal-score tests, outcome-oriented validation, and one
post-implementation benchmark compared factually with the pre-implementation baseline. It excludes
Java symbols, AST parsing, content-based pre-allocation ranking, increased limits used to hide the
defect, persistence, AI interpretation, and Engineering-Skills changes.

## Repository Summary

The relevant Java 21 Spring Boot backend pipeline is:

```text
RepositoryStructureCollector path candidates
→ DeterministicEvidenceRanker
→ BudgetedDiverseEvidenceSelector
→ SelectedFileContentEnricher
→ RepositoryContextEngine final diagnostics/budget/digest
```

Story 0012 added `EvidencePrecisionPolicy` and category-aware global selection. Story 0013 ensured
multi-module source/test/configuration candidate diversity. Story 0014 added post-selection bounded
content, explicitly preserving path-level ranking and selection.

The defect is isolated inside the content phase. `SelectedFileContentEnricher` filters already
selected source/test evidence and orders it by `relevanceScore` descending, then `reference`.
`DeterministicEvidenceRanker` caps semantic and guidance scores at 100. In the benchmark, many files
matched enough Story terms to saturate both criteria; identical layer, history, confidence, and
recency criteria then produced final score 49 for all 27 selected source/test items. Reference order
therefore chose the six files read.

## Affected Modules

* **Repository Context enrichment — `repositorycontext.enrichment`**: owns the selected-only content
  allocation loop, content limits, workspace reuse, states, warnings, and final accounting. This is
  the primary affected module.
* **Repository Context intelligence/ranking — `repositorycontext.intelligence` and
  `repositorycontext.ranking`**: own the existing precision policy, score criteria, term frequency,
  and ranking explanations. They are relevant sources of deterministic signals but must not lose
  global ownership or be redesigned without evidence.
* **Repository Context selection — `repositorycontext.selection`**: owns candidate selection and
  category concentration. Its behavior should remain unchanged; tests must prove the content phase
  does not bypass it.
* **Repository Context model/engine — `repositorycontext`**: owns evidence serialization, final
  tokens, decisions, warnings, diagnostics, and digest. It may require additive allocation metadata
  or reason reconciliation.
* **Project Context API — `projectcontext`**: serializes evidence and must remain request-compatible.
* **Tests and documentation**: enrichment, ranker, selector, engine, controller, README, roadmap, and
  ADR-044 are directly relevant.

## Existing Implementation

`DeterministicEvidenceRanker` builds semantic and guidance term models from the Story and all
candidates. A matching discriminating term contributes according to document frequency, but the
criterion is capped at 100. It records detailed explanations such as matched/common terms while the
final `EvidenceScore` retains only the capped criterion and weighted score.

`BudgetedDiverseEvidenceSelector` receives the ranked list, applies global item/token budgets and
the Context Intelligence precision policy, and emits selected evidence and decisions. The benchmark
shows it successfully selected the central `SelectedFileContentEnricher`; the immediate defect is
not absence from global selection.

`SelectedFileContentEnricher` then independently sorts selected source/test evidence by final score
and reference. It enriches until `RepositoryContentPolicy.maxEnrichedFiles` (default six), aggregate
characters, or remaining context tokens are exhausted. Later eligible items receive
`SKIPPED/ENRICHED_FILE_LIMIT`. The content object exposes extraction status and policy but does not
explain why one equal-score file preceded another.

The Story 0014 unit test verifies count and character limits using only two equally relevant files;
it does not place a stronger semantic match after alphabetical distractors. Engine tests verify
digest/accounting and broad selection behavior, not scarce content-slot usefulness. The prior test
policy therefore established mechanical correctness without validating the primary user outcome.

The first real benchmark attempt timed out at the Engineering-Skills 3000 ms deadline while a large
download was running; DevLog completed at 3243 ms and fallback worked. The human-requested retry used
the same Story and timeout and succeeded in 2552 ms. This supports transient contention but does not
prove causality; the narrow timeout margin is separate Engineering-Skills follow-up.

The successful retry returned:

* 59 candidates, 49 selected, and 4449/6000 estimated tokens;
* 14 selected source files and 13 selected test files, all score 49;
* six content-bearing source files: five complete, one truncated;
* 21 selected source/test files skipped after the content limit;
* useful content for `EngineeringStoryContext` and its controller;
* low-value content for an analysis resolver and exception;
* selected but skipped content for `SelectedFileContentEnricher` and its test.

Several central contracts still required direct discovery. Disposable baseline observations remain
outside Git at `/tmp/devlog-story-0015-benchmark/`.

## Relevant Documentation

* `README.md`
* `backend/src/main/java/com/hopeful117/devlogai/collection/README.md`
* `docs/roadmap.md`
* ADR-037 — Repository-First Context Extraction
* ADR-038 — Repository Context Engine
* ADR-039 — Context Intelligence
* ADR-040 — Knowledge and Evidence Separation
* ADR-044 — Bounded Selected File Content Enrichment
* Story 0012, Story 0013, and Story 0014 workflow artifacts
* Engineering-Skills DevLog integration reference and context adapter

## Constraints

* Only globally selected source/test evidence may compete for content.
* Allocation must remain deterministic, bounded, path-level, revision-traceable, and explainable.
* Content must not be read before the allocator chooses a file.
* Alphabetical reference order may be only the final reproducibility tiebreaker.
* The policy must be generic and must not encode DevLog-specific filenames or packages.
* Existing score criteria and explanations may be reused, but a second opaque global ranker must not
  be introduced.
* The six-file, per-file, aggregate-character, file-size, read-duration, and total-token limits
  remain authoritative.
* Configuration and non-source/test evidence remain content-free.
* Path evidence must survive skips and failures.
* Final estimates, decisions, warnings, policy metadata, and digest must match returned evidence.
* GET/POST request compatibility, global selection precision, and multi-module diversity must remain
  intact.
* Tests must prove the outcome under equal scores and adversarial filename ordering, not only policy
  mechanics.
* The post-implementation benchmark must run before Code Review, remain outside Git, and cannot be
  replaced by unit tests or Quality Gate.
* Failure to demonstrate the concrete allocation correction prevents completion even if technical
  validation passes.
* Documentation Reconciliation and all Human Approval Gates remain mandatory.

## Risks

### Allocation may become a second global ranker

Duplicating the full multi-criteria ranking model would create conflicting authorities. The new
policy must remain narrowly responsible for scarce content slots and reuse transparent existing
signals where possible.

### Saturated explanations may still lack discrimination

Final score and capped semantic/guidance criteria are equal in the benchmark. The detailed matched
term explanation contains more information, but parsing presentation strings would be brittle.
Planning must identify a typed, deterministic signal or preserve uncapped allocation information at
the correct boundary.

### Tests may overfit DevLog filenames

A fixture naming the production enricher directly could pass without general improvement. The test
must encode generic Story/path relevance and prove stability under adversarial renaming/order.

### Allocation changes final tokens and digest

Different files have different lengths. Even with unchanged limits, allocation alters content,
truncation, used tokens, warnings, and digest, all of which require coherent final reconciliation.

### Benchmark success may be overstated

One repository and one Story validate the concrete regression only. They do not establish general
productivity, token savings, or cross-language relevance.

### Consumer timeout remains close to observed latency

The retry succeeded with 448 ms margin. This Story does not own Engineering-Skills timeout policy;
recurrent failures should be addressed separately without weakening fallback.

## Open Questions

None.

The human explicitly chose reorientation toward content allocation and required a factual
post-implementation benchmark. The exact typed allocation signal and policy representation can be
determined safely during Implementation Planning.

## Recommendation

Ready for planning

The defect is reproduced, ownership is clear, the correction can remain within the existing
selected-content phase, and no external data or architectural prerequisite is missing. The revised
Story includes the previously missing outcome-oriented regression and practical post-implementation
validation.

This recommendation is technical only. It does not approve the revised Repository Analysis or
authorize planning.

## Implementation Readiness

The repository contains every required input: selected evidence, typed score criteria and ranking
reasons, Story/context request, content policy, secure revision-pinned reader, final accounting,
digest assembly, and compatible API contracts.

No persistence migration, new external dependency, DevLog API redesign, or Engineering-Skills
change is required. Planning must select the smallest typed discriminating signal and preserve one
clear owner for final allocation reasons and accounting.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
