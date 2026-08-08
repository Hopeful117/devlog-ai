# Implementation Report

## Overview

Story 0012 implements a deterministic, versioned precision policy for
`engineering-story-v1`. Context Intelligence now owns common-term, minimum-relevance,
evidence-kind concentration, and strong-relevance thresholds. The ranker uses candidate-corpus
frequency to reduce generic Story-term inflation, while the selector prevents ordinary evidence of
one kind from monopolizing the context and preserves strongly relevant overflow.

Repository Context now exposes candidate/selected distributions, preferred-layer availability,
duplicate accounting, and precise selection reasons. Existing endpoint inputs, evidence
provenance, budgets, and non-Engineering Story profile behavior remain compatible.

## Modified Files

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/ContextProfileDefinition.java`
  — adds a precision policy with an unrestricted compatibility constructor.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/ContextPlan.java`
  — transports the composed policy with a compatibility constructor.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligence.java`
  — activates `engineering-story-precision:v1`, composes policies, explains active bounds, and
  versions the plan as `context-intelligence-v2`.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRanker.java`
  — adds corpus-aware term scoring, originating-file guidance matching, term explanations, and
  `multi-criteria-v2`; unrestricted policies preserve the prior fixed contribution.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelector.java`
  — adds relevance eligibility, kind concentration, strong-relevance overflow, phase-specific
  reasons, and explicit duplicate decisions while preserving diversity and budgets.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContext.java`
  — adds diagnostics through an additive field and retains the prior constructor.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`
  — assembles distributions/availability, reconciles warnings, and includes diagnostics/policy in
  the digest.
* `backend/src/main/java/com/hopeful117/devlogai/history/service/ProjectHistoryServiceImpl.java`
  — replaces one pre-existing lambda with a method reference required by the Sonar Quality Gate.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligenceTest.java`
  — verifies plan version, unrestricted defaults, and Engineering Story policy activation.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — verifies versions, distributions, missing-layer diagnostics, and retained traceability.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — verifies additive diagnostic serialization without changing GET/POST inputs.

## New Files

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/EvidencePrecisionPolicy.java`
  — immutable validated policy and deterministic composition.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextDiagnostics.java`
  — immutable distributions, preferred-layer availability, and duplicate counts.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRankerTest.java`
  — focused common/rare term, path relevance, single-candidate, compatibility, and determinism
  tests.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelectorTest.java`
  — focused benchmark-shaped concentration, strong-overflow, relevance, duplicate, token/item,
  and empty-input tests.
* `docs/stories/0012-engineering-story-evidence-precision/implementation-report.md`
  — this execution record.

## Tests

Seven ranker/selector tests were introduced and three Context Intelligence, Repository Context, and
Web MVC test classes were extended. Coverage includes:

* generic term suppression and discriminating path terms;
* prior fixed scoring under unrestricted profiles;
* approximately 40 repeated test candidates plus 18 alternative-category candidates;
* evidence-kind concentration below half of the selected fixture;
* strong-relevance overflow;
* minimum relevance, category, item, token, and duplicate reasons;
* empty input, deterministic repeat ranking, distributions, missing preferred layers, digest, and
  API serialization.

Final complete result: 386 tests, 0 failures, 0 errors, 0 skipped. JaCoCo line coverage remains
above the bundle threshold at approximately 82.05%; Sonar new-code coverage is above 80%.

## Validation

```text
Command: ./mvnw -Dtest=DeterministicContextIntelligenceTest,DeterministicEvidenceRankerTest,BudgetedDiverseEvidenceSelectorTest,RepositoryContextServiceTest,EngineeringStoryContextControllerWebMvcTest test
Result: Passed.

Command: ./mvnw verify
Result: Passed; 386 tests, 0 failures/errors, JaCoCo bundle rule satisfied.

Command: ./mvnw sonar:sonar -Dsonar.qualitygate.wait=true (SONAR_TOKEN loaded from ignored .env)
Result: Passed; Quality Gate OK, 0 new violations, new-code coverage above 80%, 0.0% new duplication.

Command: git diff --check
Result: Passed.
```

A complete verification briefly exposed an existing nondeterministic assertion in
`KnowledgeSelectionServiceTest`; the isolated test passed and subsequent complete verification
passed without modifying it.

## Deviations

* The diagnostics were implemented as the standalone `RepositoryContextDiagnostics` record rather
  than nested records, matching the plan's allowed alternative and keeping `RepositoryContext`
  focused.
* Sonar newly reported an old lambda in `ProjectHistoryServiceImpl` under the inherited
  `PREVIOUS_VERSION` baseline. A mechanical method-reference replacement was required to obtain the
  approved zero-new-issue Quality Gate. It changes no behavior, API, persistence, security, or Story
  architecture.
* Dedicated ranker and selector tests carry the benchmark-shaped fixture instead of adding a large
  collector-backed engine fixture. Engine and API tests separately verify diagnostic assembly and
  serialization.

## Remaining Work

None.

The real follow-up Engineering Story benchmark remains intentionally outside Story 0012 and outside
Git, as required by AC-12.

## Recommendation

Ready for Review
