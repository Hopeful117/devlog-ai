# Code Review Report — Story 0018

## Review Status

Ready for human approval.

## Scope Reviewed

The review inspected the approved Story, Repository Analysis, Implementation Plan, complete working
tree diff, V31 migration, backend orchestration and tests, Angular interaction and tests, canonical
documentation, full validation results, SonarQube evidence, and the live DevLog execution.

## Findings

No Blocker, Major, Minor, or Observation finding remains.

Two implementation hardenings were made before this final review:

* history import re-resolves the Source inside its transaction rather than persisting a detached
  caller-provided entity;
* duplicate-race recovery handles only the execution-key uniqueness race and preserves unrelated
  integrity failures.

## Acceptance-Criteria Review

### Product interaction and preconditions

The Cockpit action is available before and after prior canonical executions and uses first-run or
refresh language without introducing a permanent initialization flag. The Core validates project,
active Git Source ownership/type, revision synchronization, canonical Intent resolution, and normal
task mapping before claiming an Analysis. Preparation failures do not create a partial Analysis.

Result: Compliant with AC-1 through AC-5 and AC-8.

### Traceability, duplicate safety, and failure behavior

Product Analyses persist selected Source identity plus immutable snapshot, requested revision,
canonical Intent, normalized guidance, and execution key. Existing diagnostics retain the resolved
revision and downstream context/AI provenance. Equivalent active work is reused; a partial unique
PostgreSQL index closes cross-request and cross-instance races. Terminal executions release the key
for later refresh. Post-claim start failure marks a still-pending execution failed; existing
workflow failure behavior remains authoritative after start.

Result: Compliant with AC-6, AC-7, and AC-10.

### Trust and user-facing boundaries

The operation reuses the normal deterministic collection, context, AI, proposal, and validation
pipeline. The Angular UI communicates project understanding, Source/revision, and lifecycle
outcomes without exposing orchestration internals. Live execution produced six proposals that
remained unvalidated, confirming no automatic Trusted Knowledge promotion.

Result: Compliant with AC-9 and AC-11.

### Coverage, compatibility, documentation, and quality

Focused and complete backend/frontend suites, real PostgreSQL migration/concurrency coverage,
production build, formatting, Docker/API/UI checks, JaCoCo, and authenticated SonarQube passed.
Generic Analysis and all-active-source behavior remain separate and tested. Project CRUD, Source,
history, profile, proposal, and Engineering Story Context workflows remain compatible.

Result: Compliant with AC-12 through AC-15.

## Architecture Review

* Java Core owns validation, revision resolution, canonical Intent selection, execution identity,
  workflow start, and failure transitions.
* PostgreSQL owns equivalent-active-execution uniqueness.
* The Source snapshot preserves historical provenance independently of later Source deletion.
* The selected-Source collection branch does not change generic Analysis semantics.
* Angular owns deliberate input and feedback, not prompt or workflow construction.
* Existing proposal validation remains the only Trusted Knowledge promotion boundary.
* No scheduler, webhook, broker, second scanner, or AI repository access was introduced.

No ADR is required.

## Concurrency and Transaction Review

Slow Git synchronization and history preparation occur outside the short claim transaction. The
claim transaction rechecks ownership, writes the Analysis, and flushes against the partial unique
index. A preliminary query is an optimization; the database remains authoritative. Terminal states
fall outside the index predicate and permit a later equivalent refresh without mutating history.

The PostgreSQL integration test verifies concurrent equivalent inserts, terminal release, Source FK
`SET NULL`, retained JSON snapshot, and Story 0017 project cascade compatibility.

## Security and Data-Integrity Review

The endpoint accepts identifiers, a bounded revision, and the existing bounded guidance structure;
clients cannot choose the Analysis type, Intent, prompt, execution key, or lifecycle status. Source
lookup is constrained by project and active state. The workflow uses the established workspace and
repository trust boundary. No dynamic SQL, new credential exposure, external deletion, or automatic
proposal promotion was added.

The application still has no authentication/authorization layer. Story 0018 neither adds nor
weakens one; identity and authorization remain a separate platform capability.

## Validation Review

* Backend: 437 tests, 0 failures/errors/skips.
* JaCoCo: 82.5% line coverage; bundle rule passed.
* PostgreSQL: V31 migration and real uniqueness/lifecycle/provenance assertions passed.
* Frontend: 85 tests across 23 files; production build and formatting passed.
* SonarQube: Quality Gate `OK`, new coverage 87.1%, new duplication 0.0%, new violations 0.
* Docker/live: create, equivalent reuse, completion, Profile generation, proposal-review boundary,
  and subsequent Engineering Story Context succeeded on the configured DevLog project.
* Repository hygiene: `git diff --check` passed.

## Residual Risks

* Synchronous repository preparation can make the HTTP request long-running for large or remote
  repositories; durable background orchestration is explicitly out of scope.
* V1 supports deliberate single-Source execution, not multi-Source understanding in one request.
* Browser-level accessibility and responsive behavior rely on semantic markup, component tests,
  and served-UI inspection rather than an automated browser suite in this environment.
* Future schema additions must preserve the active-key uniqueness and immutable provenance
  conventions.

These risks are bounded and documented; none represents unfinished Story 0018 scope.

## Recommendation

Technical recommendation: Approve.

Human Code Review approval is still required. No Engineering Report, commit, merge, or Story
finalization may occur before that approval.
