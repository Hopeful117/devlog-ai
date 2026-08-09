# Implementation Report — Story 0021

## Outcome

Implemented an explicit, Source-scoped freshness check that compares the current default Git
commit with the latest comparable completed `describe-project/v1` understanding. Successful checks
persist one latest operational result per Source; reads and Engineering Story Context reuse that
result without contacting Git. No analysis or proposal decision is launched automatically.

## Delivered

* Added lightweight, lock-protected current-revision resolution without checkout/reset/clean.
* Added exact baseline selection and deterministic `NO_BASELINE`, `CURRENT`, `STALE`, and `UNKNOWN`
  classification using complete Git object IDs only.
* Added versioned POST/latest APIs, a stable 503 failure contract, migration V32, proposal-review
  counts, and bounded context transport.
* Added a manual Angular cockpit action with explicit unchecked/checking/result/error states and a
  navigation-only refresh recommendation.
* Corrected unsupported cockpit claims about passive monitoring and being up to date.

## Documentation reconciliation

`README.md`, `docs/architecture.md`, `docs/ui-ux.md`, and `docs/roadmap.md` now consistently describe
freshness as an explicit operational check, keep passive monitoring deferred, and preserve human
authority over understanding launches and proposal decisions. No ADR boundary changed.

## Verification

* Backend: `./mvnw -q clean verify` passed, including PostgreSQL migration validation through V32.
* Frontend: 96 tests passed and the production Angular build passed.
* Docker: backend and frontend rebuilt; containers started healthy; deep-link UI returned HTTP 200.
* SonarQube: authenticated analysis passed the Quality Gate with 80.8% new-code coverage, 0.0%
  duplication, and zero unresolved new-code issues.
* Live DevLog check: Source `7819103b-37e7-4e15-95ec-fff9a12d21e4` returned `STALE`, comparing
  current `f67344c0f2f49fa1d938a0c0e68f496e1f85c69e` with baseline
  `b2f2c8881bf8b7b89331c5161ca4f8cad16cd3f4`. Its six proposals remained pending.
* Live agent context carried the same single checked Source, `uncheckedSourceCount=0`, and
  `truncated=false` without launching Git or analysis work.

## Validation limitation

The complete live stale → explicit refresh → current sequence was not executed: refreshing the real
DevLog baseline would create a new analysis/proposal set, contrary to the approved preservation
constraint, and a disposable remote was not provisioned. The `CURRENT` transition is covered by
the pure classifier and service/API tests, but AC-14's complete live sequence remains an explicit
Gate 3 exception rather than being represented as completed.
