# Engineering Report

## Story

Story 0016 — Bounded Java Symbol Context adds deterministic declaration-level Java context to
already selected source and test evidence returned by the Engineering Story Context API.

## Objective

The objective was to give Kiko useful classes, interfaces, records, enums, annotation declarations,
constructors, methods, annotations, parameters, and source locations without increasing the global
context budget or implying behavioral understanding. This addresses the limitation observed after
Story 0015, where relevant bounded file excerpts remained truncated and basic declaration discovery
still required broad direct inspection.

## Repository Analysis Summary

Repository Analysis identified the Repository Context Engine's post-selection boundary as the
correct ownership point. Existing global ranking, diversity selection, secure revision-pinned
content reading, content allocation, provenance, token accounting, and digest behavior had to
remain authoritative and compatible.

The affected backend components were the secure repository reader, immutable evidence contract,
Repository Context Engine, configuration, API serialization, and focused tests. The main risks were
fragile custom parsing, parsing truncated input, budget displacement, speculative semantic claims,
unbounded parser work, and ambiguous failure outcomes.

## Implementation Plan Summary

The human-approved plan selected JavaParser Core 3.27.0 in explicit Java 21 syntax mode without its
symbol solver. A deterministic symbol phase would execute after global evidence selection and
before content enrichment, inspect only bounded selected Java evidence, read complete files through
the existing confined synchronized-workspace boundary, and spend at most 1,500 tokens from the
existing 6,000-token context budget.

The approved scope excluded bodies, behavioral summaries, compilation, dependency resolution,
call graphs, cross-file linking, source/test inference, non-Java languages, persistence, automatic
project resolution, and Engineering-Skills changes.

## Implementation Summary

The implementation introduces a structured additive symbol model and a versioned deterministic
allocation/extraction phase. It extracts bounded declaration facts, records policy/extractor and
revision provenance, preserves source locations, and isolates malformed, unavailable, skipped, and
failed files without removing existing evidence.

Before parsing, the phase forms a deterministic considered set constrained by allocation rank, the
six-file limit, compact outcome metadata, the symbol budget, and remaining global capacity. Every
considered file is guaranteed an explicit outcome. Declaration payloads consume only the remaining
reserved symbol allocation, after which existing content enrichment uses the remaining context
budget.

The first live benchmark and first Code Review exposed two defects: metadata exhaustion could
remove required outcomes, and mandatory boundary/failure tests were incomplete. Implementation was
reopened. The considered-set contract was corrected, overload/failure/time/symbol/token/metadata
tests were added, and a compact real outcome was prevented from being replaced by a larger reserved
fallback. A second full review verified the corrections.

## Modified Files

* `README.md` — documents API symbol metadata, deterministic consideration, limits, and syntax-only
  trust boundary.
* `backend/pom.xml` — adds JavaParser Core 3.27.0.
* `SecureRepositoryContentReader.java` — adds confined complete-file reads that reject oversized
  parser input instead of returning a truncated prefix.
* `RepositoryContextEngine.java` — executes symbol enrichment after selection and before content.
* `RepositoryEvidence.java` — adds optional symbols, compatible constructors, immutable enrichment,
  and combined token estimation.
* `application.properties` — defines the independent symbol file/input/count/string/token/duration
  defaults.
* `SecureRepositoryContentReaderTest.java` — verifies complete and oversized reads.
* `EngineeringStoryContextControllerWebMvcTest.java` — verifies additive symbol serialization and
  unchanged endpoint behavior.
* `RepositoryContextServiceTest.java` — verifies engine compatibility and digest participation.
* `docs/roadmap.md` — records bounded Java declaration context and deferred semantic capabilities.

## Created Files

* `RepositoryEvidenceSymbols.java` — immutable symbol outcome, declaration, parameter, and source
  location contract.
* `JavaDeclarationExtractor.java` — deterministic Java 21 syntax-only declaration extractor.
* `RepositorySymbolPolicy.java` — validated versioned extraction limits.
* `SelectedSymbolAllocationPolicy.java` — deterministic rank/strength/reference allocation.
* `SelectedJavaSymbolEnricher.java` — bounded considered-set construction, secure extraction,
  outcomes, warnings, accounting, and failure isolation.
* `JavaDeclarationExtractorTest.java` — representative Java declaration, overload, malformed,
  no-symbol, ordering, and truncation coverage.
* `RepositorySymbolPolicyTest.java` — default and invalid-bound coverage.
* `SelectedSymbolAllocationPolicyTest.java` — deterministic allocation coverage.
* `SelectedJavaSymbolEnricherTest.java` — selected-only, provenance, failure, duration, symbol,
  token, metadata, and explicit-outcome coverage.
* `docs/decisions/ADR-045.md` — architecture decision for bounded selected Java symbol enrichment.
* Story 0016 lifecycle artifacts.

## Architecture Impact

ADR-045 introduces one new deterministic Repository Context enrichment responsibility and one
maintained syntax-parser dependency. Global ranking and selection remain unchanged; symbols cannot
retroactively influence selected candidates. Evidence remains separate from validated knowledge in
accordance with ADR-040. The API change is additive, persistence is unchanged, and repository
ownership, synchronized-revision, filesystem security, Kiko reasoning, and repository source-of-
truth boundaries are preserved.

## Validation

Focused reader, extractor, policy, allocator, enricher, Repository Context, and controller tests
passed.

Final `./mvnw -q verify` result:

* 420 tests;
* 0 failures;
* 0 errors;
* 0 skipped;
* JaCoCo rule passed;
* 23,687 of 27,244 lines covered (86.94%).

Authenticated SonarQube analysis completed with:

* Quality Gate `OK`;
* new-code coverage 85.4%;
* new duplicated lines 0.0%;
* new violations 0;
* unresolved issues 0.

The final backend Docker image built and started successfully. The final live Engineering Story
Context request completed in 2.614353 seconds with 59 candidates, 45 selected evidence items, six
explicit considered Java outcomes, 37 declarations from four files, 5,974/6,000 tokens, and a
context digest. Disposable observations remain outside Git under `/tmp`.

The live synchronized workspace represented the latest committed revision rather than uncommitted
Story 0016 code. Direct source checks confirmed returned declarations for synchronized files; this
validation does not claim behavioral or cross-repository productivity improvements.

## Review Outcome

The first Code Review returned two Major findings to implementation: metadata exhaustion violated
the explicit-outcome contract, and required boundary/failure tests were missing. Both were
corrected and the complete validation cycle was repeated.

The second Code Review found no remaining Blocker, Major, Minor, or Observation finding.

Technical recommendation: Ready for human approval.

Human Code Review approval: granted.

Residual risks remain bounded and documented: parser cancellation is best effort; declarations do
not prove behavior, dependencies, calls, or source/test relationships; later evidence outside the
considered set remains symbol-free; and synchronized evidence can differ from a working tree.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

None for Story 0016.

Behavioral understanding, type/dependency resolution, call graphs, source/test relationships,
non-Java symbols, and autonomous Repository Analysis remain separate future capabilities rather
than unfinished Story scope.

## Lessons Learned

* Information density improved more effectively through bounded declarations than by increasing raw
  content limits.
* Eligibility and consideration must remain distinct when explainability metadata itself consumes a
  strict global budget.
* Outcome metadata must be reserved before expensive payload allocation, and a real compact outcome
  must be allowed to reduce the reservation.
* Live benchmarks are valuable for exposing budget interactions that isolated happy-path tests can
  miss, but mandatory boundary tests must encode those lessons before completion.
* A review finding should reopen implementation and invalidate the old review rather than being
  accepted as documentation-only debt.

## Final Status

Completed
