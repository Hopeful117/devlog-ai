# Implementation Report — Story 0022

## Outcome

Implemented the approved single-commit Engineering Event vertical slice. A user can explicitly
select one active Git Source and one complete non-root target commit; Core derives and persists its
first-parent boundary, snapshots bounded evidence, invokes a dedicated versioned Intent, validates
event proposals authoritatively, and promotes an individually accepted proposal into exactly one
immutable Engineering Event in the Validation transaction.

The implementation is complete and all automated quality controls pass. Representative Docker
execution proved the real provider path and immutable boundary twice, but both valid provider calls
returned zero proposals. Consequently AC-14's required live acceptance and promotion demonstration
remains unmet and is reported as a Gate 3 exception, never replaced with fabricated knowledge.

## Delivered

* Added ADR-047, Intent proposal-type/execution-mode contracts, dedicated
  `analyze-engineering-event/v1` routing, and strict Core/Python event schemas.
* Added V33 evolution scopes, active execution keys, immutable Engineering Events, provenance
  constraints, stable read APIs, and backward-compatible separation from raw legacy
  `KnowledgeEvent` records.
* Added explicit first-parent execution preparation and concurrent claim reuse. Root commits,
  abbreviated IDs, mismatched revisions, inactive/cross-Project Sources, and generic execution of
  the dedicated Intent are rejected.
* Added bounded evolution and validated-event context to selection v3, canonical accounting and
  digests, Project context, and Engineering Story Context. Unvalidated proposals remain excluded.
* Added transaction-local type-dispatched promotion. Existing Insight promotion remains compatible;
  unsupported accepted proposal types fail before a Validation or trusted object can persist.
* Added Angular execution, review, recent/list/detail, provenance, and conditional Insight-severity
  surfaces without passive launch or bulk validation.

## Documentation reconciliation

Documentation reconciliation is complete. `README.md`, ADR-047, architecture, data model,
knowledge model, pipeline, roadmap, UI/UX, AI Engine README, and frontend README now distinguish raw
Git activity, legacy Knowledge Events, proposals, validated Engineering Events, generic Insights,
Decisions, and Challenges. The documentation claims only the delivered explicit single-commit
first-parent slice; range analysis, passive monitoring, Decisions, and Challenges remain deferred.

## Verification

* Backend: `./mvnw -q clean verify` passed, including V33 PostgreSQL migration and Testcontainers.
* AI Engine: 45 tests passed after reinstalling the moved editable environment.
* Frontend: 98 tests across 28 files passed; production build passed.
* Docker: backend, AI Engine, and frontend rebuilt and reported healthy.
* SonarQube project `devlog-ai`: Quality Gate passed with 80.0% new-code coverage, 0.0% new
  duplication, and zero new bugs, vulnerabilities, security hotspots, code smells, or unresolved
  issues.
* Hygiene: `git diff --check` passed; generated IDE, bytecode, and package-metadata changes were
  removed; no credential value was printed or persisted.

## Representative live validation

A disposable Project and Git Source analyzed target
`2e6c71eee2ffa82ddb3ebc212008a1cc946845d0` against first parent
`f67344c0f2f49fa1d938a0c0e68f496e1f85c69e` through Docker and the configured OpenAI provider.
Analysis `2abf9fd8-8b9a-466e-ab09-703b84daa526` completed with the exact `FIRST_PARENT` scope and
provider/model traceability but returned zero proposals. The one permitted second representative
attempt, Analysis `398887d7-121b-480e-96d0-e5bcd78cf790`, also completed validly with zero
proposals.

Because zero output is an intentional safe result, no event was fabricated and no acceptance was
attempted. The disposable Project was deleted through its scoped API and confirmed absent. The real
DevLog Project remained read-only: all six proposals are still pending, with zero accepted and zero
rejected.

## Acceptance status

AC-1 through AC-13 and AC-15 are implemented and supported by automated, migration, Docker, API,
build, and quality evidence. AC-14 is not fully satisfied: live explicit execution and provenance
were demonstrated, but a grounded live proposal, individual acceptance, and exactly-one promoted
event could not be demonstrated after the two plan-authorized valid zero-output runs.

