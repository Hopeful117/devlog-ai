# Engineering Report — Story 0020

## Workflow completion

All three mandatory Human Approval Gates were explicitly approved by Ludovic. Gate 3 approved the
current Code Review after the authenticated SonarQube rerun passed. Story 0020 is complete; no
automatic commit or merge was performed.

## Outcome

Implemented an Analysis-scoped guided review workspace while retaining the existing immutable,
proposal-specific Validation authority. The Core now supplies a deterministic pending-first page,
Analysis-wide counts, batched evidence summaries, persisted decisions, and resulting Insights.
Angular keeps the reviewer in one accessible queue and advances only after a successful decision.

## Main changes

* Added `GET /api/v1/analyses/{analysisId}/proposal-review` with custom paging and configurable
  limits (default 10, maximum 20).
* Added deterministic `PROPOSED`, `sourceIndex`, `createdAt`, `id` ordering and batched hydration.
* Hardened validation with a pessimistic proposal lock; the database uniqueness constraint remains
  the final invariant.
* Added `/analyses/:id/proposal-review`, explicit session-local reviewer UUID ownership, individual
  confirmation, in-flight suppression, conflict refresh, and direct audit navigation.
* Preserved all existing proposal read and Validation write contracts.

## Validation evidence

* Backend `./mvnw -q test`: passed.
* Backend `./mvnw -q clean verify`: passed, including PostgreSQL/Testcontainers and JaCoCo checks.
* Frontend `npm test -- --watch=false`: 25 files and 91 tests passed.
* Frontend `npm run build`: passed; review lazy chunk 10.99 kB raw.
* Docker `up -d --build backend frontend`: passed.
* Authenticated SonarQube (`devlog-ai`) with Quality Gate wait: `OK`; new-code coverage 80.9%,
  duplication 0.0%, zero new bugs, vulnerabilities, security hotspots, and code smells.
* Live read against DevLog Analysis `bd71ca14-88fa-4028-b3f9-91365d931b44`: six pending
  proposals returned in source indexes 0–5 with correct Project ownership and evidence hydration.
* Live Angular deep link returned HTTP 200.
* No live Validation was submitted because these are Ludovic's real DevLog proposals.

## Workflow comparison

For six proposals, the former path required six proposal-detail navigations, six returns to the
Analysis, and repeated reviewer setup. The new path requires one workspace navigation and one
explicit reviewer setup. It deliberately retains six confirmations and six Validation writes:
efficiency improves without weakening human authority.

## SonarQube reconciliation

The first authenticated scan found one Story-local Blocker code smell (`java:S1845`): the response
constant `VERSION` could clash conceptually with the record field `version`. It was renamed to
`PROJECTION_VERSION`, focused tests were rerun, and the subsequent Quality Gate passed with no
unresolved new issue.
