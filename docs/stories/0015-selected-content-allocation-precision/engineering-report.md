# Engineering Report

## Story

Story 0015 — Selected Content Allocation Precision.

The Story improves how DevLog allocates bounded source/test content after global Repository Context
selection, so equal or saturated final scores no longer make alphabetical paths the effective
priority.

## Objective

The Story 0014 benchmark showed that all selected source/test evidence could receive final score 49.
The previous content phase then ordered ties by evidence reference, allowing secondary alphabetical
files to consume six scarce content slots while the central SelectedFileContentEnricher remained
path-only.

The objective was to introduce deterministic, story-specific, explainable allocation precision
without reading content before allocation, replacing the global ranker/selector, increasing limits,
or weakening the repository trust boundary.

## Repository Analysis Summary

Repository Context already separated candidate collection, deterministic ranking, diverse
selection, and post-selection content enrichment. The analysis established that global selection
had successfully retained the central file; the defect was local to the second allocation decision
inside SelectedFileContentEnricher.

DeterministicEvidenceRanker capped semantic and guidance criteria at 100, erasing useful precision
for many equally scored files. SelectedFileContentEnricher then sorted those ties by reference.
The affected contracts were score representation, selected-content allocation, optional content
metadata, final token accounting, context digest, API serialization, tests, and ADR-044.

Important constraints were selected-only reads, unchanged ranking/selection ownership, unchanged
security and revision controls, truthful total-token accounting, compatible response evolution, and
a mandatory live benchmark before review.

## Implementation Plan Summary

The human-approved plan retained uncapped typed semantic/guidance match strength alongside existing
capped scores and introduced a focused versioned SelectedContentAllocationPolicy.

Eligible selected files would be ordered by final score, semantic strength, guidance strength, and
reference only as the last deterministic tiebreaker. Allocation decisions would become additive
response metadata, final tokens and digest would include that metadata, and adversarial tests would
verify behavior under equal scores and scarce slots.

The plan excluded symbols, AST parsing, dependencies, content-based ranking, global-ranking
redesign, increased limits, persistence changes, and Engineering-Skills changes.

## Implementation Summary

EvidenceScore now carries immutable uncapped semantic and guidance match strength while preserving
its previous constructor and all existing capped criteria/final-score behavior.
SelectedContentAllocationPolicy applies the approved deterministic ordering and produces bounded
rank reasons. RepositoryEvidenceContent exposes policy identity/version, allocation rank, and
reasons for complete, truncated, skipped, or unavailable outcomes.

The live validation exposed two additional allocation facts. First, three long early files could
consume the remaining token budget before later allocated slots. Second, text could consume tokens
needed to explain later skipped items. SelectedFileContentEnricher now reserves bounded allocation
metadata for all eligible selected files and shares a constraining remaining content budget across
the available slots. No configured limit was raised.

The final benchmark returned content for the six highest allocated files, including
SelectedFileContentEnricher and its test, while every one of the 27 eligible selected items retained
an explicit outcome.

## Modified Files

* README.md — documents allocation precision, typed strength, token reservation, and lexical
  limitations.
* backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidence.java — includes
  content-allocation metadata in final evidence token estimation.
* backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidenceContent.java —
  adds compatible allocation policy, rank, and reasons.
* backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricher.java
  — applies allocation ordering, reserves explanation metadata, shares constrained content tokens,
  and reconciles final outcomes.
* backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/EvidenceScore.java —
  adds typed uncapped match strength with compatible defaults.
* backend/src/main/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRanker.java
  — calculates typed strength without changing global scoring authority.
* backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java
  — verifies additive allocation and score serialization.
* backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java —
  verifies allocation-sensitive digest behavior.
* backend/src/test/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricherTest.java
  — verifies adversarial ordering, scarcity, token sharing, accounting, and outcomes.
* backend/src/test/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRankerTest.java
  — verifies distinct uncapped strengths under equal capped scores.
* docs/decisions/ADR-044.md — records the refined deterministic allocation and budget policy.

## Created Files

* backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedContentAllocationPolicy.java
  — focused versioned selected-content ordering and reasons.
* backend/src/test/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedContentAllocationPolicyTest.java
  — deterministic ordering, rename/reorder, and final-tiebreak tests.
* Story workflow artifacts under
  docs/stories/0015-selected-content-allocation-precision/.

Disposable benchmark files remain outside Git under /tmp/devlog-story-0015-benchmark.

## Architecture Impact

The implementation adds a narrow deterministic policy within the ADR-044 post-selection phase.
DeterministicEvidenceRanker remains responsible for global evidence scores,
BudgetedDiverseEvidenceSelector remains responsible for global selection/diversity, and the new
policy controls only which already selected source/test items receive bounded text.

Response changes are additive and compatibility constructors remain available. No endpoint,
request, database schema, external dependency, AI interpretation, or security boundary changed.
Content remains revision-pinned repository evidence rather than validated knowledge, consistent
with ADR-038, ADR-039, ADR-040, and ADR-044.

## Validation

Focused ranker, allocator, enricher, Repository Context, digest, and controller tests passed.
The final ./mvnw -q verify run passed with 406 tests, zero failures, zero errors, zero skipped, and
the JaCoCo bundle rule. Line coverage was 3,971/4,799 (82.75%).

Authenticated SonarQube analysis passed its Quality Gate with 86.5% new-code coverage, 0.0%
new-code duplication, zero new violations, and zero unresolved issues. git diff --check passed.

The backend Docker image was rebuilt. The final normal Engineering Story Context request completed
in 2.10 seconds with:

* 59 candidates and 46 selected items;
* 27 eligible source/test allocation decisions;
* six content-bearing truncated excerpts;
* SelectedFileContentEnricher at rank 6 with 1,996 characters;
* SelectedFileContentEnricherTest at rank 5 with 1,804 characters;
* 5,971/6,000 tokens;
* explicit outcomes for all eligible items;
* a deterministic digest;
* no allocation-metadata exhaustion warning.

The benchmark demonstrates this concrete regression only. It does not prove general productivity,
semantic understanding, or token savings.

## Review Outcome

The Code Review verified all 14 acceptance criteria, plan compliance, architecture, API
compatibility, accounting, documentation, tests, and benchmark evidence.

It recorded one non-blocking observation: if global path-only selection ever consumes so much budget
that even bounded allocation metadata cannot fit, DevLog preserves path evidence and emits a
context-level degraded warning rather than weakening the total token limit.

Technical recommendation: Ready for human approval.

Residual risks remain explicit: allocation is lexical/path-based, all live source/test final scores
still saturated at 49, all six excerpts were truncated, and the single benchmark cannot establish
cross-repository value.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

None for Story 0015.

Potential future work—requiring separate Stories and evidence—includes symbols, targeted follow-up
reads, dependency/source-test relationships, and adaptive profile budgets. The human explicitly
confirmed that Story 0015 should not increase limits merely to compensate for missing analytical
capability.

## Lessons Learned

* Improving bounded context quality requires correcting allocation before increasing capacity.
* Preserving uncapped typed precision avoids losing useful relevance information when public
  criteria saturate.
* A meaningful allocation order is insufficient if early long reads can greedily consume later
  slots; token distribution is part of allocation correctness.
* Explainability metadata needs its own reservation when the complete context budget is
  authoritative.
* A live benchmark can reveal user-facing defects that unit tests and a green Quality Gate do not,
  but every discovered correction must be followed by renewed formal validation.
* Larger static contexts are not a substitute for the future selective Repository Analyst
  capability.

## Final Status

Completed
