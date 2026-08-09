# Engineering Report — Story 0021

## Workflow completion

All three mandatory Human Approval Gates were explicitly approved by Ludovic. Gate 3 approved the
current Code Review together with its documented AC-14 live-validation exception. Story 0021 is
complete; no automatic commit or merge was performed.

## Outcome

DevLog can now answer, on explicit request, whether the latest comparable Project Understanding
represents the selected Git Source's current default revision. The answer is based solely on
validated complete commit identities and remains separate from Analysis execution, proposal
review, and Trusted Knowledge validation.

## Main changes

* Added lock-protected current-revision resolution without checkout, reset, or worktree cleanup.
* Added exact completed `describe-project/v1` baseline selection for the same Source.
* Added deterministic `NO_BASELINE`, `CURRENT`, `STALE`, and `UNKNOWN` states with non-authoritative
  refresh guidance.
* Added versioned explicit-check/latest-result APIs, a stable 503 error, and migration V32 storing
  one latest successful operational result per Source.
* Added bounded freshness metadata to Engineering Story Context.
* Added a manual Angular cockpit interaction and corrected unsupported passive-monitoring and
  “up-to-date” claims.
* Reconciled the README, architecture, UI/UX, and roadmap documentation.

## Validation evidence

* Backend `./mvnw -q clean verify`: passed, including JaCoCo, PostgreSQL/Testcontainers, and all 32
  migrations.
* Frontend: 27 test files and 96 tests passed; production build passed.
* Docker backend/frontend rebuild and startup passed; the Angular deep link returned HTTP 200.
* Authenticated SonarQube (`devlog-ai`) with Quality Gate wait: `OK`; new-code coverage 80.8%,
  duplication 0.0%, zero new bugs, vulnerabilities, security hotspots, and code smells.
* Live DevLog check returned `STALE`: current
  `f67344c0f2f49fa1d938a0c0e68f496e1f85c69e` versus analyzed baseline
  `b2f2c8881bf8b7b89331c5161ca4f8cad16cd3f4`.
* The live compact context carried the same checked Source without hidden Git or Analysis work.
* All six real DevLog proposals remained pending; no Validation was submitted.
* Repository hygiene: `git diff --check` passed.

## Accepted validation exception

The full disposable live `STALE → explicit refresh → CURRENT` sequence required by AC-14 was not
executed. Refreshing the real DevLog project would have created a new Analysis and proposal set,
contrary to the approved preservation constraint, and no disposable remote was provisioned. The
`CURRENT` behavior is covered by automated classifier, service, API, and Git tests. Ludovic
explicitly accepted this exception with Gate 3 approval.

## Final state

The short-term deterministic freshness capability is implemented, documented, quality-gated, and
human-approved. Passive monitoring, automatic refresh, semantic change significance, and automatic
proposal decisions remain out of scope and deferred.
