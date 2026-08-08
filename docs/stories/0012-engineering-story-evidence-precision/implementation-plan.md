# Implementation Plan

## Overview

Story 0012 will introduce a versioned, profile-owned evidence precision policy and apply it at the
two deterministic stages that already own relevance and cross-candidate selection.

The ranking stage will build one query-term model from the complete candidate corpus. Terms found
throughout the corpus will contribute less—or no semantic/guidance boost—than discriminating terms,
while meaningful domain and path terms remain eligible. The selection stage will apply a minimum
relevance threshold and a rank-aware concentration limit by evidence `kind`, with an explicit
strong-relevance escape. Diversity, global item/token budgets, reference deduplication, ordering,
and provenance remain authoritative.

The engine will expose candidate and selected distributions by layer and kind, preferred-layer
availability, and precise decision reasons. The existing Engineering Story Context endpoint and
request models remain unchanged; its additive response contract will carry the new diagnostics.

This strategy addresses the approved Repository Analysis without adding collectors, source-code
understanding, persistence, AI selection, or workflow behavior.

## Planned Changes

1. **Define a versioned evidence precision policy in Context Intelligence.**

   Add an immutable policy value owned by each `ContextProfileDefinition`. It will identify its
   policy/version and express deterministic parameters for:

   * the maximum candidate-frequency ratio at which a query term remains discriminating;
   * the minimum final relevance score required for ordinary selection;
   * the maximum ordinary share of the available selection capacity that one evidence `kind` may
     consume;
   * the final-score threshold that permits strongly relevant evidence to exceed the ordinary
     concentration limit.

   Provide an explicit unrestricted compatibility policy for existing profiles. Assign the new
   precision policy only to `engineering-story-v1`. Compose multiple active profiles
   deterministically by selecting the strictest applicable bounds and record the resulting policy
   key/version and values in `ContextPlan.explanations()`.

   Increment the Context Intelligence plan version because the plan contract and active selection
   semantics change. Keep criterion weights, preferred layers, minimum diversity, and global
   budgets unchanged.

2. **Make semantic and guidance ranking corpus-aware.**

   In `DeterministicEvidenceRanker`, normalize the objective and guidance once per `rank` call and
   calculate document frequency against the searchable text of the complete candidate set. Use the
   same evidence fields currently considered—kind, summary, and originating file—while including
   originating-file matches consistently for guidance as well as semantic relevance.

   Terms at or above the active common-term ratio will not contribute the same fixed boost as a
   discriminating term. Remaining terms will receive a deterministic contribution weighted by
   their inverse candidate frequency, capped to the existing 0–100 criterion range. Exact integer
   rounding and boundary behavior will be centralized and covered by tests. The current-analysis
   semantic floor remains intact.

   Preserve compound/path usefulness by deriving terms from the complete untruncated Story and by
   matching normalized alphanumeric tokens against normalized candidate text. Do not introduce a
   project-specific stopword list. Extend ranking explanations with the active ranking policy,
   matched discriminating terms, and ignored/common terms without removing the existing criterion
   and weight explanations.

   Change the ranker policy identifier from `multi-criteria-v1` to an explicit v2 identifier so
   consumers and digests can distinguish changed ranking semantics.

3. **Apply relevance and evidence-kind concentration during selection.**

   Refactor `BudgetedDiverseEvidenceSelector` around one deterministic eligibility/decision pass.
   After reference deduplication, calculate ordinary capacity from the smaller of the deduplicated
   candidate count and the global maximum-evidence budget. Derive the per-kind ordinary allowance
   from the active profile percentage using documented integer rounding and a minimum allowance of
   one when capacity exists.

   Selection order will remain the ranker's stable order:

   * candidates below the profile relevance threshold are excluded with
     `INSUFFICIENT_RELEVANCE`;
   * the diversity pass chooses the highest-ranked eligible evidence for preferred layers, subject
     to item/token budgets;
   * the rank-fill pass chooses remaining candidates while enforcing the per-kind allowance;
   * a candidate at or above the strong-relevance threshold may exceed that allowance;
   * lower-ranked overflow is excluded with `CATEGORY_CONCENTRATION_LIMIT`;
   * item and token failures retain distinct reasons;
   * repeated references retain an explicit `DUPLICATE_REFERENCE` decision rather than being
     silently lost.

   Track the actual selection phase so selected decisions distinguish diversity selection from
   ordinary rank selection and strong-relevance overflow. Preserve exact used-token accounting and
   the engine's final deterministic output ordering.

4. **Add immutable Repository Context diagnostics.**

   Extend `RepositoryContext` with additive deterministic diagnostics containing:

   * raw candidate counts by `RepositoryContextLayer`;
   * selected counts by layer (retaining the existing `selectedByLayer` field for compatibility);
   * raw candidate counts by evidence kind;
   * selected counts by evidence kind;
   * one availability record for every preferred layer, indicating whether it produced a
     candidate and using `NO_CANDIDATE_FOR_PREFERRED_LAYER` when it did not;
   * duplicate accounting where necessary to reconcile raw candidates, unique selection
     candidates, decisions, selected evidence, and discarded totals.

   Use immutable records/maps/lists and deterministic enum/preferred-layer ordering. Diagnostics
   report observed facts only; they must not infer or synthesize missing evidence.

5. **Assemble diagnostics and update digest participation in the engine.**

   Update `RepositoryContextEngine` to calculate candidate distributions before ranking,
   selected distributions after selection, and preferred-layer availability from the active
   `ContextPlan`. Build truthful warnings/diagnostics for empty and sparse contexts without turning
   them into exceptions.

   Include the active precision policy, new diagnostics, and revised decisions in the context
   digest input. Preserve the existing digest algorithm and its deterministic behavior for an
   identical complete request; do not broaden this work into the separate request-time digest
   investigation.

   Calculate `discardedCount` and `truncated` from reconciled raw/selected counts so category and
   relevance exclusions are represented even when global budgets are not exhausted. Keep
   `REPOSITORY_CONTEXT_BUDGET_APPLIED` specific to actual global budget pressure; use decision and
   availability diagnostics for policy exclusions and missing layers.

6. **Protect the Engineering Story API serialization contract.**

   Keep both existing operations and their inputs unchanged:

   * `GET /api/projects/{projectId}/engineering-story-context?description=...`;
   * `POST /api/projects/{projectId}/engineering-story-context` with the existing JSON body.

   Update controller serialization expectations so `repositoryContext` exposes the new additive
   diagnostics, revised reasons, policy explanations, and digest while retaining existing evidence,
   provenance, ranking details, budgets, counts, and warnings.

7. **Add focused regression coverage before engine-level adaptation.**

   Introduce dedicated ranker and selector test classes. First lock down term-frequency scoring,
   path/domain term preservation, stable ties, relevance exclusion, diversity interaction,
   concentration behavior, strong-relevance overflow, duplicates, item/token precedence, empty
   inputs, and repeated-run determinism.

   Then extend Context Intelligence, engine/service, and Web MVC tests. Add a Story-0009-shaped
   fixture with roughly 40 similarly scored test candidates plus smaller source, module,
   configuration, analysis, and validated-knowledge groups. It must use generic evidence content,
   not Story 0009 paths or a `TEST_FILE` exception.

8. **Run layered quality validation.**

   Run focused tests first, then the complete backend Maven verification and JaCoCo 80% bundle
   line-coverage rule. Finally run the pinned authenticated SonarQube analysis with Quality Gate
   waiting enabled. Do not suppress, exclude, or weaken a test or quality rule to accept the new
   policy.

## Files to Modify

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/ContextProfileDefinition.java`
  — attach the immutable precision policy to profile definitions.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/ContextPlan.java`
  — expose the composed policy to ranking/selection.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligence.java`
  — configure compatible defaults, enable `engineering-story-v1`, compose policies, increment the
  plan version, and explain the active policy.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRanker.java`
  — implement corpus-aware term discrimination, consistent candidate matching, v2 ranking
  explanations, and policy versioning.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelector.java`
  — implement relevance eligibility, kind concentration, strong-relevance overflow, phase-aware
  decisions, and explicit duplicate handling.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContext.java`
  — add immutable distributions and preferred-layer availability diagnostics.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`
  — calculate diagnostics, reconcile counts/warnings, and include new data in the digest.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligenceTest.java`
  — protect policy defaults, Engineering Story activation, composition, and explanations.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — cover distributions, missing layers, sparse/empty contexts, policy exclusions, digest stability,
  provenance, and the benchmark-shaped fixture.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — protect additive JSON serialization for GET and POST responses.

## Files to Create

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/EvidencePrecisionPolicy.java`
  — immutable versioned relevance/concentration policy with validation and an unrestricted default.
* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextDiagnostics.java`
  — immutable candidate/selected distributions, preferred-layer availability, and duplicate
  accounting, unless implementation shows these nested records are clearer inside
  `RepositoryContext`.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRankerTest.java`
  — focused corpus-aware relevance and deterministic-order tests.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelectorTest.java`
  — focused selection-policy, decision-reason, budget, diversity, duplicate, and sparse-input tests.

## Dependencies

The implementation reuses the existing `ContextProfileDefinition`, `ContextPlan`,
`RepositoryEvidence`, `EvidenceScore`, `EvidenceSelector`, `SelectionResult`, and Jackson record
serialization contracts. Ranking must precede selection; both must consume the same composed policy.
Engine diagnostics depend on raw collector output and the completed selection result.

No new external library, service, database, migration, Docker component, frontend dependency, or AI
Engine change is required. The existing Java collections, deterministic comparators, Spring/Jackson
serialization, JUnit, Maven, JaCoCo, and pinned Sonar scanner are sufficient.

The Engineering-Skills symlink correction is separate and already applied. It is not an
implementation dependency of DevLog Story 0012, although it enables later real workflow validation.

## Test Plan

1. **Context Intelligence tests**

   * unrestricted defaults preserve all non-Engineering Story profiles;
   * `engineering-story-v1` exposes the expected precision policy and version;
   * multi-profile composition is deterministic and order-independent;
   * plan explanations contain ranking/selection policy identifiers and values.

2. **Ranker tests**

   * a term appearing in most candidates does not give all candidates a saturated semantic or
     guidance score;
   * a rare domain/path term materially distinguishes matching evidence;
   * current-analysis minimum semantic relevance remains intact;
   * equal final scores retain layer/reference deterministic ordering;
   * ranking explanations identify the v2 policy and term treatment;
   * repeated identical calls return identical scores, explanations, and ordering.

3. **Selector tests**

   * a repeated generic kind is bounded under a non-binding global budget;
   * the highest-ranked candidates in that kind are retained;
   * a strong Story-specific candidate can exceed the ordinary kind allowance;
   * weak candidates receive `INSUFFICIENT_RELEVANCE`;
   * ordinary overflow receives `CATEGORY_CONCENTRATION_LIMIT`;
   * diversity selection remains effective for eligible preferred layers;
   * item budget, token budget, and duplicate reference reasons remain distinct;
   * empty, one-kind, and sparse inputs produce stable valid selections;
   * used tokens and selected counts exactly match selected evidence.

4. **Engine/service regression tests**

   * Story-0009-shaped candidates no longer yield a test-dominated context while retaining the best
     relevant test;
   * candidate and selected distributions match raw and selected evidence exactly;
   * absent preferred layers report `NO_CANDIDATE_FOR_PREFERRED_LAYER`;
   * a layer with excluded candidates is not misreported as absent;
   * provenance, collector metadata, originating files, score explanations, and related references
     remain unchanged;
   * policy exclusions set discarded/truncated state truthfully without falsely reporting a global
     budget warning;
   * identical complete inputs produce identical context and digest.

5. **API tests**

   * GET and POST keep their current input contracts;
   * the serialized response retains existing Repository Context fields;
   * new distributions, preferred-layer availability, policy explanations, decisions, and digest
     are present and correctly shaped.

6. **Validation commands**

   Focused tests:

   ```bash
   cd backend
   ./mvnw -Dtest=DeterministicContextIntelligenceTest,DeterministicEvidenceRankerTest,BudgetedDiverseEvidenceSelectorTest,RepositoryContextServiceTest,EngineeringStoryContextControllerWebMvcTest test
   ```

   Complete backend and JaCoCo validation:

   ```bash
   cd backend
   ./mvnw verify
   ```

   Authenticated SonarQube analysis using the existing non-versioned token environment:

   ```bash
   cd backend
   ./mvnw org.sonarsource.scanner.maven:sonar-maven-plugin:5.7.0.6970:sonar \
     -Dsonar.token="$SONAR_TOKEN" \
     -Dsonar.qualitygate.wait=true
   ```

   Expected success: all focused and complete tests pass, JaCoCo remains at or above the configured
   80% bundle line threshold, Sonar reports a passing Quality Gate with no new unresolved issue, and
   `git diff --check` reports no whitespace error.

## Risks

* **Policy constants could overfit the first benchmark.** Mitigation: express them as a named
  profile policy, use evidence kind rather than `TEST_FILE` logic, test multiple distributions, and
  keep other profiles unrestricted.
* **A hard category limit could hide a legitimately broad change.** Mitigation: retain the
  highest-ranked ordinary items and permit an explicit strong-relevance escape; test multiple
  strong items and scarce alternative categories.
* **Corpus-frequency suppression could remove a meaningful ubiquitous domain term.** Mitigation:
  suppress only its ranking boost, not the evidence itself; preserve other ranking criteria and
  document-frequency boundaries; test rare path/compound terms separately.
* **Relevance filtering could undermine diversity.** Mitigation: establish eligibility before the
  diversity pass and test absent, weak, and eligible preferred layers independently. Diagnostics
  must distinguish no candidate from a rejected candidate.
* **Raw candidates, duplicates, and selection decisions could yield inconsistent totals.**
  Mitigation: define raw versus unique counts explicitly, retain duplicate decisions/accounting,
  and assert reconciliation invariants in unit and engine tests.
* **Additive response fields and changed digest inputs could surprise consumers.** Mitigation:
  retain every current endpoint and field, use additive immutable diagnostics, protect JSON shape,
  and make ranking/plan versions explicit.
* **Existing tests may assume every under-budget candidate is selected.** Mitigation: update only
  assertions governed by the new Engineering Story policy; demonstrate unchanged behavior through
  unrestricted profiles rather than weakening coverage.

No remaining risk requires human clarification before implementation.

## Validation Checklist

* [ ] A versioned precision policy is owned and explained by Context Intelligence.
* [ ] Existing profiles use explicit compatible defaults; only the intended profile changes.
* [ ] Ranking uses deterministic candidate-frequency information without project-specific
  stopwords.
* [ ] Meaningful domain/path terms remain discriminating and current-analysis behavior remains
  compatible.
* [ ] Ranking and Context Plan policy versions are incremented explicitly.
* [ ] Ordinary selection enforces minimum relevance and a generic evidence-kind concentration
  allowance.
* [ ] Strongly relevant evidence can exceed the ordinary allowance deterministically.
* [ ] Diversity, global item/token budgets, reference deduplication, ordering, and token accounting
  remain correct.
* [ ] Decisions distinguish diversity, rank, strong overflow, relevance, concentration, item budget,
  token budget, and duplicate handling.
* [ ] Candidate/selected distributions are exposed by layer and kind.
* [ ] Every preferred layer reports candidate availability truthfully.
* [ ] Missing layers are never represented as selector exclusions or fabricated evidence.
* [ ] New diagnostics and decisions participate in the context digest.
* [ ] Existing GET and POST Engineering Story Context contracts remain compatible.
* [ ] Provenance, originating files, collector metadata, related references, and score explanations
  remain intact.
* [ ] Story-0009-shaped, empty, sparse, equal-score, duplicate, and repeated-run fixtures pass.
* [ ] No collector, source-content, symbol, dependency, embedding, persistence, frontend, agent,
  project-resolution, or workflow change is introduced.
* [ ] Focused tests pass.
* [ ] Complete `./mvnw verify` passes with the configured JaCoCo threshold.
* [ ] Authenticated SonarQube analysis passes with no new unresolved issue.
* [ ] `git diff --check` passes.

## Recommendation

Ready for implementation

This is a technical recommendation only. It does not approve this Implementation Plan or authorize
implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
