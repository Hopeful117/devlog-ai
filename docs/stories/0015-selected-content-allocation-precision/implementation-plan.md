# Implementation Plan

## Overview

Story 0015 will replace reference-driven content-slot allocation with a narrow, versioned policy
that uses typed match-strength information already calculated by deterministic ranking. Global
ranking and diverse selection remain unchanged: the new policy operates only on selected
`SOURCE_FILE` and `TEST_FILE` evidence and decides which of those items receives the existing six
bounded content slots.

The plan preserves uncapped path-match strength before `DeterministicEvidenceRanker` caps semantic
and guidance criteria at 100. That typed information will be attached additively to `EvidenceScore`
for explainability and consumed by a dedicated selected-content allocation policy. Final score
remains the first allocation signal; uncapped semantic/guidance strength resolves saturation; the
evidence reference remains only the final deterministic tiebreaker.

Every eligible selected file will receive explicit allocation rank/reasons in its optional content
state. Tests will reproduce the benchmark defect with equal final scores and adversarial filenames.
After all technical validation, the local backend will be rebuilt and the same complete Story
request will be executed once. Disposable results will remain outside Git and must demonstrate the
concrete correction before Code Review.

## Planned Changes

1. **Preserve typed match strength in deterministic ranking.**
   Extend the score contract with a small additive precision/tiebreak structure containing uncapped
   semantic and guidance contribution or matched-term strength. Calculate it from the existing
   `TermModel` in `DeterministicEvidenceRanker`; do not parse explanation strings or introduce a
   second term tokenizer. Keep existing capped criteria, weighted final score, sorting, ranking
   reasons, profile weights, and global selection behavior unchanged. Preserve a compatible
   constructor/default for existing collectors and tests.

2. **Introduce a focused versioned content-allocation policy.**
   Add a component under `repositorycontext.enrichment` that accepts only already selected eligible
   source/test evidence and returns deterministic allocation order plus bounded reasons. Order by:
   final relevance score, typed semantic match strength, typed guidance match strength, then
   reference solely as the last reproducibility tiebreaker. Keep the precise composition inside the
   policy so `SelectedFileContentEnricher` does not become another ranker. Identify the policy and
   version explicitly.

3. **Make allocation state explainable.**
   Extend `RepositoryEvidenceContent` additively with allocation metadata such as policy identity,
   rank, and bounded reason codes/signals. Preserve the existing constructor and current extraction
   status/reason semantics. Complete, truncated, skipped, and unavailable evidence must all retain
   the allocation decision that preceded the read. Avoid exposing raw Story text or verbose internal
   calculations.

4. **Apply the policy without changing existing bounds.**
   Replace the current score/reference sort in `SelectedFileContentEnricher` with the allocation
   policy result. Continue to enforce selected-only eligibility, maximum enriched files, per-file
   and aggregate characters, remaining context tokens, workspace/revision validation, and reader
   failures exactly as Story 0014 defines. Do not raise the default limit of six files. Preserve
   original selected evidence order in the final response while using allocation order only for
   scarce reads.

5. **Reconcile final accounting, reasons, warnings, and digest.**
   Ensure enriched evidence token estimates account consistently for returned content and bounded
   allocation metadata. Update selected decision estimates and `usedTokens` from the final evidence.
   Because final evidence and content metadata are already digest inputs, verify that allocation
   policy/rank/reasons affect the digest deterministically. Do not change candidate counts,
   selected-by-kind diagnostics, or global selection reasons.

6. **Add outcome-oriented regression coverage.**
   Build generic fixtures with equal final scores where the strongest path match sorts after
   alphabetical distractors and content slots are scarce. Assert that typed match strength selects
   the central file, filename order alone cannot determine the outcome, the weakest evidence is
   skipped first, and every outcome carries the expected allocation explanation. Retain all Story
   0014 safety, limit, compatibility, and failure tests.

7. **Validate composition and compatibility.**
   Update engine and controller fixtures for additive score/content metadata. Verify deterministic
   serialization, backward-compatible constructors and request contracts, unchanged global ranking
   order unless final scores differ, unchanged selector/category behavior, truthful final tokens,
   and digest sensitivity.

8. **Reconcile canonical documentation.**
   Update README and ADR-044 to distinguish global evidence selection from selected-content slot
   allocation, document the versioned signals/reasons and unchanged limits, and state the remaining
   limits honestly. Update the roadmap only if the completed capability changes its current factual
   boundary. Record the documentation outcome in the Implementation Report.

9. **Run the post-implementation benchmark before Code Review.**
   After focused/full tests and a local Docker rebuild using the implementation, invoke the normal
   Engineering Story Context adapter once with the complete revised Story and standard timeout.
   Store comparison data only under `/tmp/devlog-story-0015-benchmark/`. Compare request duration,
   evidence/tokens/warnings, allocated/skipped files, central implementation coverage, distractors,
   and native reads against the recorded baseline. If the central content-allocation implementation
   remains skipped or equivalent low-value files still consume the scarce slots without a factual
   explanation, do not declare implementation complete; return to implementation and repeat formal
   technical validation before any new benchmark.

## Files to Modify

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/EvidenceScore.java`
  — add compatible typed match-strength/tiebreak metadata.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRanker.java`
  — preserve uncapped term strength while keeping current criteria and global sorting.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidenceContent.java`
  — add bounded allocation policy/rank/reason metadata compatibly.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryEvidence.java` — include
  additive allocation metadata in final token estimation without changing immutable behavior.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricher.java`
  — consume the focused allocator and preserve all existing bounds/accounting.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRankerTest.java`
  — verify typed uncapped strength, capped criteria, and unchanged ranking behavior.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedFileContentEnricherTest.java`
  — add equal-score/adversarial-name outcome regression and allocation reasons.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — verify final accounting, selection compatibility, and digest sensitivity.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — verify additive serialization and unchanged GET/POST requests.
* `README.md` — document selected-content allocation and its limitations.
* `docs/decisions/ADR-044.md` — record the refined allocation policy inside the existing
  post-selection architecture.
* `docs/roadmap.md` — update only if documentation reconciliation finds its current statement no
  longer factually sufficient.

## Files to Create

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedContentAllocationPolicy.java`
  — focused, versioned, deterministic ordering and explanation for selected eligible evidence.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/enrichment/SelectedContentAllocationPolicyTest.java`
  — isolated ordering, equal-score, typed-strength, reference-tiebreak, and determinism tests.
* `docs/stories/0015-selected-content-allocation-precision/implementation-report.md` — factual
  implementation, documentation, validation, and benchmark outcome after implementation.

No benchmark report will be created inside the repository.

## Dependencies

No new external dependency, database migration, service, or API endpoint is required.

Internal dependencies and order are:

1. the ranker must expose typed match strength before the allocator can consume it;
2. the allocation policy must exist before the enricher can attach allocation results;
3. final evidence accounting/serialization follows the content contract change;
4. focused and complete validation precede Docker rebuild and the one post-implementation benchmark;
5. documentation reconciliation and the Implementation Report precede Code Review.

The current synchronized workspace, reader, `RepositoryContentPolicy`, global ranker/selector,
ObjectMapper digest, Docker environment, project mapping, and external benchmark directory are
prerequisites already available.

## Test Plan

### Ranker tests

* Equal capped semantic scores retain different typed uncapped match strengths.
* Existing final criteria, weights, final score, ordering, and reasons remain deterministic.
* Common-term filtering and guidance strength remain versioned and explainable.
* Existing constructors/default score fixtures remain compatible.

These tests cover AC-2, AC-3, AC-7, AC-9, and AC-10.

### Allocation policy tests

* More equal-score eligible files than slots, with the strongest match sorting last alphabetically.
* Renamed/reordered distractors do not displace the strongest semantic match.
* Reference order is deterministic only when all meaningful signals tie.
* Source and test evidence are eligible; configuration/non-file evidence is excluded upstream.
* Allocation ranks and bounded reasons match the returned order.

These tests cover AC-1 through AC-4, AC-7, and AC-8.

### Enricher tests

* The benchmark-shaped central file receives content while lower-strength selected evidence receives
  `SKIPPED/ENRICHED_FILE_LIMIT`.
* Existing six-file, per-file, aggregate-character, token, workspace, revision, truncation, and
  unavailable behavior remains unchanged.
* Final response order remains stable independently of read-allocation order.
* Allocation metadata is present for complete, truncated, skipped, and unavailable results.
* No content is read to calculate priority; verify reader interactions are limited to allocated
  evidence.

These tests cover AC-1, AC-4 through AC-10.

### Engine/API regression tests

* Final evidence tokens, selected decision estimates, `usedTokens`, warnings, and digest are
  consistent with allocation results.
* Changing typed allocation strength changes allocation and digest deterministically.
* GET/POST requests and existing evidence/content fields remain compatible.
* Story 0012 category precision and Story 0013 multi-module candidate diversity remain intact.

These tests cover AC-9 and AC-10.

### Validation commands

```text
./mvnw -q -Dtest=DeterministicEvidenceRankerTest,SelectedContentAllocationPolicyTest,SelectedFileContentEnricherTest,RepositoryContextServiceTest,EngineeringStoryContextControllerWebMvcTest test
./mvnw -q verify
source ../.env && ./mvnw -q sonar:sonar -Dsonar.qualitygate.wait=true
docker compose up -d --build backend
node <engineering-story>/scripts/devlog-context.mjs --base-url http://localhost:18080 --project-id 52375024-fc51-4fe4-bc70-0d4cacdcc0a9 < story.md
git diff --check
```

Expected success requires focused and complete tests with no failure, the existing JaCoCo bundle
rule, zero new unresolved Sonar issues, a passing Quality Gate, compatible live API output, and a
post-implementation benchmark that demonstrates the concrete allocation correction.

## Risks

### Typed strength changes the public score representation

The score is serialized. Mitigation: make the field additive, immutable, versioned, deterministic,
and preserve a compatibility constructor/default for existing producers and tests.

### The allocator duplicates ranking logic

Mitigation: compute term strength once inside the existing ranker and let the allocator only compose
typed signals for its narrow scarce-content responsibility. Do not parse explanation strings or
retokenize the Story.

### Raw term counts can reward long generic paths

Mitigation: reuse the ranker's discriminating/common-term model and contribution strength rather
than simple substring count. Keep reference as the final tiebreaker and add adversarial generic-path
fixtures.

### Token accounting becomes inconsistent

Mitigation: retain final accounting in the enricher/evidence contract, include bounded allocation
metadata in estimates, and verify exact evidence/decision/context sums and digest changes.

### Tests or benchmark overfit DevLog

Mitigation: unit fixtures use generic renamed paths; the live benchmark is a separate concrete
validation and cannot replace those tests or establish general product claims.

### Benchmark is flaky near the consumer timeout

Mitigation: execute once under normal local conditions, record duration and fallback factually, and
do not weaken the provider or silently raise limits in this DevLog Story. A natural timeout does not
invalidate deterministic tests but prevents claiming practical effectiveness for that run.

No blocking risk requires additional clarification.

## Validation Checklist

- [ ] Global ranking and `BudgetedDiverseEvidenceSelector` ownership remain unchanged.
- [ ] Only selected source/test evidence competes for content.
- [ ] Typed uncapped match strength is deterministic, additive, and versioned.
- [ ] Content allocation uses meaningful signals before reference order.
- [ ] Equal-score adversarial regression enriches the strongest generic Story/path match.
- [ ] Renaming/reordering distractors does not alter the intended winner.
- [ ] Allocation policy/rank/reasons are visible and bounded for every eligible result.
- [ ] Existing content file, character, size, duration, and token limits remain unchanged.
- [ ] Reader calls occur only for allocated evidence.
- [ ] Configuration and non-file evidence remain content-free.
- [ ] Final tokens, decisions, warnings, diagnostics, and digest remain truthful.
- [ ] GET/POST Engineering Story Context contracts remain compatible.
- [ ] Story 0012 precision and Story 0013 multi-module diversity regressions pass.
- [ ] Focused tests pass.
- [ ] Complete backend `verify` and JaCoCo rule pass.
- [ ] Authenticated SonarQube Quality Gate passes with no new unresolved issue.
- [ ] Docker backend rebuild and live context request succeed or degrade factually.
- [ ] Post-implementation benchmark remains outside Git and demonstrates the concrete correction.
- [ ] Documentation Reconciliation is recorded before Code Review.
- [ ] No symbols, AST, dependency analysis, persistence, AI interpretation, or workflow change is
      introduced.

## Recommendation

Ready for implementation

The implementation boundary, typed signal, allocation ownership, compatibility strategy, tests,
quality validation, and mandatory post-implementation benchmark are sufficiently defined. No
blocking ambiguity or architectural conflict remains.

This recommendation is technical only. It does not approve the Implementation Plan or authorize
implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
