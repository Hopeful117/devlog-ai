# Engineering Report

## Story

Story 0014 — Bounded Selected File Content.

The Story adds bounded source and test excerpts to already selected Repository Context evidence,
without using full content for ranking and without exposing configuration content.

## Objective

Kiko previously received relevant file paths but still needed direct reads to understand even basic
implementation structure. The objective was to supply small, deterministic, revision-traceable
excerpts for selected source and test files while preserving safety, budgets, explainability, API
compatibility, and the repository as the final source of truth.

## Repository Analysis Summary

Repository Context already followed a collector, ranker, selector, budget, and digest pipeline.
Story 0013 supplied multi-module source, test, and configuration candidates, while synchronized Git
workspaces and scanner limits already established the relevant trust boundary. The analysis found
that enrichment belonged after path-level selection: reading every candidate would create circular
ranking, unnecessary I/O, and excessive exposure.

The affected components were Repository Context assembly, repository-structure provenance,
synchronized workspace access, evidence serialization, budget accounting, tests, and canonical
documentation. The primary risks were path escape, symlinks, binary or invalid input, stale or
incorrect revision use, configuration exposure, and untruthful final token/digest metadata.

## Implementation Plan Summary

The human-approved plan introduced a deterministic post-selection enrichment phase. It retained
path-only collection, ranking, and diverse selection; enriched only selected `SOURCE_FILE` and
`TEST_FILE` items from their recorded synchronized revision; applied file-count, per-file,
aggregate-character, file-size, deadline, and remaining-token bounds; and represented outcomes as
complete, truncated, skipped, or unavailable.

The plan explicitly excluded AI summaries, symbols, dependencies, secret redaction, configuration
content, persistence changes, and request-contract redesign.

## Implementation Summary

Repository Context now records the resolved revision on file candidates and invokes a selected-file
content enricher after normal ranking and selection. The enricher resolves an active project-owned
source, synchronizes the recorded revision, and delegates targeted reads to a confined UTF-8 reader.
The reader rejects traversal, symlinks, excluded paths, non-regular, oversized, binary, invalid, or
unavailable files and enforces a read deadline.

Evidence gains an additive optional content contract containing status, bounded text, reason,
policy identity/version, and revision. Final evidence estimates, selection-decision estimates,
`usedTokens`, warnings, and the SHA-256 digest are calculated from the enriched response.
Configuration and all non-source/test evidence remain content-free.

The implementation used a dedicated targeted reader rather than a second repository scan, kept
content policy separate from the public context-budget record, and added a project-scoped active
source query to avoid lazy entity traversal. These documented choices remain within the approved
plan.

## Modified Files

* `README.md` — documents bounded selected content, API representation, limits, and trust boundary.
* `backend/src/main/java/com/hopeful117/devlogai/collection/README.md` — documents targeted-reader
  safety behavior.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java` —
  inserts post-selection enrichment and final accounting/digest assembly.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidence.java` — adds
  optional content and enriched token estimation while preserving the previous constructor.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`
  — records the exact synchronized revision on file candidates.
* `backend/src/main/java/com/hopeful117/devlogai/source/repository/SourceRepository.java` — adds an
  active project-owned source lookup.
* `backend/src/main/resources/application.properties` — adds environment-backed content limits.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — verifies additive content serialization and request compatibility.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — verifies engine integration, final accounting, compatibility, and content-sensitive digest.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java`
  — verifies revision metadata and path-only candidates.
* `docs/roadmap.md` — records the implemented ADR-044 capability.

## Created Files

* `backend/src/main/java/com/hopeful117/devlogai/collection/collector/SecureRepositoryContentReader.java`
  — confined, deadline-bounded targeted text reader.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidenceContent.java` —
  immutable content status and provenance contract.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/RepositoryContentPolicy.java`
  — versioned enrichment limits.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricher.java`
  — selected-only enrichment and final budget reconciliation.
* `backend/src/test/java/com/hopeful117/devlogai/collection/collector/SecureRepositoryContentReaderTest.java`
  — security, encoding, size, availability, truncation, and timeout tests.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricherTest.java`
  — eligibility, limits, accounting, revision, and failure tests.
* `docs/decisions/ADR-044.md` — accepted bounded post-selection enrichment decision.
* Story workflow artifacts under `docs/stories/0014-bounded-selected-file-content/`.

## Architecture Impact

ADR-044 introduces one deterministic post-selection phase inside the existing Repository Context
Engine. Ranking and selection ownership remains unchanged, content is evidence rather than
validated knowledge, and no AI interpretation is introduced. The Engineering Story Context GET and
POST request contracts remain compatible; the response extension is additive. No database schema
or external dependency changed.

## Validation

Focused reader, enricher, Repository Context, structure, selection, and controller tests passed.
The final `./mvnw -q verify` run passed with 399 tests, zero failures, zero errors, zero skipped, and
the existing JaCoCo bundle rule.

Authenticated SonarQube analysis with Quality Gate wait passed with 86.6% new-code coverage, 0.0%
new-code duplication, and zero new violations. `git diff --check` passed.

The backend Docker image was rebuilt and a live Engineering Story Context POST returned six bounded
source excerpts with explicit complete/truncated/skipped states, revision provenance, no
configuration content, 4479 of 6000 estimated tokens, and a context digest. The persisted synchronized
revision intentionally differed from uncommitted local work, demonstrating revision traceability
rather than local-working-tree equivalence.

## Review Outcome

The Code Review verified all 16 acceptance criteria, architecture and security boundaries, tests,
documentation, and final validation. It reported no findings.

Technical recommendation: Ready for human approval.

Residual risks remain bounded and explicit: synchronized evidence can lag local uncommitted work;
lexical path ranking may allocate content to a less useful file; filesystem interruption is
cooperative; and source/test literals remain inside the local trust boundary rather than passing
through secret redaction.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

None.

The next real Engineering Story may measure whether bounded content reduces direct Kiko file reads,
but that experiment is not required to complete Story 0014.

## Lessons Learned

* Repository evidence can gain useful implementation detail without allowing content to influence
  which candidates are selected.
* Revision provenance is essential: synchronized context and a dirty local working tree are
  intentionally different sources with different roles.
* Enrichment budgets must reconcile final evidence, decisions, warnings, tokens, and digest as one
  contract rather than treating text as an unaccounted attachment.
* Explicit content states preserve useful path evidence when individual reads fail.

## Final Status

Completed
