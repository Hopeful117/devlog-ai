# Implementation Report

## Overview

Story 0015 was implemented as a deterministic, versioned allocation policy inside the existing
post-selection content-enrichment phase. Global evidence ranking and diverse selection remain
authoritative and unchanged. Only already selected SOURCE_FILE and TEST_FILE evidence competes for
the existing bounded content slots.

The ranker now preserves uncapped typed semantic and guidance match strength in addition to the
existing capped criteria and final score. The selected-content allocator orders eligible evidence
by final score, semantic strength, guidance strength, and reference as the final deterministic
tiebreaker. Every eligible content state exposes the allocation policy, version, rank, and bounded
reasons.

The live benchmark also exposed that greedy long-file reads could consume the token budget before
later allocated slots. The enrichment phase therefore reserves explanation metadata for all
eligible selected files and shares a constraining remaining content budget across the remaining
file slots. Existing file-count, per-file character, aggregate-character, security, revision, and
total-token limits were not increased.

## Modified Files

* README.md — documents the distinction between global selection and content allocation, typed
  strength, explanation metadata, token reservation, and remaining lexical limitations.
* RepositoryEvidence.java — includes bounded allocation metadata in final evidence token
  estimation.
* RepositoryEvidenceContent.java — adds compatible allocation policy, version, rank, and reasons.
* SelectedFileContentEnricher.java — consumes the allocation policy, preserves final response order,
  reserves explanation metadata, shares constrained content tokens, and reconciles final warnings
  and accounting.
* EvidenceScore.java — adds immutable typed semantic/guidance match strength with a compatible
  constructor and default.
* DeterministicEvidenceRanker.java — preserves uncapped term strength while retaining existing
  capped criteria, final scoring, and sorting.
* EngineeringStoryContextControllerWebMvcTest.java — verifies additive score and allocation
  serialization without changing GET/POST behavior.
* RepositoryContextServiceTest.java — verifies allocation metadata affects the context digest.
* SelectedFileContentEnricherTest.java — covers adversarial equal scores, scarce slots, token
  sharing, explicit outcomes, and existing bounds.
* DeterministicEvidenceRankerTest.java — verifies distinct uncapped strengths survive equal capped
  semantic scores.
* ADR-044 — records the refined allocation and token-reservation policy.

## New Files

* SelectedContentAllocationPolicy.java — focused versioned ordering and bounded allocation reasons
  for selected eligible evidence.
* SelectedContentAllocationPolicyTest.java — isolated ordering, renaming, input-order, and final
  reference-tiebreak regressions.
* Story 0015 workflow artifacts: story.md, repository-analysis.md, implementation-plan.md, and this
  implementation-report.md.

## Tests

New and updated tests cover:

* equal final scores with a stronger central match sorting after an alphabetical distractor;
* typed semantic and guidance strength as deterministic allocation precision;
* filename renaming and input-order independence when meaningful signals differ;
* reference order only when all meaningful signals tie;
* preservation of selected evidence response order;
* explicit complete, truncated, skipped, and unavailable allocation metadata;
* constrained token sharing across available content slots;
* truthful final token estimates, selected decisions, usedTokens, warnings, and digest;
* additive HTTP serialization and unchanged GET/POST requests;
* existing file-count, character, workspace, revision, configuration-exclusion, and failure
  boundaries.

The complete backend run executed 406 tests with zero failures, zero errors, and zero skipped tests.
JaCoCo covered 3,971 of 4,799 lines (82.75%) and its bundle rule passed.

## Validation

Command: focused ranker, allocator, enricher, Repository Context, and controller tests.
Result: Passed.

Command: ./mvnw -q verify
Result: Passed — 406 tests; failures 0; errors 0; skipped 0; JaCoCo rule passed.

Command: source ../.env and authenticated sonar:sonar with Quality Gate wait.
Result: Passed — Quality Gate OK; new-code coverage 86.5%; duplicated lines 0.0%; new violations 0;
unresolved issues 0.

Command: docker compose up -d --build backend
Result: Passed — backend image rebuilt and the service started on port 18080.

Command: normal Engineering Story Context adapter with the complete revised Story 0015.
Result: Passed — 59 candidates, 46 selected evidence items, 27 eligible source/test decisions,
six text-bearing items, 5,971/6,000 tokens, digest present, and no allocation-metadata exhaustion.

git diff --check passed.

## Benchmark Outcome

Disposable observations remain under /tmp/devlog-story-0015-benchmark and are not part of the Git
working tree.

The baseline returned six alphabetically allocated source files and skipped the central
SelectedFileContentEnricher. The final verified run:

* completed in 2.10 seconds;
* assigned explicit allocation metadata to all 27 eligible selected files;
* placed SelectedFileContentEnricher at rank 6 and returned 1,996 characters;
* placed SelectedFileContentEnricherTest at rank 5 and returned 1,804 characters;
* returned content for exactly six ranked files while marking every later item
  SKIPPED/ENRICHED_FILE_LIMIT;
* remained within the unchanged 6,000-token limit;
* removed the CONTENT_ALLOCATION_METADATA_BUDGET_EXHAUSTED warning.

This demonstrates the concrete Story regression only. It does not prove cross-repository
productivity, general semantic relevance, or token savings.

## Deviations

The first post-implementation benchmark proved that meaningful allocation order alone was
insufficient when three long early files greedily consumed the remaining token budget. The
implementation was refined within the approved allocation responsibility to share constrained
content tokens across remaining slots.

The next benchmark proved that content could still consume the budget needed to explain later
skips. Allocation metadata is now reserved before text allocation. This makes AC-4 and AC-10
truthful for every eligible selected item without increasing a limit or changing global ranking.
After each correction, focused tests, the complete suite, JaCoCo, SonarQube, Docker rebuild, and the
normal live request were repeated.

SonarQube initially reported one bounded-score expression and later two empty-string assertions.
All were corrected within Story scope; the final Quality Gate passed with zero unresolved issue.

No deviation changes API operations, persistence, global selector ownership, repository security,
or the DevLog/Kiko trust boundary.

## Documentation Reconciliation

Documentation update: Completed.

README and ADR-044 were updated because the implementation changes the public evidence metadata and
the documented post-selection allocation behavior. The roadmap was not changed: the capability
remains within the already documented bounded selected-file-content slice and does not alter the
project phase boundary.

## Remaining Work

No implementation work remains for Story 0015. Direct repository inspection is still required for
complete class behavior because allocation remains lexical and all six live excerpts were
truncated.

## Recommendation

Ready for Review
