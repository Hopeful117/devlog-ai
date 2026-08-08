# Implementation Plan

## Overview

The implementation will establish one truthful backend quality contract without adding application
behavior or new infrastructure. Maven will intentionally manage the official Sonar scanner while
retaining the existing test and JaCoCo lifecycle. Local documentation will explain how a developer
provides a private token, runs verification plus Sonar analysis, waits for the Quality Gate, and
distinguishes each result. GitHub Actions will be corrected to enforce the deterministic checks it
can actually run without claiming access to the local SonarQube server.

The documentation pass will then use the verified commands and current repository contracts to
correct stale statements and preserve the root README as the practical entry point. Specialized
details will remain in their existing component and architecture documents.

## Planned Changes

### 1. Make the Sonar Maven scanner project-owned

Update `backend/pom.xml` to declare the official Sonar Maven scanner with an explicitly managed
version. Keep the existing project key, JaCoCo XML location, Java version, and JaCoCo executions
unchanged.

The scanner declaration must make `sonar:sonar` resolvable from the project rather than relying on
the caller's Maven plugin-group configuration or an implicit latest release. The local server URL
may remain a development default provided the canonical command documents the standard Maven
property override for another SonarQube URL.

No scanner execution will be bound automatically to every `verify` run. This preserves fast,
deterministic Maven validation when SonarQube is unavailable and keeps authenticated analysis an
explicit additional operation.

### 2. Define the secure local quality command

Document a short local sequence that:

1. exports `SONAR_TOKEN` into the current shell from a human-created SonarQube analysis token;
2. optionally supplies a non-default server through the supported Sonar/Maven host property;
3. runs `clean verify` before `sonar:sonar` in the same Maven invocation or documented sequence;
4. enables Quality Gate waiting so a failed gate cannot be presented as success;
5. keeps the credential value out of the command text, tracked `.env` files, Maven settings, and
   Story artifacts.

Add focused troubleshooting for missing/rejected authentication, unavailable server, scanner
failure, and failed Quality Gate. Explicitly state that Maven/JaCoCo success does not imply Sonar
success.

Token creation remains manual. For the current local installation, the developer will obtain it
from SonarQube at `http://localhost:9000`: user menu → **My Account** → **Security** → generate an
analysis token. Only the generated value is needed during implementation validation, supplied via
the private shell environment; it must not be pasted into a tracked document or chat transcript.

### 3. Correct the GitHub Actions quality contract

Update `.github/workflows/quality.yml` so its workflow/job naming describes backend build, tests,
and coverage rather than a Sonar Quality Gate.

Remove `-Dmaven.test.failure.ignore=true` so a failing backend test makes the quality job fail.
Remove or correct Sonar-specific comments/configuration that imply a scanner submission. Preserve
Maven dependency caching, JaCoCo summary generation, and diagnostic artifact upload with `always()`.

Do not add a Sonar step, token secret, or localhost endpoint to the GitHub-hosted workflow because
the repository contains no secure route from that runner to the local SonarQube service. A hosted
or reachable Sonar CI integration remains a separate increment.

### 4. Align the root README with the current implementation

Review and update `README.md` in bounded sections:

* correct the obsolete opening claim that document projections remain future work now that
  Deliverable generation exists;
* ensure the current-capabilities and project-status sections include the implemented Repository
  Context and Engineering Story Context capabilities without presenting roadmap items as complete;
* preserve the Story 0009 four-service runtime and ports `18080`–`18083`;
* clarify the trust boundary between deterministic repository evidence, AI proposals, human-
  validated Insights, generated Deliverables, and the repository as implementation source of truth;
* add a backend quality section covering Maven tests, JaCoCo's 80% line threshold, local SonarQube,
  secure token handling, the canonical command, Quality Gate interpretation, troubleshooting, and
  the local-versus-GitHub-CI distinction;
* keep architectural and component detail linked to the documents that already own it.

Do not rewrite unrelated prose or historical descriptions solely for style.

### 5. Audit directly affected current documentation

Check `.env.example`, `ai-engine/README.md`, `frontend/README.md`, and
`frontend/docs/manual-mvp-test.md` against the final README, Compose contract, and current source.
Modify a file only when a concrete contradiction or missing directly owned instruction is found.

Do not add `SONAR_TOKEN` to `.env.example`: that file documents Compose runtime configuration,
whereas the token belongs to a private developer shell or secret store for a local Maven analysis.
Preserve all correct dedicated-port and frontend-container instructions delivered by Story 0009.

### 6. Validate the unauthenticated and authenticated paths

Run the backend Maven lifecycle first and confirm tests, JaCoCo XML generation, and the existing
coverage check independently.

Then run the canonical Sonar command without a token and verify it fails explicitly at
authentication rather than appearing successful. After the human provides a private token, rerun
the canonical command against the local server, wait for processing, and retrieve the Quality Gate
and available metrics for `devlog-ai` through scanner output and authenticated SonarQube APIs.

Record only non-secret results: project key, redacted command shape, analysis completion, gate
status, bugs, vulnerabilities, security hotspots, code smells, coverage, and duplicated lines. If
the Quality Gate fails or new findings appear, report them accurately; do not fix unrelated findings
inside this Story.

Finally, inspect the rendered GitHub workflow semantics and verify documentation links and current
commands. Run AI Engine or frontend validation only if their owned files are materially changed.

## Files to Modify

* `backend/pom.xml` — declare and pin the official Sonar Maven scanner while preserving JaCoCo and
  existing Sonar project properties.
* `.github/workflows/quality.yml` — enforce test failures and accurately name/document the Maven and
  JaCoCo checks actually run by GitHub Actions.
* `README.md` — correct stale product statements and add the secure, reproducible local quality
  workflow and CI boundary.
* `.env.example` — only if inspection identifies a concrete non-secret configuration clarification;
  no Sonar credential value will be added.
* `ai-engine/README.md` — only if a verified contradiction with current component behavior is found.
* `frontend/README.md` — only if a verified contradiction with the current runtime is found.
* `frontend/docs/manual-mvp-test.md` — only if a verified contradiction with the current runtime or
  validation entry point is found.
* Story 0010 workflow artifacts — record implementation and validation evidence without secrets.

## Files to Create

None.

No wrapper script, new configuration subsystem, or application test fixture is required for the
minimal quality contract.

## Dependencies

The implementation uses the existing Maven Wrapper, JaCoCo plugin, GitHub Actions workflow, and
local SonarQube server. It intentionally adds only the official Sonar Maven scanner as a managed
build plugin; no runtime dependency is introduced.

Implementation ordering is significant:

1. pin the scanner so the canonical command is stable;
2. correct deterministic CI behavior;
3. document the exact verified contract;
4. validate without credentials;
5. obtain the human-controlled token and perform authenticated validation;
6. finalize documentation and report the actual results.

The only external prerequisite for complete validation is a valid analysis token created in the
local SonarQube user security page. The token is not required to implement or test Maven/JaCoCo and
the expected unauthenticated failure path.

## Test Plan

### Backend deterministic validation

Run from `backend`:

```text
./mvnw clean verify
```

Expected results:

* compilation succeeds;
* all backend tests succeed without `maven.test.failure.ignore`;
* `target/site/jacoco/jacoco.xml` is produced;
* the existing 80% line-coverage check succeeds.

This covers AC-1 and AC-4 without depending on SonarQube.

### Scanner resolution and authentication failure

Run the canonical scanner goal without `SONAR_TOKEN` against the running local server.

Expected results:

* Maven resolves the project-declared scanner version;
* the scanner reaches SonarQube;
* authentication failure is explicit and returns a failed process;
* no output contains a secret.

This covers AC-1, AC-2, and the negative portion of AC-10.

### Authenticated analysis and Quality Gate

With `SONAR_TOKEN` exported privately, run the canonical verification and scanner command with
Quality Gate waiting enabled. Query the current project status and metrics using authenticated
SonarQube access without printing the token.

Expected results:

* analysis completes for `devlog-ai`;
* scanner version and server target are identifiable;
* the process reflects the actual Quality Gate result;
* available bugs, vulnerabilities, hotspots, smells, coverage, and duplication metrics are reported
  accurately;
* the token is absent from Git status, diffs, logs committed to the Story, and documentation.

This covers AC-2, AC-3, and the positive portion of AC-10. A failed Quality Gate is a valid observed
result but cannot be reported as successful validation.

### CI contract validation

Inspect the workflow and run its Maven command locally. Where a GitHub run is available, verify the
job fails on test failure and continues to upload diagnostics through `always()` steps.

Expected results:

* names and comments do not claim Sonar execution;
* tests are not ignored;
* coverage reporting and artifact uploads remain configured;
* no unreachable local Sonar step or credential is added.

This covers AC-4 and AC-5.

### Documentation validation

Use targeted searches and direct reads to verify:

* service count and ports match Compose;
* Engineering Story Context GET/POST examples match current controllers;
* implemented capabilities match current code and accepted ADRs;
* future work remains labelled as future work;
* quality commands and troubleshooting match observed behavior;
* referenced repository links resolve;
* no secret or token-shaped value is present in tracked changes.

Run frontend tests/build or AI Engine tests only if the corresponding current documentation or
configuration file is materially changed and needs operational confirmation.

This covers AC-6 through AC-9.

## Risks

### Token handling during delegated execution

An execution provider must not receive a credential in its prompt or write it into artifacts.
Mitigation: pause authenticated validation until the human exports the token into the local process
environment; reference only the environment variable name and redact command/report output.

### Quality Gate may expose unrelated findings

The first authenticated baseline may fail for existing code. Mitigation: preserve the result,
separate it from scanner-integration correctness, and report out-of-scope findings without modifying
unrelated application code.

### Maven command coupling

Binding Sonar automatically to `verify` would make normal builds depend on a local external service.
Mitigation: declare the plugin but retain an explicit `sonar:sonar` invocation.

### CI behavior regression

Removing ignored test failures may reveal existing test instability. Mitigation: treat any actual
test failure as a real blocker and diagnose it rather than restoring the ignore flag.

### Documentation expansion

Trying to restate every ADR in the README would create a new maintenance burden. Mitigation: update
only verified current facts and keep detailed ownership in existing linked documents.

## Validation Checklist

* [ ] The official Sonar Maven scanner is declared with an intentional version.
* [ ] `sonar:sonar` resolves through project configuration.
* [ ] Existing project key and JaCoCo report path remain correct.
* [ ] `./mvnw clean verify` runs all tests and enforces the 80% line-coverage rule.
* [ ] The GitHub workflow no longer ignores Maven test failures.
* [ ] GitHub workflow names/comments describe Maven and JaCoCo, not an absent Sonar Quality Gate.
* [ ] No local Sonar endpoint or private token is added to GitHub Actions as if it were reachable.
* [ ] Missing authentication produces a clear failed scanner invocation.
* [ ] A human-generated token is supplied only through a private environment mechanism.
* [ ] Authenticated analysis completes and the real Quality Gate result is retrieved.
* [ ] Available Sonar metrics are reported without exposing credentials.
* [ ] The README accurately describes the current four-service product and trust model.
* [ ] Obsolete Deliverable/document-projection wording is corrected.
* [ ] Local quality commands, CI boundaries, and troubleshooting are documented.
* [ ] Directly affected current documentation is consistent and navigable.
* [ ] Docker ports, topology, APIs, persistence, application behavior, and Repository Context remain
  unchanged.
* [ ] Historical Stories and ADRs are unchanged.
* [ ] Unrelated Sonar findings are reported rather than fixed opportunistically.
* [ ] Git diff and repository history contain no token or credential.

## Recommendation

Ready for implementation

This is a technical recommendation only. It does not approve the Implementation Plan or authorize
Implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
