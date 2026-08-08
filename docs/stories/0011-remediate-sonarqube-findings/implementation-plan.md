# Implementation Plan

## Overview

Story 0011 will remediate the authenticated 152-issue SonarQube baseline in reviewable batches while
preserving the Java Core's behavior and architecture. The plan separates behavior-sensitive
production refactors from mechanical cleanup, validates each risky component with focused tests,
then uses the complete Maven/JaCoCo lifecycle and authenticated Sonar issue API as the final source
of completion evidence.

The first production change removes the one-task `ExecutorService` from `CollectorRunner` in favor
of an explicitly cancellable virtual-thread task, avoiding both the reported resource and the
unbounded `ExecutorService.close()` semantics. Regex findings will be addressed by bounded parsing
and patterns rather than suppressions. Complexity refactors will extract existing responsibilities
inside their owning modules. Test findings will be corrected without deleting or weakening tests.

No Quality Profile, Quality Gate, API, persistence, frontend, Docker, or dependency configuration
will change.

## Planned Changes

### 1. Preserve an immutable baseline and remediation map

Before production edits, query the authenticated Sonar issue API and retain a disposable local
snapshot outside Git. Build a working map keyed by rule, component, line, severity, and main/test
scope.

Use the Story baseline as the acceptance reference:

* 152 unresolved issues;
* 1 bug and 151 code smells;
* 1 Blocker, 23 Critical, 64 Major, 52 Minor, and 12 Info;
* 78 production and 74 test issues;
* 74 affected files.

After every implementation batch, run focused tests and a scanner-only analysis when useful. The
working snapshot and intermediate API output remain under `/tmp` or another non-repository path;
only the final non-secret counts belong in Story artifacts.

### 2. Replace the one-task executor with bounded virtual-thread execution

Modify `CollectorRunner` before other production refactors.

Replace `Executors.newVirtualThreadPerTaskExecutor()` with one explicitly owned virtual thread
running a `FutureTask<CollectionResult>` or equivalent JDK primitive. Continue to use the configured
collector timeout through timed `get`. In `finally`, cancel/interrupt unfinished work so cooperative
collector tasks terminate promptly.

Preserve the current exception contract:

* timeout → `NonFatalCollectionException` with code `COLLECTOR_TIMEOUT`;
* caller interruption → restore caller interrupt status and throw `IllegalStateException` with the
  original cause;
* collector runtime failure → rethrow that runtime exception directly;
* collector checked failure wrapped by task execution → `IllegalStateException` with the original
  cause;
* success → return the exact collector result.

This removes the `ExecutorService` resource rather than converting it mechanically to
try-with-resources. Java 21 `ExecutorService.close()` may wait indefinitely for a task that ignores
interruption, which would undermine the existing bounded timeout contract.

Extend `CollectorRunnerTest` with deterministic latches/futures instead of `Thread.sleep` to verify:

* successful result;
* timeout and worker interruption/cancellation;
* direct runtime propagation;
* checked failure wrapping when constructible through the task boundary;
* caller interruption and restored interrupt flag;
* no lingering cooperative worker after completion or failure.

Run this test class independently before proceeding. Re-scan to confirm both `S2095` and the test's
`S2925` issue are closed without suppression.

### 3. Make repository-text parsing bounded and regression-tested

Address regex rules `S8786`, `S5843`, `S6326`, and `S6353` in component groups.

#### Build descriptors

In `BuildCollector`, replace broad DOTALL `.*?` tag traversal with bounded tag-content matching,
small deterministic tag/block extraction helpers, or possessive/negated classes where semantics are
clear. Compile every reusable pattern once. Preserve Maven dependency/plugin/module/version Facts,
default Maven plugin group behavior, Gradle coordinates, Java version detection, and malformed/
unsupported warnings.

Add focused cases for multiline Maven blocks, optional plugin group IDs, Gradle string/call forms,
missing tags, malformed descriptors, and long irrelevant content. Use timeout assertions only for
the adversarial cases, with thresholds loose enough to avoid machine-speed flakiness.

#### Spring metadata

In `SpringCollector`, separate property-tag parsing from dependency-coordinate version parsing.
Replace broad negative-digit matching with bounded, readable patterns or deterministic substring
parsing. Preserve version precedence and type/stereotype detection. Update concise character classes
only where they express the same accepted language.

#### Docker and Compose

In `DockerCollector`, bound instruction arguments to a line, precompile Compose-entry patterns, and
replace comment stripping with non-backtracking line logic where appropriate. Preserve service,
volume, healthcheck, stage, user, exposed-port, and file-presence Facts.

#### Documentation and paths

In `DocumentationCollector`, use line-bounded heading extraction and deterministic sensitive-heading
redaction. Preserve title truncation and all documentation/ADR/API/architecture classifications.

In `CommitDiffContextBuilder`, replace path-exclusion backtracking with normalized suffix/segment
checks or bounded patterns, preserving generated/vendor/binary exclusions and changed-file evidence.

Add or extend collector/diff tests for representative accepted, rejected, malformed, redacted, and
long inputs. Run the affected test classes after each parser family.

### 4. Reduce complexity inside existing component ownership

Refactor only methods reported by active complexity/control-flow rules and closely coupled helpers.

* `DockerCollector` — extract Compose section transition and entry emission from line iteration.
* `DocumentationCollector` — separate repository-path classification, document metadata creation,
  and Fact emission.
* `SecureRepositoryScanner` — extract directory-limit/read/sort decisions and child dispatch while
  preserving early termination and warning-once behavior.
* `TestStructureCollector` — use a small internal counter/state object or named classification
  helpers for source/resource/unit/integration accumulation.
* `BudgetedDiverseEvidenceSelector` — extract diversity-first selection and candidate insertion into
  a local selection state while retaining exact order, deduplication, represented layers, budgets,
  token accounting, and decisions.
* `SpringCollector` and `CommitDiffEvidenceCollector` — replace multi-continue loops with predicate or
  helper-based control flow without changing result ordering.
* `GitWorkspaceManager` — extract one synchronization attempt and retry-after-reclone helper,
  preserving locking, deletion safety, first-failure suppression, and Git command order.
* `CommandLineGitHistoryProvider` — replace loop-counter mutation with a bounded token cursor/helper
  that consumes one status plus one or two paths; extract new-path selection from the nested ternary.

Do not introduce cross-collector services or a generic complexity abstraction. Helpers remain
private or package-local to the owning component unless tests require a narrower seam.

Before each refactor, preserve existing behavior in focused tests. Add missing cases for scanner
limits/warnings, Compose root-section transitions, documentation classification, selection ordering
under both budgets, Git rename/copy/delete/add parsing, malformed token streams, workspace retry, and
suppressed failures.

### 5. Make evidence construction cohesive without changing evidence contracts

Introduce an internal immutable request record in `EvidenceFactory` (for example
`EvidenceDraft`/`EvidenceInput`) grouping the current 11 construction parameters by their actual
evidence concept:

* collector metadata;
* layer, kind, reference, and summary;
* occurrence time and related references;
* repository location, originating file, and identifier;
* maximum summary characters.

Change `EvidenceFactory.create` to accept that cohesive input. Update all Repository Context
collectors to construct it with named record components or focused factory methods. Do not change
`RepositoryEvidence`, provenance, extraction metadata, estimated-token formula, bounded summary, or
ranking defaults.

Simplify `ProjectKnowledgeContextCollector`'s eight-parameter helper by accepting the same input
concept or separate knowledge snapshot plus request, whichever produces the smallest readable
contract.

Remove the unused `CollectorLimits` field and constructor parameter from
`RepositoryStructureCollector` only after confirming Spring wiring and all tests use no required
side effect. Update tests and instantiations accordingly.

Run all Repository Context collector, selector, intelligence/ranker, service, adapter, and controller
tests. Assert exact evidence references, provenance, ordering, scores, decisions, and token usage.

### 6. Correct deprecation metadata and local maintainability findings

Retain the legacy `AIEngineClient.submit(AiTaskSubmissionRequest)` overload because it is a public
Java interface compatibility/error boundary. Add matching `@Deprecated(since = ..., forRemoval =
...)` metadata and JavaDoc `@deprecated` guidance to the interface and implementation. The planned
default is `forRemoval = false` unless repository history provides an approved removal version.
Preserve the current explicit rejection behavior and test it.

Remove the unused private `storyDescription` parameter from
`RepositoryContextAdapter.synthesizeAnalysisContext`; continue passing the full Story to
`createIntentDefinition` and `createGuidance`.

Resolve remaining small production rules with local changes:

* private constants for component-owned repeated literals;
* existing domain constants/enums where they already define the value;
* direct boolean returns, method references, separate declarations, and unused members/imports;
* deprecation Javadoc/annotation completion;
* no global constants container and no public signature change solely for Sonar.

Run focused tests for every affected service/client/context component.

### 7. Apply isolated mechanical test remediation

Process test-only rule families separately from production behavior:

* `S5778` — compute fixtures/arguments outside `assertThrows`; keep only the intended service or
  constructor invocation inside the executable;
* `S1128` — remove verified unused imports;
* `S6068`/`S8924` — remove redundant Mockito matchers or use the intended static import while
  preserving valid matcher consistency;
* `S5786` — remove unnecessary `public` modifiers from JUnit classes;
* `S5785` — replace boolean equality assertions with the specific equivalent assertion;
* `S1130` — remove impossible checked-exception declarations;
* `S1612` — use clear method references where behavior is identical.

Do not delete tests, combine unrelated cases, relax assertions, or remove verification. Run each
changed test package, then the complete suite.

### 8. Execute full deterministic and Sonar validation

Run from `backend`:

```text
./mvnw clean verify
```

Confirm:

* all tests pass;
* JaCoCo XML is generated;
* the 80% line-coverage rule passes;
* no test failure is ignored.

Load the private Project Analysis Token from the ignored root `.env` without displaying it, then run:

```text
./mvnw sonar:sonar -Dsonar.qualitygate.wait=true
```

Query the authenticated issue, Quality Gate, and measures APIs. Completion evidence must show:

* scanner `5.7.0.6970`, project `devlog-ai`;
* Quality Gate result;
* zero unresolved baseline issues;
* zero new issues introduced by Story changes;
* bugs, vulnerabilities, hotspots, and code smells;
* coverage and duplicated-lines density;
* before/after counts by type and severity.

If an issue remains because the analyzer does not recognize a behaviorally safe pattern, stop for
human guidance. Do not add suppression or change issue status automatically.

## Files to Modify

The exact set is bounded by the authenticated 74-file issue inventory. Expected production files
include:

* `backend/src/main/java/com/hopeful117/devlogai/collection/collector/CollectorRunner.java`
* collector/parser files: `BuildCollector`, `SpringCollector`, `DockerCollector`,
  `DocumentationCollector`, `SecureRepositoryScanner`, `TestStructureCollector`,
  `RepositoryMetadataCollector`
* Git files: `GitWorkspaceManager`, `CommandLineGitHistoryProvider`, `CommitDiffContextBuilder`
* Repository Context files: `EvidenceFactory`, `ProjectKnowledgeContextCollector`,
  `RepositoryStructureCollector`, `CommitDiffEvidenceCollector`,
  `DeterministicKnowledgeContextCollector`, `DeterministicContextIntelligence`,
  `DeterministicEvidenceRanker`, `BudgetedDiverseEvidenceSelector`
* `RepositoryContextAdapter`
* AI client files: `AIEngineClient`, `RestAIEngineClient`
* local service/contract files reported for constants, imports, declarations, or expressions in
  analysis diagnostics, knowledge selection, projects, decisions, documentation, observations,
  validation, proposals, profiles, and logging
* all test files in the Sonar baseline, with concentrated changes in `CollectorRunnerTest`, collector
  and scanner tests, Git workspace/history tests, Repository Context tests,
  `ProjectContextProviderTest`, knowledge/proposal/AI service tests, and service test classes with
  visibility/import findings
* Story 0011 workflow artifacts for factual implementation and review evidence

No file outside the authenticated issue set should change unless it is a focused regression test or
an unavoidable call site of an approved internal signature refactor.

## Files to Create

None expected in production.

Prefer private nested records/helpers and existing test classes. A new focused test class may be
created only when an affected component currently lacks an appropriate test owner; its purpose and
rule coverage must be documented in the Implementation Report.

## Dependencies

No new external or runtime dependency is required. The implementation uses Java 21 virtual threads,
`FutureTask`/concurrency primitives, JUnit 5, Mockito, the existing Maven/JaCoCo lifecycle, and the
pinned Sonar Maven scanner.

Ordering dependencies are:

1. capture baseline before edits;
2. fix and validate collector execution lifecycle;
3. secure parser/regex behavior with tests;
4. refactor behavior-sensitive control flow;
5. migrate internal evidence construction contracts and call sites;
6. correct local constants/deprecation/small production findings;
7. perform isolated mechanical test cleanup;
8. run full validation and authenticated re-analysis.

The private `SONAR_TOKEN` in the ignored root `.env` is required only for analysis/API verification,
not for compilation or tests.

## Test Plan

### Collector execution lifecycle

Extend `CollectorRunnerTest` with deterministic synchronization to verify success, timeout,
cancellation/interruption, runtime propagation, checked-cause wrapping, caller interruption, and no
cooperative worker remaining after completion. Avoid sleeps.

Coverage: AC-1, AC-6, AC-9.

### Parser and regex safety

Extend the tests owning Build, Spring, Docker, Documentation, TestStructure, secure scanning, and
commit-diff classification. Preserve current accepted/malformed cases and add long adversarial
content for backtracking rules. Use bounded timeout assertions only on the explicit adversarial
cases.

Coverage: AC-2, AC-3, AC-4, AC-9.

### Git and workspace behavior

Add or strengthen cases for first synchronization, retry after Git failure, suppressed original
failure, lock-safe cleanup, explicit/default revisions, add/delete/modify/rename/copy tokens, binary
stats, malformed/truncated token streams, and empty history.

Coverage: AC-2, AC-4, AC-9, AC-11.

### Repository Context invariants

Run and adapt collector, ranking/intelligence, selector, service, adapter, and controller tests.
Assert exact evidence values, provenance, references, ordering, diversity, budgets, token estimates,
selection decisions, Story objective, and guidance.

Coverage: AC-2, AC-4, AC-5, AC-11.

### AI compatibility and local services

Verify current PromptRequest submission, Deliverable generation, legacy submission rejection,
project/service constants, diagnostics statuses, and logging behavior remain unchanged.

Coverage: AC-5, AC-7, AC-11.

### Mechanical test remediation

Run every affected test class after cleanup and confirm exception assertions still isolate exactly
one potentially throwing invocation and Mockito verifications remain equivalent.

Coverage: AC-6, AC-9.

### Full validation

```text
./mvnw clean verify
./mvnw sonar:sonar -Dsonar.qualitygate.wait=true
```

Then query unresolved issues and metrics through authenticated APIs. Expected result is 0 unresolved
issues, no new issue, passing deterministic validation, and an accurately reported Quality Gate.

Coverage: AC-8 through AC-12.

## Risks

### Virtual-thread cancellation remains cooperative

Java interruption cannot forcibly stop a task that ignores it. The proposed one-shot virtual thread
removes the `ExecutorService` resource and preserves bounded caller behavior, but an intentionally
non-cooperative collector could continue running. Mitigation: collectors are controlled internal
implementations; test cooperative cancellation and preserve timeout return semantics rather than
blocking in `close()`.

### Parser regression under security cleanup

Bounded parsing may alter recognized inputs. Mitigation: characterize current outputs first, add
accepted/malformed/adversarial cases, and refactor one parser family at a time.

### Repository Context argument migration

Moving eleven positional parameters into a record could swap provenance fields at call sites.
Mitigation: named record components/factories, compile-time migration of every caller, and exact
evidence/provenance assertions.

### Large diff reviewability

Seventy-four files can obscure defects. Mitigation: keep production risk batches distinct from
mechanical test cleanup, validate each package immediately, and report changes by rule family.

### Compatibility of deprecated API cleanup

Removing the overload would be riskier than documenting it. Mitigation: retain the contract with
complete metadata and test its rejection behavior.

### Sonar issue movement

Line changes can close/reopen or relocate issues, and complexity extraction can create new findings.
Mitigation: compare issue keys/rules/components after batches and require a final zero-unresolved
query, not merely a passing Quality Gate.

## Validation Checklist

* [ ] Authenticated 152-issue baseline captured outside Git.
* [ ] `CollectorRunner` no longer owns an unclosed `ExecutorService`.
* [ ] Success, timeout, cancellation, interruption, and exception behavior are covered.
* [ ] No timing sleep remains in `CollectorRunnerTest`.
* [ ] All reported regexes are bounded or replaced with deterministic parsing.
* [ ] Parser behavior and adversarial long inputs are tested.
* [ ] All reported cognitive-complexity/control-flow findings are resolved inside existing ownership.
* [ ] Git synchronization and change-token parsing contracts remain covered.
* [ ] Evidence construction uses a cohesive internal input without changing evidence/provenance.
* [ ] Repository Context ordering, ranking, selection, diversity, budgets, and decisions are unchanged.
* [ ] Complete Story continues to influence Intent and Guidance.
* [ ] Deprecated AI overload remains compatible and has complete metadata.
* [ ] Repeated literals use appropriately scoped constants.
* [ ] Every `assertThrows` contains only the intended potentially throwing invocation.
* [ ] Mockito and assertion cleanup does not weaken tests.
* [ ] No test is deleted to reduce issue count.
* [ ] No suppression, exclusion, profile/gate change, or administrative issue closure is used.
* [ ] All 375 existing tests plus new focused tests pass.
* [ ] JaCoCo's 80% bundle line-coverage rule passes.
* [ ] Authenticated Sonar analysis completes with the pinned scanner.
* [ ] Final unresolved baseline count is zero and no new issue exists.
* [ ] Quality Gate and all required metrics are reported accurately.
* [ ] No API, persistence, frontend, Docker, port, or workflow contract changes.
* [ ] No token or secret appears in tracked files, diffs, logs, or Story artifacts.
* [ ] Implementation Report records rule-family and before/after evidence.

## Recommendation

Ready for implementation

This is a technical recommendation only. It does not approve the Implementation Plan or authorize
Implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
