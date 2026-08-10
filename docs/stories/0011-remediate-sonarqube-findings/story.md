# Story 0011 — Remediate the SonarQube Quality Findings

## Story ID
0011

## Title
Remediate the current SonarQube bug and code-smell baseline

## Status
Completed

## Priority
High

## Date
2026-08-08

---

## User Story

As a developer evolving DevLog from the quality baseline established by Story 0010,
I want the current SonarQube bug and code-smell findings to be corrected safely,
So that future Engineering Stories begin from clean static-analysis results without hiding defects
or changing established product behavior.

---

## Context

Story 0010 made the backend SonarQube workflow reproducible, authenticated, and explicit. The
official scanner is pinned, Maven tests and JaCoCo remain mandatory, and the canonical analysis
waits for the SonarQube Quality Gate.

The first authenticated baseline passed its configured Quality Gate but exposed active findings
that predate this Story:

* 152 unresolved issues across 74 Java files;
* 1 `BUG` and 151 `CODE_SMELL` issues;
* 1 Blocker, 23 Critical, 64 Major, 52 Minor, and 12 Info issues;
* 78 issues in production sources and 74 issues in tests;
* an estimated aggregate Sonar remediation effort of 1,023 minutes;
* 0 vulnerabilities and 0 security hotspots;
* 86.7% Sonar coverage and 0.0% duplicated lines.

The Blocker is rule `java:S2095` in `CollectorRunner`: its `ExecutorService` is not guaranteed to be
closed on every execution path. The code-smell baseline spans mechanical cleanup, test readability,
duplicated literals, regular-expression safety, control-flow complexity, deprecated API contracts,
large parameter lists, and several smaller maintainability rules.

The largest rule families are:

| Rule | Count | Main concern |
| --- | ---: | --- |
| `java:S5778` | 29 | Test lambdas contain multiple potentially throwing invocations |
| `java:S1128` | 27 | Unused imports |
| `java:S1192` | 18 | Duplicated string literals |
| `java:S8786` | 13 | Regular expressions with super-linear backtracking risk |
| `java:S6068` | 10 | Redundant Mockito `eq(...)` calls |
| `java:S5786` | 10 | Unnecessary `public` modifiers in tests |
| `java:S5785` | 5 | Assertions should use `assertEquals` |
| `java:S3776` | 5 | Cognitive complexity above the configured limit |
| `java:S135` | 4 | Excessive `break`/`continue` statements |
| `java:S6353` | 4 | Non-concise regular-expression digit classes |
| Other rules | 27 | Loop counters, parameters, deprecation metadata, nested control flow, unused members, and related cleanup |

Passing the Quality Gate does not mean these findings are absent. Story 0011 exists to remove the
known baseline rather than normalizing it as permanent debt.

---

## Objective

Correct every active SonarQube issue reported for backend project `devlog-ai` by the authenticated
baseline established in Story 0010, while preserving current behavior, public contracts,
deterministic processing, tests, coverage, and architectural boundaries.

The completed Story must leave the backend with:

* no unresolved issue from the 152-issue baseline;
* no newly introduced Sonar issue;
* a passing Maven build and complete test suite;
* the existing JaCoCo coverage rule still satisfied;
* an authenticated Sonar analysis and truthful Quality Gate result;
* no issue dismissed, suppressed, or marked false-positive merely to reduce the count.

---

## Acceptance Criteria

### AC-1: The resource-management bug is corrected first

The `java:S2095` Blocker in `CollectorRunner` must be corrected so its executor is reliably shut down
on successful execution, task failure, interruption, timeout, and unexpected exceptions.

The correction must:

* preserve collector ordering, timeout, warning, interruption, and result semantics;
* avoid leaking executor threads;
* restore the current thread's interrupted status where the existing contract requires it;
* include focused tests for relevant lifecycle and failure paths;
* be confirmed closed by the next Sonar analysis rather than only hidden through suppression.

### AC-2: All Critical and Major production findings are remediated safely

Production findings involving duplicated literals, regex backtracking, cognitive complexity,
control flow, parameter counts, nested `try` blocks, deprecated contracts, and related rules must be
resolved without changing externally visible behavior.

Repository Analysis and Implementation Planning must determine the smallest safe refactor per rule
family. Refactors must preserve:

* deterministic collector output;
* evidence references, summaries, ordering, ranking, and budgets;
* existing API request/response/error contracts;
* Git and workspace behavior;
* callback, retry, timeout, and interruption semantics;
* backward compatibility of intentionally retained deprecated APIs.

### AC-3: Regular-expression findings retain matching behavior

All findings under `java:S8786`, `java:S5843`, `java:S6326`, and `java:S6353` must be addressed with
bounded, non-pathological expressions or simpler deterministic parsing.

For every affected regex family:

* existing accepted and rejected inputs must remain covered;
* representative long/adversarial input must complete within a bounded test timeout where the rule
  concerns backtracking complexity;
* parsing must not broaden repository scanning, file classification, version detection, or evidence
  extraction unintentionally;
* suppressing the rule is not an acceptable substitute for a safe implementation.

### AC-4: Complexity reductions preserve responsibilities and outputs

Methods reported by `java:S3776`, `java:S135`, `java:S127`, `java:S1141`, or nested-expression rules
must be simplified through small, named responsibilities rather than cosmetic rearrangement.

The resulting code must:

* remain readable and deterministic;
* avoid new cross-module dependencies;
* preserve current return values, warnings, exceptions, ordering, and side effects;
* reuse existing value objects and abstractions where appropriate;
* avoid a broad architecture refactor solely to satisfy a metric.

### AC-5: Duplicated literals and large signatures are corrected coherently

`java:S1192` duplicated literals must be replaced by appropriately scoped constants or existing
domain identifiers. Constants must remain owned by the component or domain that defines their
meaning; a generic global constants container must not be introduced.

`java:S107` large signatures must be assessed against existing records/value objects. If a parameter
object is introduced, it must express an actual cohesive concept, preserve current call behavior,
and remain internal unless a public contract change is explicitly justified and approved.

### AC-6: Test-source findings are remediated without weakening tests

All test findings—including `java:S5778`, `java:S1128`, `java:S6068`, `java:S5786`, `java:S5785`,
`java:S2925`, and related rules—must be corrected while retaining or improving behavioral coverage.

The changes must:

* keep assertions equivalent or stronger;
* preserve the exact operation under `assertThrows`;
* replace timing sleeps with deterministic synchronization or polling where required;
* remove unused imports and redundant matchers without weakening Mockito verification;
* avoid deleting tests merely to eliminate findings;
* keep test intent legible.

### AC-7: Deprecation and cleanup findings remain contract-safe

Deprecated APIs reported by `java:S1123`, `java:S1133`, and `java:S6355` must receive coherent
Javadoc and annotation metadata, or be removed only when repository evidence proves they have no
supported consumers and removal is compatible with the current contract.

Unused members, parameters, imports, and unreachable or obsolete code may be removed only after
their actual usage is verified. Sonar remediation must not silently break source or binary
compatibility.

### AC-8: Sonar findings are fixed, not administratively hidden

The Story must not obtain a clean result by:

* weakening or changing the active Quality Profile;
* changing the Quality Gate to ignore findings;
* marking issues `Won't Fix`, `Accepted`, or `False Positive` without demonstrated analyzer error and
  explicit human approval;
* adding broad `NOSONAR`, `@SuppressWarnings`, exclusions, or generated-code classifications;
* excluding affected source or test paths from analysis;
* reducing test execution or coverage requirements.

If a genuine false positive is discovered, implementation must stop for human guidance before
changing issue status or adding a narrowly scoped suppression.

### AC-9: Deterministic validation remains healthy

The completed implementation must run:

* backend compilation;
* the complete backend test suite;
* JaCoCo report generation;
* the existing 80% bundle line-coverage check.

All existing tests must pass. New focused tests must cover changed behavior where a refactor affects
resource lifecycle, regex parsing, control flow, timeouts, or other observable behavior.

### AC-10: The authenticated Sonar baseline is clean and traceable

After deterministic validation, run the canonical authenticated Sonar command introduced by Story
0010 and wait for processing.

Validation must report:

* project key and scanner version;
* Quality Gate status;
* unresolved issue total and breakdown by type/severity;
* bugs, vulnerabilities, security hotspots, and code smells;
* coverage and duplicated lines;
* comparison with the 152-issue Story baseline.

Completion requires zero unresolved issues from the baseline and zero new issues introduced by this
Story. Any remaining issue must be reported and prevents the Story from being represented as fully
complete unless the human explicitly approves a revised scope.

### AC-11: Public behavior and architecture remain stable

The remediation must not change:

* API paths, payloads, response models, or standardized error contracts;
* database schema, migrations, or persisted semantics;
* Repository Context profiles, evidence scoring, selection, diversity, token budgets, or provenance;
* Engineering Story Context GET/POST behavior;
* AI task, Proposal, validation, Insight, or Deliverable semantics;
* Docker services, ports, health checks, or frontend behavior;
* Human Approval workflow semantics.

Any finding that cannot be fixed without one of these changes must be isolated and returned for
human decision instead of being included silently.

### AC-12: Changes remain reviewable despite the baseline size

Implementation must be organized into coherent rule families or component batches so production
behavior changes can be reviewed separately from mechanical test cleanup.

The Implementation Report and Code Review must identify:

* affected rule families;
* production versus test changes;
* focused validation for behavior-sensitive refactors;
* any issue whose remediation differed materially from Sonar's suggested fix;
* the final before/after issue counts.

---

## Scope

### In Scope

* The 1 active Sonar `BUG` and 151 active `CODE_SMELL` issues reported for project `devlog-ai` after
  Story 0010.
* The 74 affected Java production and test files, limited to changes required by those findings.
* Resource lifecycle correction in `CollectorRunner`.
* Safe regex and deterministic parsing remediation.
* Complexity and control-flow refactoring required by active rules.
* Cohesive constants, parameter objects, and deprecation metadata where justified.
* Mechanical test cleanup and deterministic replacement of timing-based tests.
* Focused regression tests for behavior-sensitive changes.
* Complete Maven/JaCoCo validation and authenticated Sonar re-analysis.
* Engineering artifacts recording rule-family and before/after evidence.

### Out of Scope

* Changing SonarQube Quality Profiles, Quality Gates, rules, severity configuration, or server
  administration.
* Blanket suppressions, exclusions, or administrative closure of findings.
* Remediating future findings unrelated to code changed by this Story without renewed scope.
* New product features or UI behavior.
* API, database, migration, Repository Context policy, AI workflow, or Docker topology changes.
* Upgrading Java, Spring Boot, Maven, JaCoCo, SonarQube, or unrelated dependencies.
* Frontend or AI Engine quality remediation; the current Sonar project analyzes the Java backend.
* General formatting, renaming, or architectural cleanup not required by an active issue.
* Raising coverage thresholds or redesigning the CI/Sonar infrastructure established by Story 0010.

---

## Impacted Components

Repository Analysis must confirm the exact file set from the authenticated issue API. The baseline
currently affects:

* collection execution and collectors, especially `CollectorRunner`, `BuildCollector`,
  `SpringCollector`, `DockerCollector`, `DocumentationCollector`, and repository scanning;
* Git workspace/history utilities;
* knowledge selection, project context, and Repository Context collectors/ranking/selection;
* selected project, decision, documentation, observation, validation, proposal, and AI client
  contracts or services;
* backend tests across collection, project context, repository context, knowledge selection,
  proposals, services, controllers, and shared behavior;
* Story 0011 engineering artifacts and authenticated Sonar validation evidence.

No frontend, Python AI Engine, Flyway migration, or Docker configuration change is expected.

---

## Architectural Ownership and Boundaries

* Each production package continues to own its domain constants, parsing, control flow, and resource
  lifecycle.
* Test cleanup remains in the test suite and must not drive production-only abstractions without a
  genuine domain need.
* Deterministic collectors and Repository Context components must remain deterministic.
* SonarQube identifies candidate defects and maintainability risks; repository behavior, tests,
  accepted ADRs, and human-approved Story scope remain authoritative.
* Maven and JaCoCo continue to own deterministic build/test/coverage validation.
* SonarQube continues to own rule evaluation and Quality Gate calculation.
* The human remains the authority for any proposed false-positive classification, suppression,
  public-contract change, or scope revision.

No new ADR is expected for behavior-preserving internal remediation. Repository Analysis must stop
and identify the conflict if a finding requires a new architectural decision.

---

## Risks

### Resource lifecycle regression

Closing the executor at the wrong time could cancel valid collector work, alter timeout handling, or
leave threads alive after exceptional paths. Focused lifecycle and interruption tests are required.

### Regex semantic drift

Simplifying expressions may change accepted repository metadata, dependency versions, file paths, or
classification behavior. Existing examples and adversarial long inputs must be tested before and
after each regex family.

### Broad mechanical-change risk

Seventy-four files are affected. Mixing mechanical test cleanup with production refactors would make
review and regression diagnosis difficult. Changes must be grouped and validated incrementally.

### Metric-driven over-refactoring

Cognitive-complexity and parameter-count findings can encourage abstractions that are harder to
understand than the original code. Refactoring must extract real responsibilities and preserve
module ownership rather than optimize only for the metric.

### Contract compatibility

Deprecation, unused-parameter, and large-signature findings may touch types with external or test
consumers. Usage must be verified before removal or signature change.

### False confidence from a clean issue count

Zero Sonar issues does not prove functional correctness. Maven tests, JaCoCo, focused regression
tests, architectural review, and human approval remain mandatory.

### Scope expansion

The new scan may expose unrelated or newly activated rules. Issues caused by Story changes must be
resolved; unrelated new baseline findings require explicit scope evaluation rather than automatic
expansion.

---

## Validation Strategy

Capture the authenticated 152-issue baseline by rule, severity, component, and source/test scope
before implementation. Address the Blocker first, then use small batches for mechanical test rules,
production constants/contracts, regex safety, and behavior-sensitive complexity refactors.

After each behavior-sensitive batch, run focused tests for the affected components. Run the complete
backend Maven verification before the final authenticated Sonar analysis.

The final validation sequence must include:

```text
cd backend
./mvnw clean verify
./mvnw sonar:sonar -Dsonar.qualitygate.wait=true
```

with the private `SONAR_TOKEN` supplied from the Git-ignored local environment established by Story
0010. Query the authenticated issue and measures APIs after processing and record non-secret
before/after results in the Story artifacts.

---

## Definition of Done

* [ ] All acceptance criteria are satisfied.
* [ ] The `CollectorRunner` resource leak is fixed and covered by focused tests.
* [ ] All 151 baseline code smells are corrected without blanket suppression or administrative
  closure.
* [ ] No new Sonar issue is introduced.
* [ ] All backend tests pass.
* [ ] The JaCoCo 80% bundle line-coverage rule remains satisfied.
* [ ] The authenticated Sonar analysis completes.
* [ ] The actual Quality Gate result is reported accurately.
* [ ] Final unresolved baseline issue count is zero.
* [ ] APIs, persistence, Repository Context policy, AI workflow, frontend, and Docker contracts are
  unchanged.
* [ ] No token or secret appears in tracked files or engineering artifacts.
* [ ] Implementation Report records rule-family and before/after evidence.
* [ ] Code Review is complete.
* [ ] Engineering Report is produced after all Human Approval Gates.
