# Story 0012 — Engineering Story Evidence Precision

## Story ID
0012

## Title
Improve Engineering Story evidence precision and missing-layer diagnostics

## Status
Completed

## Priority
High

## Date
2026-08-08

---

## User Story

As Kiko preparing an Engineering Story,
I want DevLog to return a balanced and explainable set of repository evidence,
So that the context guides me toward relevant modules, files, tests, configuration, knowledge, and
history without allowing one broad evidence category to dominate the analysis.

---

## Context

Stories 0001–0009 integrated DevLog as an optional deterministic context provider in Kiko's real
`engineering-story` Repository Analysis stage. Story 0009 supplied the first observational
benchmark of that integration.

The benchmark classified the result as `USEFUL_WITH_LIMITATIONS`:

* DevLog resolution and the Engineering Story Context API succeeded;
* 58 evidence items were returned from 58 candidates;
* provenance and the context digest were usable;
* principal modules and aggregate configuration files were surfaced;
* 40 of the 58 selected items were `TEST_FILE` evidence, most unrelated to the port-focused Story;
* no ADR, Git-history, or commit-diff evidence was returned;
* direct repository analysis still required one broad search, targeted searches, and approximately
  25 file or document reads.

The evidence distribution is important. ADR, Git-history, and commit-diff items were not discarded
by the selector: no candidate from those layers was present in the benchmark response. Conversely,
all 58 available candidates fit under the 60-item and 6,000-token budgets, so the rank-first fill
phase selected every test-file candidate despite weak practical usefulness.

The current ranker derives semantic relevance through substring matching of every normalized Story
term of at least three characters. Generic terms such as `test`, `API`, `backend`, or
`configuration` can therefore give the same high score to broad portions of the repository. The
current selector enforces only a minimum number of represented preferred layers before filling the
remaining global item/token budget by rank. It has no category concentration control and cannot
explain whether an expected layer was unavailable at collection time or available but excluded.

This Story is the first stage of the approved DevLog roadmap:

1. improve evidence selection and diagnostics using measured workflow behavior;
2. add deeper analytical capabilities only where subsequent benchmarks justify them;
3. introduce a specialized DevLog Repository Analyst agent after context quality and fallback
   behavior are proven.

Story 0012 improves precision and observability. It does not create missing repository knowledge,
add source-code understanding, or introduce an autonomous agent.

---

## Objective

Make `engineering-story-v1` produce a more balanced, useful, and explainable evidence set by:

* reducing the ability of a repeated evidence category to monopolize the context;
* reducing ranking inflation caused by generic Story vocabulary;
* preserving strongly relevant evidence even when its category is broadly represented;
* exposing candidate and selected distributions by evidence layer and kind;
* distinguishing unavailable preferred layers from candidates excluded by relevance, category
  concentration, or existing global budgets;
* retaining deterministic ordering, traceability, and current context budgets.

The completed capability must be profile/policy driven and reusable across Engineering Stories. It
must not contain vocabulary, paths, limits, or exceptions tailored specifically to Story 0009.

---

## Acceptance Criteria

### AC-1: Evidence concentration is bounded deterministically

The Engineering Story context policy must prevent one repeated evidence kind—especially
`TEST_FILE`—from consuming most of the selected context when other useful categories are available.

The policy must:

* define deterministic category concentration behavior;
* apply before or during final budget selection;
* preserve global maximum-item and maximum-token budgets;
* use stable ordering and deterministic tie-breaking;
* retain the highest-ranked eligible evidence within each affected category;
* avoid a hard-coded exception that recognizes only `TEST_FILE` or Story 0009.

Repository Analysis and Implementation Planning must determine whether the category boundary is
layer, kind, a cohesive evidence family, or a small combination of these. The chosen representation
must match the existing Context Profile and evidence architecture.

### AC-2: Strongly relevant evidence survives concentration control

Category balancing must not act as an unconditional truncation that discards a highly relevant item
merely because its category is common.

Tests must demonstrate that:

* a test file with strong Story-specific relevance remains selectable;
* lower-ranked repetitive evidence is excluded first;
* relevant source, configuration, module, knowledge, and historical candidates remain competitive;
* category behavior composes predictably with diversity and token/item budgets.

### AC-3: Generic Story vocabulary does not inflate broad repository categories

Semantic and guidance relevance must distinguish meaningful Story-specific terms from generic
workflow or repository vocabulary.

The solution must:

* use a deterministic and versioned normalization policy;
* address generic terms only when repository evidence shows they create broad false matches;
* avoid project-specific or Story-0009-specific stopword lists;
* preserve meaningful compound/path matches such as domain names, configuration identifiers, or
  feature-specific terms;
* keep ranking explanations sufficient to understand the calculated score.

### AC-4: Candidate and selected distributions are exposed

The resulting `RepositoryContext` diagnostics must expose, directly or through an existing
explainability structure:

* candidate count by `RepositoryContextLayer`;
* selected count by `RepositoryContextLayer`;
* candidate count by evidence kind or the approved evidence-family representation;
* selected count by the same category;
* whether each preferred layer produced at least one candidate.

The representation must remain deterministic and must not require log parsing.

### AC-5: Missing preferred layers are distinguished from selection exclusions

When a preferred layer has no collected candidate, context diagnostics must report an explicit,
non-fatal reason equivalent to:

`NO_CANDIDATE_FOR_PREFERRED_LAYER`

When candidates exist but are not selected, the decision model must distinguish at least:

* exclusion by insufficient relevance or applicable relevance policy;
* exclusion by category concentration control;
* exclusion by evidence-item budget;
* exclusion by token budget;
* duplicate reference handling.

The implementation must not fabricate evidence for an unavailable layer or misreport collection
absence as a selector rejection.

### AC-6: Diversity and budget behavior remains compatible

The existing diversity-first selection contract and global context budgets must remain effective.

The implementation must preserve:

* minimum diverse-layer behavior where candidates exist and fit;
* maximum evidence-item enforcement;
* maximum-token enforcement;
* reference deduplication;
* deterministic selected ordering;
* accurate used-token accounting;
* traceable selection decisions for every deduplicated candidate.

Any version change to ranking or selection policy must be explicit and covered by tests.

### AC-7: Existing context profiles remain stable unless explicitly versioned

No profile other than `engineering-story-v1` may change behavior accidentally.

If the selected design adds reusable category policy to `ContextPlan` or a profile definition:

* existing profiles must receive explicit compatible defaults;
* profile composition must remain deterministic;
* plan explanations must expose the active policy/version;
* serialization and existing consumers must remain backward compatible where required.

### AC-8: Explainability and provenance remain intact

Selected evidence must retain its existing:

* provenance;
* originating file when available;
* relevance score and criterion explanations;
* collector metadata;
* reference and related references;
* token estimate;
* context digest participation.

Balancing and diagnostics must add explanations rather than replace or weaken existing traceability.

### AC-9: Empty and sparse contexts degrade gracefully

The engine must continue producing a valid context when:

* no collector produces evidence;
* only one layer or category is available;
* preferred layers are absent;
* all remaining candidates fail an approved relevance policy;
* category limits cannot be filled;
* evidence exceeds the item or token budget.

These states must produce truthful diagnostics without turning an optional Engineering Story Context
request into a server failure.

### AC-10: The Story 0009 benchmark scenario is represented by regression tests

Create a deterministic regression fixture equivalent in shape—not hard-coded content—to the
observed benchmark:

* approximately 40 similarly scored test-file candidates;
* a smaller set of module, source/configuration, analysis, and validated-knowledge candidates;
* preferred layers with no candidates;
* global budgets large enough that all candidates would previously have been selected.

The result must demonstrate:

* repetitive test evidence no longer dominates the selected context;
* the best relevant test evidence is retained;
* available non-test categories remain represented where relevant and within budget;
* absent preferred layers receive the correct diagnostic;
* decisions and distribution counts are stable across repeated runs.

### AC-11: Existing tests and quality baseline remain healthy

The implementation must run:

* focused ranking, intelligence, selector, Repository Context service, and Engineering Story Context
  tests;
* the complete backend test suite;
* JaCoCo verification with the existing 80% bundle line-coverage rule;
* authenticated SonarQube analysis with the pinned scanner and Quality Gate wait.

Completion requires no new unresolved Sonar issue and a passing Quality Gate. No test may be removed
or weakened merely to accommodate changed selection behavior.

### AC-12: A real follow-up benchmark is prepared without contaminating Story artifacts

After implementation and normal workflow approval, the new policy must be suitable for another real
DevLog-assisted Engineering Story benchmark.

The benchmark itself remains observational and disposable:

* it must run on a real subsequent Engineering Story rather than replay Story 0009 artificially;
* benchmark observations must remain outside Git repositories;
* it should compare category distribution, evidence actually used, broad searches, targeted reads,
  missing layers, and irrelevant evidence;
* one run must not be represented as proof of general superiority or token savings.

Story 0012 tests the deterministic policy. It does not claim product validation before that later
real workflow run.

---

## Scope

### In Scope

* Engineering Story relevance normalization required to reduce generic-term score inflation.
* Deterministic category-aware selection or concentration control.
* Profile/policy representation needed to configure that behavior coherently.
* Candidate/selected distributions by layer and evidence category.
* Preferred-layer availability diagnostics.
* More precise selection/exclusion reasons.
* Ranking, selector, Context Intelligence, Repository Context, and API serialization tests where
  affected.
* A deterministic regression fixture shaped like the Story 0009 evidence distribution.
* Complete Maven/JaCoCo/SonarQube validation.

### Out of Scope

* New repository collectors.
* Reading or returning source-file contents.
* Configuration key/value or network-boundary extraction.
* Class, method, interface, annotation, symbol, call-graph, or dependency analysis.
* Source-to-test relationship inference.
* Embeddings, vector search, or AI-based context selection.
* Creating ADR, Git-history, or commit-diff evidence when no underlying candidate exists.
* Repository ingestion, synchronization, or freshness redesign.
* Automatic DevLog project resolution.
* A DevLog Repository Analyst agent.
* Changes to Kiko, `engineering-story`, `delegate-task`, or Human Approval semantics.
* Frontend changes unless a current diagnostic response is already rendered and must remain
  compatible; no new visualization is required.
* Database migrations unless Repository Analysis proves durable storage is genuinely required.

---

## Impacted Components

Repository Analysis must confirm the exact design and affected file set. Expected components are:

* `DeterministicEvidenceRanker` and its tests;
* `BudgetedDiverseEvidenceSelector` and selector tests;
* `DeterministicContextIntelligence`, `ContextProfileDefinition`, and `ContextPlan` only if policy
  belongs in the profile/plan contract;
* `RepositoryContext`, its selection decisions/warnings/diagnostics, and serialization tests;
* `RepositoryContextEngine` or service-level assembly only where candidate/selected distributions
  are computed;
* Engineering Story Context controller/service tests to protect the exposed response;
* Story 0012 workflow artifacts.

No collector, persistence entity, frontend component, AI Engine component, or Docker service is
expected to change.

---

## Architectural Ownership and Boundaries

* Collectors own deterministic discovery and production of evidence candidates.
* Context Intelligence owns versioned profile policy and explains the active plan.
* The Evidence Ranker owns per-candidate relevance scoring.
* The Evidence Selector owns cross-candidate diversity, concentration, deduplication, and budget
  decisions.
* `RepositoryContext` owns the immutable, traceable result and its diagnostics.
* DevLog may report that a preferred layer has no candidate; it must not infer that missing evidence
  exists.
* Kiko remains responsible for reasoning and targeted repository verification.
* The repository remains the implementation source of truth.
* `engineering-story` retains workflow orchestration and fallback behavior.
* The human remains approval authority.

This Story is expected to evolve the deterministic Repository Context policy established by ADR-038
and ADR-039. Repository Analysis must determine whether the change is an implementation refinement
covered by those ADRs or whether a new ADR/versioned policy decision is required.

---

## Tests and Validation

At minimum, tests must cover:

* repeated evidence categories under a non-binding global budget;
* preservation of the highest-ranked strongly relevant item;
* deterministic equal-score tie-breaking;
* generic-term normalization and meaningful domain-term preservation;
* preferred layer with zero candidates;
* preferred layer with candidates excluded by relevance, category, item budget, and token budget;
* diversity combined with category concentration;
* empty and single-category inputs;
* exact distribution counts and decision reasons;
* stable output across repeated identical requests;
* unchanged provenance/ranking metadata;
* response serialization through Engineering Story Context;
* complete backend tests, JaCoCo, and authenticated SonarQube Quality Gate.

The eventual real benchmark must remain outside the repository and must not alter the normal
Engineering Story workflow.

---

## Risks

### Overfitting to one benchmark

A policy tuned specifically to 40 test files or port-related vocabulary may improve Story 0009 while
hurting other Stories. Mitigation: configure a generic versioned policy and test several evidence
distributions and relevance strengths.

### Excessive balancing hides genuinely relevant evidence

A strict per-kind cap could discard multiple tests or source files that are all important to a
cross-cutting Story. Mitigation: preserve rank-aware behavior, define an explicit escape or relevance
rule if justified, and test both noisy and legitimately broad scenarios.

### Mixing collection absence with selector behavior

ADR/Git/diff evidence cannot be selected when collectors produce no candidates. Mitigation: expose
candidate distributions and missing-layer diagnostics; do not claim selector changes solve data
availability.

### Profile regressions

Adding policy to a shared plan may change non-Engineering Story analyses. Mitigation: explicit
compatible defaults and tests for every current Context Profile.

### Explainability drift

New category rules could create ambiguous exclusion reasons or counts. Mitigation: one authoritative
decision per candidate, exact diagnostic tests, and stable policy/version explanations.

### Digest instability remains separate

The benchmark observed request-time digest variability. This Story must not silently expand into a
digest redesign unless Repository Analysis proves the new diagnostics make a narrowly scoped change
unavoidable. Otherwise record it as separate follow-up.

---

## Definition of Done

* [ ] Every acceptance criterion is satisfied.
* [ ] Repetitive weak evidence cannot monopolize an Engineering Story context.
* [ ] Strongly relevant evidence remains selectable.
* [ ] Generic vocabulary handling is deterministic, versioned, and not project-specific.
* [ ] Candidate and selected distributions are exposed by layer and approved category.
* [ ] Missing preferred layers are distinguishable from selector exclusions.
* [ ] Selection decisions distinguish relevance, category, item-budget, token-budget, and duplicate
  handling.
* [ ] Diversity, ordering, deduplication, and token accounting remain correct.
* [ ] Provenance and ranking explanations remain intact.
* [ ] Existing Context Profiles remain stable or are explicitly versioned.
* [ ] Story-0009-shaped regression tests pass without hard-coding Story 0009.
* [ ] Complete backend tests and JaCoCo verification pass.
* [ ] Authenticated Sonar analysis passes with zero new unresolved issue.
* [ ] No collector, source-content analysis, agent, project-resolution, or workflow change is added.
* [ ] Repository Analysis, Implementation Plan, Implementation Report, Code Review, and Engineering
  Report are produced through the normal Human Approval workflow.
