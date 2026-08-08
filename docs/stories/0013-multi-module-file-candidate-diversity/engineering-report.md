# Engineering Report

## Story

Story 0013 — Multi-Module File Candidate Diversity.

The Story corrects Repository Structure candidate discovery so multi-module repositories provide a
bounded and representative set of source, test, and configuration evidence to the Repository
Context pipeline.

## Objective

The first real DevLog-assisted Repository Analysis benchmark returned 40 `TEST_FILE` candidates but
no `SOURCE_FILE` or individual `CONFIG_FILE` candidates. Story 0012 correctly limited test
concentration during final selection, but it could not select evidence that the collector had never
produced.

The objective was therefore to fix this pre-ranking discovery defect before adding file content or
deeper source understanding.

## Repository Analysis Summary

The analysis identified `RepositoryStructureCollector` as the ownership boundary for the defect.
Production source roots were recognized only at the beginning of a repository-relative path, while
test roots could be recognized inside module-prefixed paths. All file kinds then competed for one
40-item pre-ranking allowance, allowing tests to consume the complete candidate set.

The secure scanner and synchronized workspace already supplied safe normalized paths and did not
require modification. ADR-037, ADR-038, ADR-039, and ADR-040 required candidate discovery to remain
deterministic, bounded, separate from final ranking/selection, and represented as transient evidence
rather than trusted knowledge.

Affected areas were the Repository Structure Collector, its focused tests, Repository Context
composition coverage, and the canonical README capability description. No database, AI Engine,
frontend, persistence, or public API change was required.

## Implementation Plan Summary

The human-approved plan selected a private collector-level policy:

* recognize supported source and test roots at repository or module path boundaries;
* classify each eligible path exactly once, with test before source and configuration;
* sort source, test, and configuration buckets independently by Story-term path matches and stable
  alphabetical tie-breaking;
* allocate candidates in deterministic `SOURCE_FILE → TEST_FILE → CONFIG_FILE` round-robin order;
* preserve the existing total 40-item bound and naturally redistribute unused capacity;
* advance the collector version to `v2`;
* leave Context Intelligence, ranking, final selection, budgets, scanner safety, and API contracts
  unchanged.

File content, symbols, dependencies, embeddings, new collectors, project resolution, and unrelated
workflow changes were explicitly excluded.

## Implementation Summary

`RepositoryStructureCollector` now uses a shared segment-aware matcher for aggregate and individual
source/test discovery. Multi-module paths such as `backend/src/main/java/...` are recognized, while
near matches such as `src/main/java-copy` and `contest` are rejected.

Classification is path-based and mutually exclusive. A production source whose filename contains
`Test` remains source evidence because it is not located under a test root.

Eligible candidates are grouped by kind, sorted using the existing bounded Story-path relevance
signal, and interleaved deterministically under the existing collector limit. Final multi-criteria
ranking and Story 0012 concentration policy remain authoritative downstream.

All evidence continues through `EvidenceFactory` with the existing layer, kinds, references,
repository location, originating path, token estimation, and provenance. The collector version is
now `repository-structure:v2`.

The initial SonarQube run found cognitive complexity 16 against the allowed 15 in the new candidate
method. Private collection, sorting, and allocation helpers were extracted without changing
behavior. The subsequent Quality Gate passed.

The engine-level test uses a faithful collector fixture rather than repeating scanner integration,
and no controller test changed because the API and response shape remained compatible. These were
the only documented plan deviations.

## Modified Files

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`
  — multi-module root matching, explicit classification, per-kind ordering, round-robin allocation,
  focused helpers, and collector version `v2`.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java`
  — multi-module, boundary, classification, allocation, redistribution, determinism, provenance,
  and aggregate-evidence regressions.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — mixed source/test/configuration composition through normal ranking, selection, diagnostics, and
  budgets.
* `README.md` — canonical documentation for multi-module candidate discovery and allocation before
  final ranking and selection.

## Created Files

* `docs/stories/0013-multi-module-file-candidate-diversity/story.md` — reoriented Story definition.
* `docs/stories/0013-multi-module-file-candidate-diversity/repository-analysis.md` — human-approved
  repository analysis.
* `docs/stories/0013-multi-module-file-candidate-diversity/implementation-plan.md` — human-approved
  implementation plan.
* `docs/stories/0013-multi-module-file-candidate-diversity/implementation-report.md` — implementation
  and validation record.
* `docs/stories/0013-multi-module-file-candidate-diversity/code-review.md` — human-approved Code
  Review report.
* `docs/stories/0013-multi-module-file-candidate-diversity/engineering-report.md` — this final
  engineering record.

## Architecture Impact

No new architectural component, external dependency, persistence model, public contract, or ADR was
introduced.

The change refines the deterministic candidate semantics of the existing Repository Structure
Collector. Collection still owns discovery, while the ranker and selector own relevance and final
selection. Repository evidence remains transient and traceable. Scanner/workspace safety, endpoint
compatibility, budgets, and Story 0012 policies are preserved.

## Validation

Validation recorded by the Implementation Report:

```text
Focused Repository Structure, ranker, selector, Repository Context, and Web MVC suite:
32 tests passed; 0 failures/errors.

Post-Sonar-refactor focused collector/context suite:
19 tests passed; 0 failures/errors.

./mvnw verify:
391 tests passed; 0 failures/errors/skips.

./mvnw sonar:sonar -Dsonar.qualitygate.wait=true:
Quality Gate PASSED.

git diff --check:
Passed.
```

JaCoCo reports 3709 covered and 807 missed lines, approximately 82.13% bundle line coverage. Sonar
reports 86.1% new-code coverage, 0.0% new duplicated lines, and zero new bugs, vulnerabilities,
security hotspots, or code smells.

One complete run reproduced a pre-existing nondeterministic assertion in
`KnowledgeSelectionServiceTest`. It passed immediately in isolation and in the subsequent complete
391-test verification without modifying that unrelated component.

## Review Outcome

The Code Review verified all fourteen acceptance criteria, plan compliance, architecture
boundaries, documentation reconciliation, test coverage, and quality evidence. No Blocker, Major,
Minor, or Observation finding remained.

Technical recommendation: Ready for human approval.

Residual risk: equal round-robin allocation is deterministic and prevents candidate starvation, but
its practical precision across different repositories still requires observation in later real
Engineering Story benchmarks. Direct repository verification remains necessary because DevLog
still provides paths and metadata rather than exact implementation behavior.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

None required for Story 0013.

Bounded file-content enrichment remains an optional subsequent Story after this candidate-discovery
prerequisite is exercised in real workflows.

## Lessons Learned

Selection precision cannot compensate for missing candidate categories. Candidate generation must
first expose a representative, bounded corpus before downstream ranking and concentration policies
can operate effectively.

The collector/ranker/selector boundary remains useful: the collector guarantees safe candidate
availability, while Context Intelligence and selection retain final relevance and budget authority.

The first real benchmark was more valuable than adding the originally planned content capability:
it exposed a more fundamental multi-module discovery defect and prevented deeper functionality from
being built on an incomplete evidence foundation.

## Final Status

Completed
