# Engineering Report

## Story

Story 0012 — Engineering Story Evidence Precision.

The Story improves the precision and explainability of the evidence returned by the
`engineering-story-v1` Repository Context profile. It prevents weak evidence of one repeated kind
from dominating the selected context, reduces relevance inflation from vocabulary common across
the candidate corpus, preserves strongly relevant evidence, and exposes diagnostics explaining
candidate availability and selection outcomes.

## Objective

The objective was to address the principal limitation observed during the first real
DevLog-assisted Engineering Story benchmark: 40 of 58 selected evidence items were `TEST_FILE`
items, while expected ADR, history, and commit-diff layers had supplied no candidates.

The requested correction had to remain deterministic, profile-driven, budget-aware, traceable,
and reusable. It was not intended to add new repository knowledge, source-code interpretation, or
agent autonomy.

## Repository Analysis Summary

The analysis located the change in the backend Repository Context pipeline:

```text
Context Intelligence → collectors → ranker → selector → RepositoryContextEngine
```

The existing ranker assigned equal fixed relevance contributions to every matching Story term,
regardless of how broadly the term occurred. The selector enforced diversity and global budgets,
but had no relevance threshold or category concentration control. Because the benchmark context
fit within its global budgets, weak repeated test evidence was selected rather than excluded.

The affected boundaries were Context Intelligence, evidence ranking, evidence selection,
Repository Context assembly, diagnostics, and additive API serialization. ADR-038 required the
pipeline to remain deterministic and traceable; ADR-039 assigned reusable selection policy to
Context Intelligence; ADR-040 required repository evidence to remain distinct from validated
knowledge. Existing collectors, persistence, API inputs, AI interpretation, and workflow behavior
were outside scope.

## Implementation Plan Summary

The approved plan introduced a versioned precision policy carried from
`ContextProfileDefinition` through `ContextPlan`. Only `engineering-story-v1` would activate the
new bounds; all other profiles would receive an unrestricted compatibility policy.

The plan then:

* built one deterministic candidate-frequency model for Story terms;
* reduced the contribution of terms common across the evidence corpus;
* applied minimum-relevance and evidence-kind concentration rules after ranking;
* allowed strongly relevant evidence to exceed the ordinary category allowance;
* preserved diversity-first selection and item/token budgets;
* exposed candidate/selected distributions and preferred-layer availability;
* added explicit selection and exclusion reasons;
* covered ranking, selection, engine assembly, API serialization, compatibility, and the
  Story-0009-shaped distribution through focused tests.

New collectors, content or symbol analysis, embeddings, persistence changes, automatic project
resolution, and changes to Engineering-Skills remained excluded.

## Implementation Summary

The implementation added `engineering-story-precision:v1`, with deterministic thresholds for
common-term frequency, minimum relevance, ordinary evidence-kind share, and strong relevance.
Context Intelligence and the ranking policy were explicitly advanced to v2.

The ranker now evaluates Story terms against the complete candidate corpus. Discriminating terms
retain relevance value, while terms occurring too broadly are reported as common rather than
inflating every candidate. Guidance matching also includes the originating file. Unrestricted
profiles preserve the prior fixed term contribution.

The selector now performs relevance eligibility, diversity-first selection, ordinary kind
concentration, strong-relevance overflow, and global budget enforcement. Its decisions distinguish
diversity, rank, strong relevance, insufficient relevance, category concentration, item budget,
token budget, and duplicate references.

Repository Context now includes deterministic diagnostics for candidate and selected
distributions, preferred-layer availability, and duplicate accounting. These diagnostics and the
active policy participate in the context digest. Existing request contracts and evidence
provenance remain intact.

Documented deviations were limited to using a standalone diagnostics record, distributing the
benchmark-shaped regression coverage across focused selector and engine/API tests, and replacing
one pre-existing history-service lambda with an equivalent method reference required by the Sonar
new-code baseline.

## Modified Files

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/ContextProfileDefinition.java`
  — adds the precision policy and unrestricted compatibility constructor.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/ContextPlan.java`
  — transports the composed precision policy.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligence.java`
  — activates the Engineering Story policy and exposes its version and bounds.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRanker.java`
  — adds corpus-aware term scoring and expanded explanations.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelector.java`
  — adds relevance, concentration, strong-overflow, and precise decision handling.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContext.java`
  — adds diagnostics through a backward-compatible field and constructor.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`
  — assembles diagnostics and includes the policy and diagnostics in the digest.
* `backend/src/main/java/com/hopeful117/devlogai/history/service/ProjectHistoryServiceImpl.java`
  — uses an equivalent method reference to satisfy the Sonar baseline.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligenceTest.java`
  — verifies policy activation, versions, defaults, and explanations.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — verifies distributions, missing layers, digest behavior, and traceability.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — verifies additive diagnostics in the GET and POST responses.

## Created Files

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/EvidencePrecisionPolicy.java`
  — immutable, validated, composable precision policy.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextDiagnostics.java`
  — immutable evidence distributions, layer availability, and duplicate accounting.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRankerTest.java`
  — focused corpus relevance, compatibility, path matching, and determinism tests.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelectorTest.java`
  — focused concentration, strong-overflow, exclusion, budget, duplicate, and empty-state tests.
* `docs/stories/0012-engineering-story-evidence-precision/implementation-report.md`
  — implementation and validation record.
* `docs/stories/0012-engineering-story-evidence-precision/code-review.md`
  — acceptance-criteria and architecture review record.
* `docs/stories/0012-engineering-story-evidence-precision/engineering-report.md`
  — this final engineering record.

## Architecture Impact

The Story adds a reusable immutable policy abstraction to Context Intelligence and a standalone
Repository Context diagnostics model. Ranking and selection semantics for the Engineering Story
profile are explicitly versioned; other profiles retain unrestricted behavior.

No external dependency, database migration, persistence model, security boundary, frontend
contract, AI Engine behavior, collector, or workflow responsibility changed. The Engineering Story
Context endpoint inputs remain compatible and its response extension is additive.

DevLog remains the deterministic context specialist. It does not infer missing evidence or replace
direct repository verification.

## Validation

The following validation is recorded by the Implementation Report and approved Code Review:

```text
Focused Maven tests for Context Intelligence, ranking, selection, Repository Context, and Web MVC
Result: Passed.

./mvnw verify
Result: Passed; 386 tests, 0 failures, 0 errors, 0 skipped.

JaCoCo
Result: Approximately 82.05% line coverage; existing 80% bundle rule passed.

./mvnw sonar:sonar -Dsonar.qualitygate.wait=true
Result: Quality Gate OK; zero new violations, new-code coverage above 80%, and 0.0% new duplication.

git diff --check
Result: Passed.
```

One pre-existing randomized assertion failed transiently during an earlier complete run, then
passed in isolation and in subsequent complete verification without modification. It was reviewed
as an unrelated flaky fixture rather than a Story 0012 defect.

## Review Outcome

The Code Review verified all twelve acceptance criteria as passed. It found no Blocker, Major,
Minor, or Observation finding and confirmed compliance with ADR-038, ADR-039, and ADR-040.

Technical recommendation: Ready for human approval.

Human Code Review approval: granted.

The remaining risk is empirical rather than a missing Story requirement: the selected precision
thresholds have synthetic regression coverage but have not yet been measured in a second real
DevLog-assisted Engineering Story.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

None for Story 0012.

A subsequent real Engineering Story may run the observational benchmark required to evaluate the
policy's practical usefulness. That benchmark is intentionally outside this Story and outside the
Git repositories.

## Lessons Learned

* Global token and item budgets do not prevent context noise when every candidate fits; precision
  requires relevance and concentration policy before final budget fill.
* Candidate absence and selector exclusion are different facts. Exposing both prevents missing ADR
  or history layers from being misdiagnosed as ranking failures.
* Corpus frequency provides a deterministic way to reduce generic vocabulary inflation without
  maintaining project-specific stopword lists.
* Compatibility defaults are essential when a reusable policy is introduced into an existing set
  of Context Profiles.
* Synthetic distribution tests can protect selection invariants, but a real workflow benchmark is
  still required before claiming improved Kiko effectiveness.

## Final Status

Completed
