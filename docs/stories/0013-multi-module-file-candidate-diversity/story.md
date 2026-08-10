# Story 0013 — Multi-Module File Candidate Diversity

## Story ID
0013

## Title
Produce balanced source, test, and configuration candidates for multi-module repositories

## Status
Completed

## Priority
Critical

## Date
2026-08-08

---

## User Story

As Kiko preparing an Engineering Story,
I want DevLog to discover relevant source, test, and configuration files across repository modules,
So that Context Intelligence can rank a representative candidate set instead of receiving only one
file category.

---

## Context

Stories 0001–0009 integrated DevLog into Kiko's Repository Analysis workflow as an optional,
deterministic context provider. Story 0012 then addressed the first real benchmark's excessive
selection noise through:

* corpus-aware Story-term ranking;
* minimum relevance;
* evidence-kind concentration limits;
* strong-relevance overflow;
* candidate and selected distributions;
* explicit preferred-layer and exclusion diagnostics.

Story 0013 initially targeted bounded content enrichment for selected source and test evidence. Its
mandatory real benchmark exposed a more fundamental prerequisite.

For the complete Story 0013 request, DevLog returned:

* 58 total candidates;
* 40 `TEST_FILE` candidates;
* zero `SOURCE_FILE` candidates;
* zero individual `CONFIG_FILE` candidates;
* 15 selected `TEST_FILE` items after Story 0012 concentration control;
* 25 explicit `CATEGORY_CONCENTRATION_LIMIT` decisions.

Story 0012 therefore worked as designed at selection time: repeated test evidence no longer formed
a majority and its exclusions were explainable. However, it could not select source or individual
configuration evidence that the collector never produced.

The cause is in `RepositoryStructureCollector`, before ranking and selection:

* source roots are recognized only when the complete path begins with values such as
  `src/main/java`;
* test roots are recognized through substring matching such as `src/test/`;
* DevLog is multi-module, so production paths begin with `backend/`, `frontend/`, or `ai-engine/`;
* all file kinds share one `MAX_FILE_EVIDENCE_ITEMS = 40` limit after local Story-term ordering.

Consequently, `backend/src/main/java/...` is not classified as source evidence while
`backend/src/test/java/...` is classified as test evidence. The global pre-ranking cap can then be
entirely consumed by tests.

This is a critical evidence-discovery defect. Adding source content, symbols, dependencies, or a
Repository Analyst agent before correcting candidate generation would deepen a pipeline that still
cannot reliably surface production files in a common multi-module layout.

Story 0013 is therefore reoriented to correct file-candidate discovery and diversity. Bounded
post-selection content enrichment is deferred to Story 0014 or a later Story after this prerequisite
is validated.

---

## Objective

Make `RepositoryStructureCollector` produce a deterministic, representative, and bounded set of
file candidates across multi-module repository layouts by:

* recognizing supported source and test roots at module boundaries;
* preserving correct source/test/configuration classification;
* preventing one file kind from consuming the entire collector-level candidate allowance;
* retaining Story-relevant candidates within every eligible kind;
* exposing enough candidate diversity for the existing ranker and selector to perform their own
  responsibilities;
* preserving scanner safety, collector bounds, evidence provenance, deterministic ordering, and
  current Repository Context budgets.

The solution must be generic across supported repository layouts. It must not special-case the
DevLog module names, Story 0013 vocabulary, or a fixed observed count of 40 tests.

---

## Acceptance Criteria

### AC-1: Module-prefixed source roots are recognized

Supported production source roots must be detected at a valid repository or module path boundary.

At minimum, tests must cover existing supported roots in layouts equivalent to:

* `src/main/java/...`;
* `backend/src/main/java/...`;
* `module-a/src/main/kotlin/...`;
* `service/src/main/python/...`;
* supported TypeScript/JavaScript source layouts already represented by the collector.

Matching must be path-segment aware. A directory whose name merely contains a source-root string
must not be misclassified.

### AC-2: Module-prefixed test roots remain correctly classified

Test files under repository-root and module-prefixed test layouts must remain `TEST_FILE` evidence.

Classification must:

* distinguish tests from production sources deterministically;
* define unambiguous precedence when a filename contains words such as `Test` but resides in a
  production root;
* avoid relying on a filename suffix alone;
* retain supported Java, Kotlin, Python, TypeScript, and JavaScript test layouts where currently
  applicable.

### AC-3: Individual configuration candidates are retained

Supported safe configuration files must continue to produce individual `CONFIG_FILE` candidates in
repository-root and module-prefixed locations.

The collector must not allow source or test volume to eliminate every eligible configuration
candidate before ranking. This Story concerns paths and metadata only; it does not authorize reading
or exposing configuration contents.

### AC-4: Collector-level candidate capacity is category aware

One file evidence kind must not consume the collector's complete candidate allowance when eligible
candidates of other supported kinds exist.

The policy must:

* define deterministic capacity for source, test, and configuration candidates or a reusable
  equivalent grouping;
* retain the highest-priority candidates within each category;
* use stable tie-breaking;
* remain inside an explicit total collector bound;
* degrade gracefully when only one category exists by allowing useful capacity rather than leaving
  it artificially empty;
* avoid a hard-coded exception that recognizes only `TEST_FILE` or this repository.

Repository Analysis and Implementation Planning must determine whether the policy belongs directly
to the collector, a versioned Context Profile policy, or a small reusable candidate-allocation
abstraction.

### AC-5: Story relevance is preserved without duplicating final ranking

Candidate bounding may prioritize paths using the Story objective, but it must not become an
independent replacement for `DeterministicEvidenceRanker`.

The solution must:

* retain clearly Story-relevant paths within each eligible category;
* use deterministic normalization and matching;
* avoid project-specific stopwords or module names;
* preserve stable alphabetical or equivalent deterministic tie-breaking;
* leave multi-criteria scoring, cross-layer ranking, and final selection to the existing ranker and
  selector.

### AC-6: Evidence provenance and contracts remain intact

Every produced file candidate must retain:

* the existing `RELATED_SOURCE_CODE` layer;
* the correct `SOURCE_FILE`, `TEST_FILE`, or `CONFIG_FILE` kind;
* stable reference format;
* repository source location;
* originating relative file path;
* deterministic collector identifier and version;
* estimated-token and later ranking metadata behavior.

No content field, persistence entity, or new trusted-knowledge representation is required.

### AC-7: Existing ranking and selection policy remains authoritative

Story 0012 behavior must remain intact:

* generic terms are handled by `multi-criteria-v2`;
* minimum relevance remains active;
* evidence-kind concentration and strong-relevance overflow remain selector responsibilities;
* candidate and selected distributions remain accurate;
* selection decisions continue to explain relevance, concentration, item budget, token budget, and
  duplicate handling.

The collector must not preselect the final Repository Context or claim that candidate allocation is
final selection.

### AC-8: Scanner and workspace safety remains unchanged

The correction must continue using the existing secure repository scan and synchronized workspace.

It must preserve:

* excluded-directory handling;
* symlink rejection;
* workspace-root confinement;
* maximum files/directories and timeout behavior;
* deterministic traversal;
* graceful empty-source and unavailable-workspace behavior.

This Story does not authorize new file-content reads.

### AC-9: Empty, sparse, and single-category repositories degrade gracefully

The collector must return a valid deterministic result when:

* no active repository source exists;
* the workspace is unavailable;
* the scan is empty;
* only sources exist;
* only tests exist;
* only configurations exist;
* one category has fewer candidates than its nominal allocation;
* supported files exceed every category and total bound.

Unused capacity may be redistributed only through a deterministic documented rule.

### AC-10: Multi-module regression fixture represents the observed defect

Add a deterministic fixture shaped like the real benchmark, without hard-coding DevLog names:

* multiple module-prefixed production sources;
* more tests than the previous global file limit;
* repository-root and module configuration files;
* Story terms matching candidates across source and test categories;
* unrelated files and excluded/generated paths.

The result must demonstrate:

* at least one relevant production source candidate;
* at least one relevant test candidate;
* at least one supported configuration candidate;
* no category monopolizes candidate production while other eligible categories exist;
* deterministic ordering and identical repeated output;
* correct diagnostic candidate counts after full Repository Context assembly.

### AC-11: Existing repository layouts remain compatible

Current single-module fixtures and supported root-level paths must continue to work. Aggregate
module, source-directory, test-directory, configuration-file, extension, and module evidence must
remain available.

If collector or candidate policy versions change, the change must be explicit in extraction or
Context Intelligence explanations and covered by compatibility tests.

### AC-12: API and digest behavior remains compatible

The Engineering Story Context GET and POST request contracts must not change. Candidate diversity
may legitimately change selected evidence and the context digest, but:

* serialization shape must remain compatible;
* distributions must match actual candidates and selections;
* warnings must remain truthful;
* identical complete inputs and timestamps used by tests must produce deterministic results.

This Story must not expand into the existing request-time digest timestamp issue unless planning
shows the candidate correction cannot be tested safely without addressing it.

### AC-13: Tests and quality baseline remain healthy

The implementation must run:

* focused Repository Structure Collector, ranker, selector, Repository Context service, and
  Engineering Story Context API tests;
* the complete backend test suite;
* JaCoCo verification with the existing 80% bundle line-coverage rule;
* authenticated SonarQube analysis with the pinned scanner and Quality Gate wait.

Completion requires no new unresolved Sonar issue and a passing Quality Gate. No existing test may
be removed or weakened to accommodate changed candidate distribution.

### AC-14: The prerequisite for bounded content is demonstrably available

After implementation, a Story-shaped Engineering Story Context test must demonstrate selected or at
least rankable eligible `SOURCE_FILE`, `TEST_FILE`, and `CONFIG_FILE` evidence from a multi-module
fixture.

This criterion prepares but does not implement bounded content enrichment. No source or
configuration content may be returned by Story 0013.

---

## Scope

### In Scope

* Path-segment-aware production-source and test-root classification.
* Multi-module and single-module file discovery.
* Deterministic source/test/configuration candidate allocation under a total collector bound.
* Story-path prioritization within candidate categories.
* Compatible evidence kinds, references, provenance, and token estimates.
* Repository Structure Collector and focused allocation abstraction changes where justified.
* Context Profile/plan changes only if candidate allocation is profile-owned.
* Candidate/selected diagnostic and API regression tests.
* Complete Maven, JaCoCo, and SonarQube validation.
* Documentation reconciliation for any changed architecture, configuration, or public behavior.

### Out of Scope

* Reading or returning source, test, or configuration contents.
* Post-selection content enrichment.
* AI summaries or interpretations.
* AST, class, method, interface, annotation, or symbol extraction.
* Dependency, import, call-graph, ownership, or source-test relationship analysis.
* Embeddings or vector search.
* New Git, ADR, roadmap, or documentation collectors.
* Repository ingestion or synchronization redesign.
* Automatic DevLog project resolution.
* A DevLog Repository Analyst agent.
* Kiko, Engineering-Skills, `delegate-task`, workflow-gate, or Human Approval changes.
* Frontend changes unless a compatibility regression requires a minimal correction; no new UI is
  required.
* Database migrations or persistence.
* Fixing request-time digest variability as unrelated work.

---

## Impacted Components

Repository Analysis must confirm the exact design and file set. Expected components are:

* `RepositoryStructureCollector` and `RepositoryStructureCollectorTest`;
* a possible immutable file-candidate allocation policy or helper if it avoids collector-specific
  branching;
* `ContextProfileDefinition`, `ContextPlan`, and `DeterministicContextIntelligence` only if the
  allocation policy belongs to the active profile;
* `RepositoryContextServiceTest` for candidate/selected distributions and Story 0012 composition;
* `EngineeringStoryContextControllerWebMvcTest` only to protect unchanged response shape and
  diagnostic serialization;
* README or architecture documentation if Documentation Reconciliation finds the capability is
  part of the canonical documented contract.

No changes are expected in `SecureRepositoryScanner`, persistence, the AI Engine, the frontend,
Git-history collection, commit-diff collection, or project-knowledge collection.

---

## Architectural Ownership and Boundaries

* `SecureRepositoryScanner` owns safe deterministic filesystem discovery.
* `RepositoryStructureCollector` owns classification and production of structure/file candidates.
* Context Intelligence may own reusable profile-specific candidate policy, but it must not perform
  collection.
* The ranker owns multi-criteria relevance scores across all candidates.
* The selector owns cross-candidate relevance, diversity, concentration, deduplication, and global
  budgets.
* `RepositoryContext` owns the immutable selected result and diagnostics.
* DevLog supplies evidence; Kiko reasons and verifies the current repository.
* `engineering-story` owns workflow orchestration and fallback.
* The human remains approval authority.

This Story refines collector candidate production under ADR-038 and Context Profile strategy under
ADR-039. It does not add a new Repository Context pipeline stage and is not expected to require a
new ADR unless Repository Analysis discovers a broader architectural decision.

---

## Tests and Validation

At minimum, tests must cover:

* repository-root and module-prefixed source roots;
* repository-root and module-prefixed test roots;
* production files whose names contain `Test`;
* configuration files at root and module depth;
* mixed categories above the total candidate bound;
* Story-relevant and unrelated paths inside every category;
* deterministic unused-capacity redistribution;
* only-source, only-test, only-config, empty, and unavailable-workspace cases;
* preserved excluded/generated directory behavior;
* stable reference/provenance/ordering;
* candidate and selected distribution reconciliation;
* Story 0012 concentration behavior over the improved candidate set;
* unchanged GET and POST Engineering Story Context inputs;
* complete backend tests, JaCoCo, and authenticated SonarQube Quality Gate.

---

## Risks

### Candidate allocation duplicates selector policy

Collector allocation and final concentration operate at different stages. If their boundaries are
unclear, two competing policies may become difficult to explain. Mitigation: collector allocation
only guarantees a bounded representative input; the selector remains authoritative for final
choice.

### Fixed per-kind quotas waste capacity

Repositories may legitimately contain only one supported category. Mitigation: define deterministic
redistribution of unused capacity while preserving a total bound and representation where multiple
categories exist.

### Broad path matching creates false source roots

Changing prefix matching to unrestricted substring matching could classify unrelated directory
names. Mitigation: match normalized path segments and test near-match negative cases.

### Story-term matching remains simplistic

Collector-local lexical prioritization may still favor generic path vocabulary. Mitigation: keep it
bounded to within-category candidate retention, use deterministic normalization, and leave final
corpus-aware relevance to Story 0012 ranking.

### Candidate changes alter context digests and tests

Correctly adding source/config candidates changes distributions, selections, and digests.
Mitigation: assert semantic contracts rather than preserving obsolete candidate counts, while
keeping deterministic ordering and serialization stable.

### More representative candidates increase ranking work

Per-category capacity can expose more candidates than the previous single 40-item list. Mitigation:
preserve a validated total collector bound and include performance-shaped regression fixtures.

---

## Definition of Done

* [ ] Every acceptance criterion is satisfied.
* [ ] Supported module-prefixed production sources produce `SOURCE_FILE` evidence.
* [ ] Supported tests remain correctly classified as `TEST_FILE` evidence.
* [ ] Supported configuration files produce individual `CONFIG_FILE` evidence.
* [ ] One file kind cannot consume all candidate capacity when other eligible kinds exist.
* [ ] Unused category capacity is redistributed deterministically.
* [ ] Story-relevant paths are retained within categories without replacing final ranking.
* [ ] Scanner and workspace safety behavior remains unchanged.
* [ ] References, provenance, ordering, and token estimates remain traceable.
* [ ] Story 0012 ranking, concentration, diagnostics, and budgets remain authoritative.
* [ ] Multi-module and existing single-module regression tests pass.
* [ ] GET and POST Engineering Story Context contracts remain compatible.
* [ ] Complete backend tests and JaCoCo verification pass.
* [ ] Authenticated SonarQube analysis passes with zero new unresolved issue.
* [ ] Required canonical repository documentation is reconciled before Code Review.
* [ ] No content reading, symbol/dependency analysis, agent, persistence, project-resolution, or
  workflow change is introduced.
* [ ] Repository Analysis, Implementation Plan, Implementation Report, Documentation
  Reconciliation, Code Review, and Engineering Report follow the normal Human Approval workflow.
