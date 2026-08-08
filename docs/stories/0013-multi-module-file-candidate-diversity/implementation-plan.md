# Implementation Plan

## Overview

Story 0013 will correct file-candidate production inside `RepositoryStructureCollector` without
changing the Repository Context pipeline, public API, scanner, ranker, selector, or persistence.

The implementation will replace repository-root-only source detection with normalized
path-segment-aware root matching. It will classify each scanned path once as source, test,
configuration, or ineligible, sort each eligible category independently using the existing
Story-path relevance signal and stable path tie-breaking, then interleave the non-empty categories
deterministically until the existing total 40-file candidate bound is reached.

Round-robin allocation is preferred over fixed quotas because it guarantees representation when
multiple categories exist and automatically redistributes unused capacity when a category is
sparse or absent. It remains collector-level candidate shaping only. Story 0012's ranker and
selector continue to own final relevance, concentration, diversity, and budgets.

No new Context Profile field or candidate-allocation abstraction is planned initially. The policy
is generic, fixed for repository-structure discovery, and small enough to remain private to the
collector. The collector version will advance explicitly because its candidate semantics change.

## Planned Changes

1. **Introduce normalized path-boundary matching in `RepositoryStructureCollector`.**

   Replace `startsWith`-only source-root detection and permissive test-root substring checks with a
   shared private helper that recognizes a configured root only when it begins at the repository
   root or after a `/` path boundary and ends at a boundary or continues with `/`.

   The helper will operate on scanner-normalized `/`-separated relative paths. It will recognize
   paths such as `src/main/java/...` and `backend/src/main/java/...` while rejecting near matches
   such as `examples/not-src/main/java-copy/...` or directory names that merely contain a root
   token.

   Use the same boundary logic for aggregate source/test directory evidence so aggregates and
   individual classification do not disagree.

2. **Make file classification explicit and mutually exclusive.**

   Classify paths by their location rather than filename conventions:

   * test-root membership produces `TEST_FILE` when the extension is supported;
   * otherwise production-source-root membership produces `SOURCE_FILE` when supported;
   * otherwise a supported configuration filename produces `CONFIG_FILE`;
   * all other files remain ineligible for individual file evidence.

   Test classification will be evaluated before source classification as an explicit defensive
   precedence rule. A production file named `PaymentTest.java` remains `SOURCE_FILE` because its
   path is not under a configured test root. Configuration detection remains filename-based and
   content-free.

3. **Build and sort independent candidate buckets.**

   Replace the single `fileEvidence` list with one deterministic bucket for each supported evidence
   kind. Create candidates through the existing `EvidenceFactory` so references, originating paths,
   repository location, extraction metadata, summary bounding, and token estimates remain
   unchanged.

   Within each bucket, order candidates by:

   1. descending count of normalized Story terms found in the lowercase originating path;
   2. ascending originating path as the deterministic tie-breaker.

   Retain the existing lexical signal only for deciding which candidates survive the collector
   bound. Do not assign relevance scores or reproduce corpus-aware ranker behavior.

4. **Apply deterministic round-robin candidate allocation.**

   Iterate non-empty buckets in the stable category order:

   ```text
   SOURCE_FILE → TEST_FILE → CONFIG_FILE
   ```

   Take at most one next candidate from each bucket per cycle until:

   * 40 candidates have been emitted; or
   * every bucket is exhausted.

   This gives each available category equal opportunity before another item of the same kind is
   added. When a bucket is exhausted, subsequent cycles naturally redistribute its capacity among
   remaining categories. When only one category exists, it may use the complete bound. The output
   remains stable for identical scans and Stories.

   Keep `MAX_FILE_EVIDENCE_ITEMS` as the authoritative total collector bound. Do not add runtime
   configuration or Context Profile fields in this Story because no profile-specific allocation
   requirement has been demonstrated.

5. **Version and explain changed collector semantics.**

   Advance `RepositoryStructureCollector.collectorVersion()` from `v1` to `v2`. All produced
   structure evidence will continue exposing the same collector ID and source type, with the new
   version making candidate-semantic changes traceable.

   No new Repository Context warning or selection reason is required: existing diagnostics already
   expose candidate and selected counts, while ranker/selector reasons remain authoritative.

6. **Extend collector tests for multi-module classification and allocation.**

   Add focused fixtures covering:

   * supported root-level and module-prefixed source roots;
   * module-prefixed test roots;
   * a production file whose name includes `Test`;
   * root and module configuration files;
   * negative path-boundary near matches;
   * mixed candidates above 40 items;
   * round-robin representation and stable order;
   * deterministic capacity redistribution for sparse and single-category inputs;
   * unchanged references, originating paths, and `repository-structure` provenance;
   * aggregate source/test directory evidence for module-prefixed layouts.

   Update existing collector-version assertions from `v1` to `v2` where they refer to this
   collector. Preserve all current root-layout, empty-source, workspace-failure, aggregate, module,
   and file-limit tests.

7. **Add an engine-level composition regression.**

   Extend `RepositoryContextServiceTest` with an actual or equivalently faithful mixed
   repository-structure candidate fixture that reaches the normal ranker and Story 0012 selector.
   Assert that:

   * candidate diagnostics include `SOURCE_FILE`, `TEST_FILE`, and `CONFIG_FILE`;
   * selected evidence retains relevant representatives where they satisfy relevance and budget;
   * selected counts equal evidence counts;
   * no existing selection reason, token-budget, provenance, or digest contract is weakened.

   The test must not require a new production API or duplicate the full scanner integration already
   covered by collector tests.

8. **Protect the Engineering Story Context response.**

   Update `EngineeringStoryContextControllerWebMvcTest` only where current fixtures or collector
   version assertions require it. Preserve GET and POST inputs and the existing response shape.
   Candidate diversity should be observable through the diagnostics already serialized; no new
   fields are planned.

9. **Perform Documentation Reconciliation before Code Review.**

   Inspect `README.md`, `docs/architecture.md`, and the Phase 1 roadmap wording after implementation.
   Update only canonical statements that materially need to mention multi-module file-candidate
   discovery or the explicit distinction between candidate shaping and final selection.

   If the existing description—bounded ranked source/test/configuration paths—remains accurate and
   sufficiently complete, record `Documentation update: Not required` with that evidence in the
   Implementation Report. Do not add speculative Story 0014 content, a changelog, or unrelated
   documentation cleanup.

10. **Run focused and repository-wide quality validation.**

    Run targeted collector, ranker, selector, Repository Context, and Web MVC tests first. Then run
    the complete Maven verification, JaCoCo rule, authenticated pinned SonarQube scanner with
    Quality Gate wait, and `git diff --check`.

    Do not weaken tests or correct unrelated Sonar findings without explicit human authorization.

## Files to Modify

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`
  — segment-aware root detection, explicit classification, per-kind sorting, deterministic
  round-robin allocation, aggregate root consistency, and collector version `v2`.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollectorTest.java`
  — multi-module, mixed-category, boundary, redistribution, determinism, provenance, and version
  regression coverage.
* `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextServiceTest.java`
  — composition of representative file candidates with Story 0012 ranking, selection,
  diagnostics, budgets, and digest behavior.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  — only if fixture/version expectations require adaptation; preserve transport and serialization.
* `README.md`, `docs/architecture.md`, or `docs/roadmap.md`
  — only if Documentation Reconciliation establishes that canonical capability wording is
  materially incomplete.

## Files to Create

None expected.

If implementation shows that round-robin allocation cannot remain clear and focused as private
collector methods, one package-private immutable helper under
`repositorycontext.collector` may be introduced. This is an allowed bounded deviation, not a reason
to create a general selection framework.

## Dependencies

The implementation reuses:

* `SecureRepositoryScanner` normalized relative paths;
* `RepositoryFile` scan metadata;
* `ContextRequest` Story objective;
* `EvidenceFactory` evidence/provenance/token construction;
* `RepositoryEvidence` kinds and originating-file provenance;
* Story 0012 `DeterministicEvidenceRanker` and `BudgetedDiverseEvidenceSelector` behavior;
* `RepositoryContextDiagnostics` candidate and selected distributions;
* existing Spring/Jackson serialization, JUnit, Mockito, Maven, JaCoCo, and SonarQube tooling.

No new external dependency, service, database entity, migration, Docker component, frontend
dependency, or AI Engine change is required.

Implementation order matters: path classification must be corrected before bucket allocation tests
are meaningful; bucket production must be stable before engine-level composition assertions are
updated; Documentation Reconciliation and full quality validation occur after implementation and
before Code Review.

## Test Plan

1. **Repository Structure Collector tests**

   * root and module source paths map to `SOURCE_FILE` — AC-1;
   * root and module tests map to `TEST_FILE`, while a production `*Test.java` remains source —
     AC-2;
   * root/module configuration candidates survive mixed volume — AC-3;
   * more than 40 mixed files produce bounded representation through round-robin — AC-4 and AC-10;
   * Story-relevant candidates are retained first within each bucket — AC-5;
   * references, provenance, token estimates, and collector `v2` remain correct — AC-6 and AC-11;
   * scanner invocation, empty source, unavailable workspace, aggregate evidence, and excluded
     paths remain compatible — AC-8, AC-9, and AC-11;
   * repeated identical inputs produce identical candidate order — AC-4 and AC-10.

2. **Ranker/selector composition tests**

   * mixed source/test/config candidates reach the existing ranker and selector;
   * Story 0012 minimum relevance and category concentration remain authoritative;
   * candidates are not treated as automatically selected;
   * used tokens, decisions, and selected order remain correct — AC-7.

3. **Repository Context service tests**

   * candidate and selected distributions reconcile with actual evidence;
   * `SOURCE_FILE`, `TEST_FILE`, and `CONFIG_FILE` candidates exist in a multi-module-shaped fixture;
   * selection reasons, provenance, warnings, and digest remain valid — AC-10, AC-12, and AC-14.

4. **Engineering Story API tests**

   * GET and POST request contracts remain unchanged;
   * existing diagnostics serialize without new fields;
   * changed collector metadata is reflected only where already exposed — AC-6 and AC-12.

5. **Validation commands**

   ```text
   ./mvnw -Dtest=RepositoryStructureCollectorTest,DeterministicEvidenceRankerTest,BudgetedDiverseEvidenceSelectorTest,RepositoryContextServiceTest,EngineeringStoryContextControllerWebMvcTest test
   ./mvnw verify
   ./mvnw sonar:sonar -Dsonar.qualitygate.wait=true
   git diff --check
   ```

   The Sonar token must be loaded from the existing ignored local `.env`; it must never be written
   to Git, logs, Story artifacts, or command output.

Expected success conditions:

* all focused and complete backend tests pass;
* JaCoCo's existing 80% bundle line-coverage rule passes;
* SonarQube Quality Gate passes with no new unresolved issue;
* no diff whitespace error exists;
* no content-reading, persistence, API-input, frontend, AI, or workflow change appears in the diff.

## Risks

### Round-robin order may be mistaken for final relevance

The output list interleaves kinds for representation, but the ranker will later reorder candidates
by score. Mitigation: keep collector reasons as `COLLECTED_NOT_RANKED`, avoid relevance scores in the
collector, and verify ranker/selector composition separately.

### Equal allocation may retain weak categories

Candidate representation can include configuration or test paths with weak Story relevance.
Mitigation: candidate production is intentionally broader than final selection; Story 0012 minimum
relevance and kind concentration remain the final filter. The total remains bounded at 40.

### Segment matching can regress supported layouts

An overly strict helper could reject root-level files or Python/TypeScript layouts. Mitigation: table
tests for every existing configured root plus module-prefixed and near-match negative cases.

### Existing tests may encode obsolete candidate ordering

Changing a single sorted list to round-robin changes order by design. Mitigation: update assertions
to the new deterministic contract while preserving semantic checks for relevance, provenance, and
limits; do not weaken unrelated tests.

### Documentation can expand scope

The new workflow requires reconciliation, but broad documentation rewriting would distract from the
critical collector fix. Mitigation: inspect only canonical capability descriptions and explicitly
record when no update is necessary.

No risk requires additional human clarification before implementation.

## Validation Checklist

* [ ] Module-prefixed supported production roots produce `SOURCE_FILE` candidates.
* [ ] Root-level supported production roots remain compatible.
* [ ] Module/root test roots produce `TEST_FILE` candidates.
* [ ] Production files named like tests remain source evidence by path ownership.
* [ ] Root/module supported configuration files produce `CONFIG_FILE` candidates.
* [ ] Path-root matching is segment aware and rejects near matches.
* [ ] Candidate buckets are sorted by Story-path match count and stable path tie-breaker.
* [ ] Round-robin allocation represents every non-empty category before repeating a kind.
* [ ] Output never exceeds 40 file candidates.
* [ ] Sparse and single-category inputs deterministically reuse available capacity.
* [ ] References, originating files, repository location, token estimates, and collector metadata
  remain intact.
* [ ] Collector version changes explicitly to `v2`.
* [ ] Aggregate source/test directory evidence recognizes module-prefixed roots consistently.
* [ ] Story 0012 ranker, selector, concentration, diagnostics, and budgets remain authoritative.
* [ ] Candidate/selected diagnostic counts exactly reconcile.
* [ ] GET and POST Engineering Story Context inputs and response shape remain compatible.
* [ ] Scanner/workspace behavior is unchanged and no file content is read.
* [ ] Focused tests pass.
* [ ] Complete Maven verification and JaCoCo rule pass.
* [ ] Authenticated SonarQube Quality Gate passes with no new issue.
* [ ] Documentation impact is explicitly reconciled in the Implementation Report.
* [ ] No new dependency, database, frontend, AI Engine, Docker, agent, or workflow change exists.
* [ ] `git diff --check` passes.

## Recommendation

Ready for implementation

The implementation is bounded to the collector's existing ownership, uses a simple deterministic
allocation rule, preserves the Story 0012 ranking/selection boundary, requires no new architecture
or external dependency, and has direct regression seams for every critical behavior.

This recommendation is technical only. It does not approve the Implementation Plan or authorize
implementation.

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
