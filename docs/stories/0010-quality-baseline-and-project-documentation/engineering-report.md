# Engineering Report

## Story

Story 0010 — Quality Baseline and Project Documentation stabilized DevLog's development baseline by
making local SonarQube analysis reproducible and authenticated, correcting the hosted CI quality
contract, and aligning the root project documentation with the system delivered through Story 0009.

## Objective

The Story addressed two coupled problems:

* the backend contained Sonar properties but did not manage the Sonar Maven scanner, so the short
  scanner goal was not reproducible and no authenticated Quality Gate result had been established;
* the repository documentation had evolved incrementally and needed a factual pass covering current
  runtime, context capabilities, trust boundaries, validation commands, and known limitations.

The intended outcome was a trustworthy base for subsequent Engineering Stories without changing
product behavior or weakening authentication and quality controls.

## Repository Analysis Summary

Repository Analysis identified Maven as the owner of backend compilation, tests, JaCoCo coverage,
and scanner invocation. `backend/pom.xml` already configured project key `devlog-ai`, a local
SonarQube URL, and the JaCoCo XML path, but did not declare the scanner plugin. An explicit scanner
invocation could reach the running SonarQube 26.7 server and was rejected with HTTP 401 when no
private token was available.

The workflow `.github/workflows/quality.yml` was named `Quality Gate` despite running only Maven and
JaCoCo. It also used `maven.test.failure.ignore`, allowing CI to continue despite test failures. No
repository evidence showed that GitHub-hosted runners could securely reach the local SonarQube
server.

The README already described Story 0009's four-service runtime and dedicated ports accurately, but
it lacked a Sonar quality contract and still described document projections as future work despite
implemented Deliverable generation.

Important constraints were preservation of the 80% JaCoCo rule, Sonar authentication, APIs,
database state, Repository Context behavior, frontend behavior, Docker topology and ports, accepted
ADRs, and historical Story artifacts. No new ADR or product dependency was required.

## Implementation Plan Summary

The human-approved plan defined six ordered activities:

1. declare and pin the official Sonar Maven scanner without binding it automatically to every
   `verify` run;
2. document secure private-token handling and a canonical command that waits for the Quality Gate;
3. correct GitHub Actions so tests fail the job and CI does not imply an absent Sonar submission;
4. update the root README using verified current implementation facts;
5. inspect directly related component and runtime documentation and modify only concrete
   contradictions;
6. validate Maven/JaCoCo, unauthenticated Sonar failure, authenticated analysis, Quality Gate,
   metrics, documentation links, Compose, and credential hygiene.

The plan explicitly excluded hosted Sonar infrastructure, product changes, coverage-threshold
adjustment, authentication weakening, and opportunistic remediation of unrelated Sonar findings.

## Implementation Summary

The backend now declares official Sonar Maven scanner version `5.7.0.6970`. Normal Maven verification
remains independent of SonarQube, while the documented explicit scanner goal can perform analysis
and wait for the Quality Gate.

The GitHub workflow is now named `Backend Build and Coverage`, uses the repository Maven Wrapper,
and runs `clean verify` without ignoring test failures. It continues to upload JaCoCo and Surefire
artifacts but no longer contains Sonar-specific checkout wording or claims to evaluate a local
Quality Gate.

The README now:

* describes implemented Deliverables, Repository Context, and Engineering Story Context;
* distinguishes deterministic Repository Evidence, human-validated knowledge, AI interpretation,
  and the current repository as implementation source of truth;
* documents Maven tests, the JaCoCo threshold, scanner version ownership, Sonar server override,
  Project Analysis Token creation, private-shell and ignored local `.env` loading, the canonical
  Quality Gate command, failure boundaries, and the local-versus-CI distinction;
* preserves the current four-service runtime and dedicated host ports.

At the human's explicit request, the new Project Analysis Token was stored in the repository-root
`.env` rather than only in a transient shell variable. This file is ignored by Git and already owns
local secrets. Its value was never written to tracked configuration, commands in Story artifacts,
or validation output.

Inspection found no contradictions requiring changes to `.env.example`, the AI Engine README, the
frontend README, or the manual MVP guide.

## Modified Files

* `backend/pom.xml` — version-manages and declares the official Sonar Maven scanner.
* `.github/workflows/quality.yml` — enforces Maven test failures and accurately represents hosted
  build/coverage responsibilities.
* `README.md` — updates current capabilities and trust boundaries and documents the complete local
  backend quality workflow.

## Created Files

* `docs/stories/0010-quality-baseline-and-project-documentation/story.md` — Story scope and
  acceptance contract.
* `docs/stories/0010-quality-baseline-and-project-documentation/repository-analysis.md` — approved
  repository findings and constraints.
* `docs/stories/0010-quality-baseline-and-project-documentation/implementation-plan.md` — approved
  implementation and validation strategy.
* `docs/stories/0010-quality-baseline-and-project-documentation/implementation-report.md` — factual
  implementation and validation record.
* `docs/stories/0010-quality-baseline-and-project-documentation/code-review.md` — independent
  acceptance-criteria and technical review.
* `docs/stories/0010-quality-baseline-and-project-documentation/engineering-report.md` — this final
  lifecycle record.

## Architecture Impact

There is no meaningful product architecture change.

Maven continues to own deterministic backend validation, SonarQube owns static-analysis rules,
metrics and Quality Gate evaluation, GitHub Actions owns only hosted checks it can execute, and the
developer's ignored environment owns the private credential. README remains the project entry point
while ADRs and component documents retain detailed architectural ownership.

No runtime dependency, application abstraction, service boundary, API, schema, persisted data,
Repository Context behavior, frontend behavior, Docker service, or port changed. Existing consumers
remain compatible.

## Validation

The deterministic backend lifecycle ran twice after implementation:

```text
./mvnw clean verify
```

Both runs passed with 375 tests, 0 failures, 0 errors, and 0 skipped tests. JaCoCo measured 3,461
covered lines out of 4,280 (80.86%) and satisfied the existing 80% bundle line-coverage rule.

The unauthenticated scanner validation used the pinned plugin and reached the local server before
failing as expected with HTTP 401 and explicit `SONAR_TOKEN` guidance.

The authenticated validation loaded the new Project Analysis Token from the ignored root `.env`
and ran the documented command shape:

```text
./mvnw clean verify sonar:sonar -Dsonar.qualitygate.wait=true
```

It passed all 375 tests, passed JaCoCo, uploaded the analysis for `devlog-ai`, waited for server
processing, and received `QUALITY GATE STATUS: PASSED`.

Authenticated API verification returned:

* Quality Gate: `OK`;
* bugs: 1;
* vulnerabilities: 0;
* security hotspots: 0;
* code smells: 151;
* coverage: 86.7%;
* duplicated lines density: 0.0%;
* lines of code: 11,351.

Additional successful validation included effective-POM scanner-version inspection, GitHub workflow
YAML parsing, `docker compose config --quiet`, README local-link validation, `.env` ignore
verification, credential-pattern inspection, and `git diff --check`.

A GitHub-hosted workflow run was not available during local validation. Its first push remains the
final confirmation in the hosted runtime.

## Review Outcome

Code Review mapped all ten acceptance criteria to implementation and validation evidence. It found
no Blocker, Major, Minor, or Observation finding. Module ownership, security boundaries, existing
ADRs, public contracts, and scope exclusions were preserved.

Technical recommendation: Ready for human approval.

Residual risks recorded by the review are:

* the first GitHub-hosted workflow run remains to be observed;
* the local `.env` must remain private and excluded from Git;
* SonarQube reports one bug and 151 code smells that require separate triage;
* the uncommitted POM lacked current SCM blame during this analysis and will receive normal
  attribution after a later committed analysis.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

None required for Story 0010.

The first GitHub-hosted workflow execution and triage of the existing Sonar bug/code smells are
non-blocking follow-ups outside this Story's approved implementation scope.

## Lessons Learned

* A Maven/JaCoCo success, scanner upload, and Sonar Quality Gate are distinct quality signals and
  should be reported separately.
* A workflow name can overstate enforcement; quality documentation must be checked against the exact
  command executed, including flags such as `maven.test.failure.ignore`.
* External local quality services should remain explicit additions to deterministic builds rather
  than hidden prerequisites for every developer command.
* A Git-ignored local environment file can support repeatable secret-backed validation, provided
  Maven loading is explicit and credential hygiene is independently verified.
* Establishing the baseline can be complete even when the first scan reveals findings; remediation
  should remain separately scoped instead of contaminating infrastructure stabilization work.

## Final Status

Completed
