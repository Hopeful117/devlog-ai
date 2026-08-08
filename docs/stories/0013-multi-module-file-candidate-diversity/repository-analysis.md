# Repository Analysis

## Story Understanding

Story 0013 is reoriented around the critical prerequisite discovered by its real DevLog-assisted
benchmark: Repository Context cannot rank or enrich production files that
`RepositoryStructureCollector` never emits as candidates.

The requested behavior is to recognize supported source, test, and configuration paths at module
boundaries and to bound file evidence through deterministic category-aware candidate allocation.
The collector should supply a representative input; Story 0012 ranking and selection remain
authoritative for final relevance, concentration, diversity, and budgets.

The engineering objective is to ensure a multi-module repository can contribute relevant
`SOURCE_FILE`, `TEST_FILE`, and `CONFIG_FILE` evidence before deeper capabilities such as bounded
content are introduced.

In scope are path classification, category-aware candidate capacity, within-category Story-path
prioritization, provenance, diagnostics, tests, quality validation, and documentation
reconciliation. Explicit exclusions include content reads, symbols, dependencies, embeddings, new
knowledge/history collectors, ingestion redesign, agent behavior, persistence, project resolution,
frontend features, and workflow changes.

## Repository Summary

The relevant implementation is confined to the Java backend's Repository Structure collection and
its composition with the existing Repository Context pipeline.

`RepositoryStructureCollector` synchronizes the active project source, scans it through
`SecureRepositoryScanner`, and creates:

* aggregate module, source-directory, test-directory, configuration, and extension evidence;
* module evidence;
* individual `SOURCE_FILE`, `TEST_FILE`, and `CONFIG_FILE` evidence.

The individual evidence is collected into one list, sorted by the number of raw Story terms found
in each path, tie-broken by path, and truncated to the first 40 items. Classification happens before
that shared limit.

Production source detection currently requires a path to start with one of:

* `src/main/java`;
* `src/main/kotlin`;
* `src/main/python`;
* `src/main/typescript`;
* `src/app`;
* `src/lib`.

Test detection uses containment for `src/test/`, `__tests__/`, `test/`, and `tests/`. Therefore
`backend/src/main/java/...` is not a source, while `backend/src/test/java/...` is a test. Individual
configuration matching is based on the filename and supports module paths, but configurations lose
the shared top-40 competition when tests receive stronger Story-term matches.

The Story 0013 benchmark confirmed this exact behavior: 40 test candidates, zero source candidates,
and zero individual configuration candidates. Story 0012 then correctly limited the selected tests
to 15 and reported the other 25 as category concentration exclusions.

No database, AI Engine, frontend, or external API change is required. The response already exposes
candidate/selected distributions, selection reasons, and provenance needed to validate the fix.

## Affected Modules

### Backend — Repository Structure Collector

Package:

`com.hopeful117.devlogai.repositorycontext.collector`

Relevant components:

* `RepositoryStructureCollector` owns supported path classification, file-level candidate creation,
  Story-path ordering, the 40-item limit, and structure collector metadata;
* `EvidenceFactory` bounds summaries, creates provenance, and estimates candidate tokens;
* `RepositoryContextCollector` is the stable extension contract.

This is the primary ownership boundary. The source-root helpers assume repository-root layouts,
whereas module and test helpers already operate across deeper paths. The shared candidate list and
limit permit one kind to eliminate others before final ranking.

### Backend — Collection scanner and workspace

Packages:

* `com.hopeful117.devlogai.collection.collector`
* `com.hopeful117.devlogai.collection.workspace`

`SecureRepositoryScanner`, `CollectorLimits`, `WorkspaceManager`, and `SynchronizedWorkspace`
provide safe deterministic discovery. They already return normalized relative paths for all
non-excluded regular files and are not responsible for Repository Context file classification or
candidate category allocation.

No scanner or synchronization change is required. Their current exclusions, symlink handling,
limits, and graceful warnings must remain unchanged.

### Backend — Context Intelligence

Package:

`com.hopeful117.devlogai.repositorycontext.intelligence`

`ContextProfileDefinition`, `ContextPlan`, and `DeterministicContextIntelligence` already carry the
Story-0012 precision policy. ADR-039 allows Context Profiles to own internal evidence and token
allocation strategies, but Context Intelligence must not perform collection.

Planning may place reusable candidate allocation values in a small profile policy if Engineering
Story contexts need different file-category capacity. A fixed repository-wide collector policy is
also compatible if it is demonstrably profile independent. No new ADR is needed for either bounded
implementation because the collector remains responsible for candidate production under ADR-038.

### Backend — Ranking and selection

Packages:

* `com.hopeful117.devlogai.repositorycontext.ranking`
* `com.hopeful117.devlogai.repositorycontext.selection`

`DeterministicEvidenceRanker` and `BudgetedDiverseEvidenceSelector` consume the collector output.
Story 0012 owns corpus-aware scoring, minimum relevance, evidence-kind concentration, strong
overflow, diversity, deduplication, and item/token budgets.

These components should not need production changes. Regression tests must prove that improved
candidate diversity composes with them rather than moving final selection into the collector.

### Backend — Repository Context assembly and API

Packages:

* `com.hopeful117.devlogai.repositorycontext`
* `com.hopeful117.devlogai.projectcontext`

`RepositoryContextEngine` already calculates candidate and selected distributions by kind/layer,
preferred-layer availability, decisions, warnings, and digest. `EngineeringStoryContextController`
exposes this result through compatible GET and POST operations.

The model and controller do not require new fields. Service/API tests should verify changed counts
and preserved serialization rather than add a new response contract.

### Backend — Tests and quality

Relevant tests:

* `RepositoryStructureCollectorTest` covers aggregate evidence, root-level source/test/config files,
  module evidence, a global 40-source limit, Story-term ordering, empty source, and unavailable
  workspace;
* `SecureRepositoryScannerTest` covers traversal, content inclusion/exclusion, symlinks, excluded
  directories, limits, encoding, and warnings;
* `DeterministicEvidenceRankerTest` and `BudgetedDiverseEvidenceSelectorTest` cover Story 0012
  ranking and concentration;
* `RepositoryContextServiceTest` covers candidate/selected distributions, budgets, decisions,
  provenance, and digest;
* `EngineeringStoryContextControllerWebMvcTest` protects response serialization.

The missing regression seam is a multi-module fixture with mixed file kinds above the shared
candidate limit. Current source fixtures start directly with `src/main/...`, masking the production
layout defect.

## Existing Implementation

Existing behavior:

* the secure scanner supplies deterministic normalized paths and excludes unsafe/generated trees;
* aggregate configuration evidence can list module-level files;
* individual config classification uses the final filename and can recognize module paths;
* root-level production sources and module-level tests produce file evidence;
* Story terms prioritize file paths before the current collector cap;
* every file candidate retains stable reference, originating path, repository source, collector
  metadata, and token estimate;
* Story 0012 exposes the exact candidate imbalance and limits final repeated-kind selection.

Missing behavior:

* module-prefixed production roots are not recognized;
* source-root matching is not path-segment relative to a module boundary;
* candidate capacity is not divided or fairly shared between source, test, and configuration kinds;
* unused per-category capacity has no deterministic redistribution rule because categories do not
  yet have capacity;
* tests do not cover mixed multi-module candidates above the total bound;
* no regression demonstrates that source/test/config evidence all reaches the ranker together.

Behavior that must remain unchanged:

* scanning, workspace synchronization, and filesystem safety;
* aggregate structure evidence;
* existing evidence kinds, references, and provenance;
* Story 0012 ranking and selector ownership;
* Repository Context item/token budgets and diagnostics;
* GET/POST Engineering Story Context input contracts;
* no source or configuration content reads.

## Relevant Documentation

* `README.md`
* `docs/decisions/ADR-037.md` — Repository-First Context Extraction
* `docs/decisions/ADR-038.md` — Repository Context Engine
* `docs/decisions/ADR-039.md` — Context Intelligence
* `docs/decisions/ADR-040.md` — Knowledge and Evidence Separation
* Story 0012 and its Engineering Report
* the current `engineering-story` skill and Repository Analysis prompt
* the DevLog Context integration reference
* the disposable Story 0013 benchmark under `/tmp/devlog-story-0013-benchmark/`

## Constraints

* ADR-037 prefers deterministic repository-derived context and bounded evidence.
* ADR-038 assigns candidate discovery to collectors and final ranking/selection to separate stages.
* ADR-039 permits predefined versioned profile strategy but prohibits Context Intelligence from
  becoming a collector.
* ADR-040 keeps paths and structure as transient Repository Evidence rather than Trusted Knowledge.
* Path matching must use normalized segment boundaries, not unrestricted substrings.
* Candidate allocation must preserve an explicit total bound and deterministic ordering.
* Candidate diversity must not be represented as final evidence selection.
* Existing ranker and selector policies remain authoritative and unchanged unless a concrete
  compatibility defect is proven during planning.
* Root-level repositories and all currently supported file extensions remain compatible.
* Scanner exclusions, symlink rejection, workspace confinement, limits, and failure behavior remain
  unchanged.
* Evidence kinds, references, provenance, and API request contracts remain backward compatible.
* Correctly changed candidates may change distributions, selections, and digests.
* No content reads, secrets boundary, persistence, or database migration is introduced.
* Documentation impact must be reconciled before Code Review under the current workflow.

## Risks

### Candidate allocation can duplicate final selector policy

The collector needs representation before ranking, while the selector owns final concentration.
Without a clear boundary, two similar policies could diverge. Candidate allocation must only ensure
a bounded representative input and must not use final relevance scores or final-selection reasons.

### Static category quotas can waste useful capacity

Some repositories contain few or no tests/configurations. A rigid split could reduce useful source
candidates below the existing 40-item capacity. Planning must define deterministic redistribution
of unused capacity while preserving representation when multiple categories exist.

### Broader source-root matching can create false positives

Using arbitrary substring matching would repeat the test helper's permissiveness and could classify
unrelated paths. Segment-aware positive and negative fixtures are required.

### Story-term prioritization can still favor generic paths

Collector-local matching is simpler than Story 0012 corpus-aware ranking. Its responsibility should
remain candidate retention within each category; the ranker must remain the only source of final
multi-criteria relevance.

### Correct output will update existing context assertions

More source/config candidates may change counts and selection order. Tests must update obsolete
expectations without weakening budget, determinism, provenance, or API guarantees.

## Open Questions

None.

The exact allocation representation and redistribution formula are bounded implementation-design
choices for the Implementation Plan. They do not require product clarification because the Story
defines the required invariants and ownership boundary.

## Recommendation

Ready for planning

The reoriented Story addresses the measured prerequisite directly. Repository ownership is clear,
the existing architecture supports the change without a new pipeline stage or ADR, the defect is
reproduced by current implementation and benchmark evidence, and no blocking contract or external
dependency is missing.

This recommendation is technical only. It does not approve the Repository Analysis or authorize
Implementation Planning.

## Implementation Readiness

The current repository contains all required technical prerequisites:

* secure normalized repository scanning;
* source/test/config classification helpers;
* Story objective access in `ContextRequest`;
* immutable evidence/provenance construction;
* Context Profile and policy composition if required;
* ranker/selector precision and diagnostics;
* focused collector, engine, and API test seams;
* Maven, JaCoCo, and authenticated SonarQube quality infrastructure.

No missing data, persistence, external service, new ADR, or API redesign blocks planning.

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
