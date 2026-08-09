# Implementation Report

## Overview

Story 0016 was implemented as a deterministic Java declaration-enrichment phase between global
evidence selection and selected-file content enrichment. It operates only on already selected Java
`SOURCE_FILE` and `TEST_FILE` evidence and does not change global ranking, diversity selection, or
the existing Engineering Story Context operations.

The implementation uses JavaParser Core in Java 21 syntax mode without symbol resolution. It
extracts bounded type, constructor, and method declarations with modifiers, annotations,
parameters, ownership, and source locations. Every eligible selected Java item receives an
explicit versioned symbol outcome, including bounded skip, unavailable, unsupported, and failure
states. The considered set is fixed deterministically before parsing by allocation rank, file
limit, symbol-metadata allocation, and remaining global context capacity. Symbol metadata
participates in evidence token estimates, selection decisions, warnings, and the context digest.

## Modified Files

* `backend/pom.xml` — adds the JavaParser Core dependency.
* `SecureRepositoryContentReader.java` — adds complete bounded reads and rejects oversized input
  instead of parsing truncated Java.
* `RepositoryContextEngine.java` — inserts symbol enrichment after selection and before content.
* `RepositoryEvidence.java` — adds optional symbol metadata, compatible constructors, immutable
  enrichment helpers, and combined token estimation.
* `application.properties` — defines the bounded symbol policy defaults.
* `SecureRepositoryContentReaderTest.java` — covers complete, oversized, and unavailable reads.
* `EngineeringStoryContextControllerWebMvcTest.java` — verifies additive symbol serialization.
* `RepositoryContextServiceTest.java` — verifies symbol metadata affects the digest and final model.
* `README.md` and `docs/roadmap.md` — document the new capability and its limits.

## New Files

* `RepositoryEvidenceSymbols.java` — immutable public symbol outcome and Java declaration model.
* `JavaDeclarationExtractor.java` — deterministic Java 21 syntax-only declaration extraction.
* `RepositorySymbolPolicy.java` — configured file, input, symbol, token, component, and duration
  bounds.
* `SelectedSymbolAllocationPolicy.java` — deterministic allocation of selected eligible evidence.
* `SelectedJavaSymbolEnricher.java` — safe synchronized-workspace lookup, bounded extraction,
  explicit outcomes, token reservation, warning propagation, and final accounting.
* Four focused test classes for the extractor, policy, allocator, and enricher.
* `docs/decisions/ADR-045.md` — records the bounded syntax-only symbol-context decision.

## Tests

New and updated tests cover:

* Java classes, interfaces, records, enums, annotation declarations, nested types, constructors,
  methods, modifiers, annotations, parameters, ownership, and source locations;
* malformed source, unsupported syntax, declaration/component limits, and deterministic ordering;
* complete-reader security, oversized input rejection, and workspace unavailability;
* selected-only eligibility, allocation ordering, file/symbol/token/time bounds, and explicit
  outcomes for every eligible item;
* deterministic considered-set construction and metadata reservation before declaration allocation;
* overloaded methods, unexpected extractor failure, per-file and aggregate duration limits,
  aggregate symbol exhaustion, token exhaustion, and metadata-budget exhaustion;
* additive API serialization, token accounting, selection decisions, warnings, and digest changes;
* compatibility of existing evidence constructors and content enrichment.

The final complete backend run executed 420 tests with zero failures, zero errors, and zero skipped
tests. JaCoCo covered 23,687 of 27,246 lines (86.94%) and its bundle rule passed.

## Validation

Command: focused symbol extractor, policy, enricher, Repository Context, reader, and controller
tests.  
Result: Passed.

Command: `./mvnw verify`.  
Result: Passed — 420 tests; failures 0; errors 0; skipped 0; JaCoCo rule passed.

Command: authenticated `./mvnw -q verify sonar:sonar` with Quality Gate wait.  
Result: Passed after scoped cleanup — Quality Gate `OK`; new-code coverage 85.4%; duplicated lines
0.0%; new violations 0; unresolved issues 0.

Command: `docker compose up -d --build backend`.  
Result: Passed — rebuilt backend served the Engineering Story Context API on port 18080.

Command: live Story 0016 request through the configured Engineering Story Context adapter.  
Result: Passed — 59 candidates, 45 selected items, 27 eligible Java items, six deterministically
considered items with explicit outcomes, 37 declarations extracted from four files, 5,974/6,000
tokens, and a context digest.

`git diff --check` passed.

## Benchmark Outcome

Disposable benchmark data remains under `/tmp/devlog-story-0016-benchmark` and is not part of the
Git working tree.

The first live run exposed an explainability defect: declaration payloads consumed the symbol
allocation before outcomes could be attached to later eligible files. Only 9 of 27 eligible Java
items had symbol metadata and the response emitted
`SYMBOL_ENRICHMENT_METADATA_BUDGET_EXHAUSTED`.

The first Code Review found that clearing all outcomes when their aggregate metadata could not fit
violated AC-5, and that required overload/failure/boundary tests were missing. The corrected run
forms its bounded considered set before inspection and reserves compact outcome metadata before
allocating declaration payloads. It:

* completed in 2.614353 seconds;
* attached an explicit symbol outcome to all six deterministically considered Java files;
* extracted 37 declarations from four files;
* marked two considered files with `SYMBOL_TOKEN_LIMIT`;
* remained inside the unchanged six-file, 1,500-symbol-token, and 6,000-global-token limits;
* removed the metadata-exhaustion warning;
* returned declaration names that matched direct reads of two synchronized source/test files.

The synchronized workspace represented committed revision
`15d3cc8f342f07e22a838576198bce433a5f60ab`, so the live run does not validate the uncommitted Story
0016 implementation through DevLog itself. It demonstrates bounded extraction and truthful
outcomes, not general productivity, semantic understanding, or token savings.

## Deviations

The approved plan allowed an implementation-detail choice between shared reads and a separate
bounded read path. The implementation adds `readComplete` to the existing secure reader and does
not introduce a cross-phase content cache.

The benchmark-driven metadata reservation was refined after Code Review so eligibility and
consideration are distinct: later selected Java evidence is not considered once the file or compact
metadata boundary is reached. Every considered file is now guaranteed an outcome. No configured
limit was increased.

SonarQube initially found an unused import, an eight-parameter helper, and an empty switch branch.
They were corrected without changing behavior; the final Quality Gate passed. The review cycle then
added five focused tests covering the previously missing mandatory paths.

No deviation changes persistence, API operations, global selection ownership, repository security,
or the DevLog/Kiko trust boundary.

## Documentation Reconciliation

Documentation update: Completed.

README, roadmap, and ADR-045 describe the additive symbol model, phase ordering, deterministic
bounds, syntax-only limitation, failure behavior, and continued need for direct repository
verification.

## Remaining Work

No implementation work remains for Story 0016. DevLog still does not resolve types, dependencies,
call graphs, method bodies, runtime behavior, or source-to-test relationships. Those remain direct
repository-analysis responsibilities and possible future Stories.

## Recommendation

Ready for Review
