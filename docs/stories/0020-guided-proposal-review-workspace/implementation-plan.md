# Implementation Plan — Story 0020

## Status

Human-approved; implemented and validated.

## Overview

Implement a bounded Analysis-level proposal-review read projection and a dedicated Angular review
workspace while preserving the existing proposal-specific Validation write path. The Core will
assemble deterministic queue pages, authoritative counts, readable evidence summaries, persisted
decision metadata, and resulting Insight links. Angular will use that projection to keep the
reviewer in one queue, retain one explicitly chosen local reviewer UUID for the browser session,
and advance only after each individual decision succeeds.

No bulk decision endpoint, automatic decision, proposal editing, new lifecycle state, or durable
reviewer identity will be introduced. Existing proposal list/detail APIs and `/proposals/:id`
remain compatible audit surfaces.

## Approved Inputs

Implementation will use:

* Story 0020;
* the human-approved Repository Analysis;
* the current proposal, validation, Insight, Fact, Observation, Analysis, and Angular contracts;
* ADR-033's deterministic knowledge boundary;
* ADR-034's rule that only human-validated Insights feed Deliverables;
* the current 20-proposal AI output bound as a sizing input, not a database invariant.

No implementation work may begin before explicit approval of this plan.

## Planned Changes

### 1. Capture a disposable pre-implementation workflow baseline

Before changing production code, identify or create a safe Analysis containing at least three
`PROPOSED` Insight proposals. Prefer a disposable Project/Analysis; if the existing DevLog project
is reused, do not accept or reject its real proposals without separate evidence that the data is
disposable.

Record outside Git:

* Analysis and Project IDs;
* proposal count and stable identifiers;
* number of route transitions needed to decide all proposals;
* number of returns to the Analysis page;
* reviewer UUID generation/entry count;
* HTTP read requests and individual Validation writes;
* starting Validation/Insight counts;
* current conflict response for a deliberately repeated decision where safe.

The comparison target is reduced navigation and repeated identity work. The number of explicit
human decisions and authoritative Validation writes must remain exactly one per decided proposal.

### 2. Define a versioned bounded review policy

Add an immutable `ProposalReviewPolicy` under a cohesive proposal-review package, configured using
Spring properties:

* default page size: `10`;
* hard maximum page size: `20`;
* maximum supporting Fact summaries per proposal: `10`;
* maximum supporting Observation summaries per proposal: `10`;
* maximum evidence-reference strings per proposal: `20`;
* maximum Fact/Observation content characters: `1,000` each;
* maximum canonical structured-payload preview bytes: `8,192` per proposal.

Use project property names under `devlog.proposal-review.*`. Validate all limits as positive and
ensure the default page size does not exceed the maximum. The AI Engine's current 20-proposal
result bound justifies the hard page size, but paging remains mandatory because proposals can also
be created through the public Core API.

Content preview truncation must be deterministic and Unicode-safe. Structured payloads are
serialized canonically with the configured Jackson mapper. If a payload exceeds the preview byte
limit, the review projection returns no partial invalid JSON; it returns a bounded preview outcome
containing byte count, SHA-256 digest, `truncated=true`, and the direct proposal-detail link. Small
payloads retain their structured map.

### 3. Define the Analysis proposal-review API contract

Add:

`GET /api/v1/analyses/{analysisId}/proposal-review?page=0&size=10`

Return a custom stable DTO rather than Spring's internal `Page` serialization. The top-level
contract should contain:

* projection version and ordering-policy version;
* Analysis ID and persisted Project ID;
* authoritative total, pending, accepted, and rejected counts;
* zero-based page, requested/effective size, total pages, and `hasPrevious/hasNext`;
* ordered review items.

Each review item should contain:

* proposal ID, Project ID, Analysis ID, source index, type, status, timestamps;
* typed Insight proposal title, summary, rationale, and confidence;
* bounded payload-preview outcome;
* bounded evidence-reference list plus available/returned counts and truncation flag;
* bounded Fact evidence outcomes;
* bounded Observation evidence outcomes;
* persisted Validation summary when decided;
* resulting Insight summary/link when accepted;
* explicit warnings for missing or cross-Analysis evidence.

Reject negative pages, size below one, and size above the hard maximum with the standard
`INVALID_PARAMETER` 400 contract. Unknown Analysis IDs return the standard 404. Derive Project
identity from the Analysis; clients cannot select it independently.

### 4. Implement deterministic paged proposal selection

Extend `ValidatableProposalRepository` with one paged Analysis query using an explicit stable order:

1. `PROPOSED` first;
2. `sourceIndex` ascending with nulls last;
3. `createdAt` ascending;
4. `id` ascending.

Use an explicit JPQL query with a status `CASE` and null-order expression so behavior does not
depend on database default ordering. Add separate Analysis-scoped count methods for total and each
status, or one grouped count query mapped deterministically. Counts describe the complete Analysis,
not only the current page.

Do not add a new database index initially: normal AI output is bounded at 20 proposals and the
existing Analysis index covers selection. Validate the query plan/behavior with PostgreSQL; add a
Flyway index only if measured evidence demonstrates a need.

### 5. Batch-hydrate review evidence and decision outcomes

Add a read-only `ProposalReviewService` that:

1. loads the Analysis with its Project;
2. obtains the deterministic proposal page and aggregate counts;
3. unions bounded candidate Fact/Observation IDs for the page;
4. batch-loads Facts and Observations with Analysis-scoped repository queries;
5. batch-loads Validations by proposal IDs;
6. batch-loads resulting Insights by proposal IDs;
7. assembles immutable DTOs in proposal order.

Add repository methods such as:

* `findByAnalysisIdAndIdIn` for Facts and Observations;
* `findByProposalIdIn` for Validations and Insights.

Never call a repository once per proposal or evidence item. Focused tests should verify a fixed
number of repository interactions independent of page item count.

For each requested Fact/Observation ID:

* include it only when it belongs to the source Analysis;
* retain its stable ID, type, bounded content, source/rule provenance, and relevant evidence IDs;
* return a stable `MISSING` or `ANALYSIS_MISMATCH` outcome when it cannot be used;
* preserve proposal-declared order and deduplicate repeated IDs.

The projection reports available/returned/truncated counts. It does not rank, infer, rewrite, or
validate supporting evidence.

### 6. Harden concurrent individual Validation

Add `ValidatableProposalRepository#findByIdForValidation` using
`@Lock(LockModeType.PESSIMISTIC_WRITE)` and a focused query. `ValidationServiceImpl#validate` will
load the proposal through this method inside its existing transaction, then recheck status and the
unique Validation relation before changing state.

Preserve:

* one request per proposal;
* required severity for accepted Insight proposals;
* rejection without Insight creation;
* atomic proposal, Validation, and Insight persistence;
* the database unique constraint as final defense;
* the existing 409 conflict message/contract for already-decided proposals.

Do not add retries, lock-timeout loops, last-write-wins behavior, or decision reversal.

Add a real PostgreSQL integration test with two concurrent decisions for one proposal. Assert:

* exactly one succeeds;
* the other receives a stable conflict after the winner commits;
* exactly one Validation exists;
* proposal status matches the winner;
* an Insight exists only when the winning decision is acceptance and never more than once.

### 7. Add the Angular review models and service contract

Extend the Insight/proposal models with typed review-projection interfaces. Add
`getProposalReview(analysisId, page, size)` to `InsightProposalService` with encoded route values and
exact response typing.

Keep existing `getProposalsByAnalysis`, `getProposal`, `getDecision`, `acceptProposal`, and
`rejectProposal` methods unchanged for compatibility.

Add HTTP service tests for:

* exact review URL and query parameters;
* typed response consumption;
* unchanged individual accept/reject payloads;
* invalid/error propagation through the existing request-error layer.

### 8. Add session-local reviewer identity ownership

Create a focused injectable `ProposalReviewerSessionService` using a versioned
`sessionStorage` key. It should expose synchronous get/set/clear or a minimal observable signal
appropriate to the component, without introducing a global state library.

Rules:

* no value is generated automatically;
* only a valid UUID is returned or stored;
* malformed stored data is removed;
* generation remains an explicit user action using `crypto.randomUUID()`;
* replacement and clearing are supported;
* storage access errors fall back to component-memory state for the active page;
* comments, severity, proposal IDs, and decisions are never stored.

Unit tests should use a replaceable storage abstraction or browser test storage cleanup to prove
session reuse, replacement, clearing, malformed input, and unavailable-storage fallback.

### 9. Implement the guided review route and state machine

Add route:

`/analyses/:id/proposal-review`

Create a standalone `ProposalReviewPage` using declarative RxJS streams and reactive forms. Reuse
the repository's `exhaustMap`, `shareReplay`, request-error, and semantic status patterns. The page
state should track proposal identity rather than only array index so refreshes do not silently move
the reviewer to another item.

Initial selection policy:

1. explicitly requested/current proposal when still present;
2. first pending proposal on the current page;
3. first proposal when the page contains only completed items;
4. fetch the next page when current pending work is exhausted and `hasNext=true`;
5. show completion when authoritative pending count is zero.

The page must show:

* Review heading and link back to the source Analysis;
* total/pending/accepted/rejected progress;
* current position and previous/next controls;
* proposal status/type/confidence/time;
* title, summary, rationale, payload-preview outcome;
* readable Fact/Observation summaries and raw evidence references;
* direct proposal audit link;
* reviewer UUID control with local-MVP disclaimer and generate/replace/clear actions;
* comment and accepted-severity controls;
* explicit accept/reject selection followed by immutable-decision confirmation;
* persisted Validation and resulting Insight for completed proposals;
* completion links to Insights and the Analysis deliverable panel or existing deliverable entry.

After successful Validation:

1. clear decision confirmation;
2. retain reviewer UUID;
3. reset proposal-specific comment and severity to documented defaults;
4. refresh the authoritative review projection;
5. advance to the next pending proposal;
6. announce the outcome and new position.

On ordinary failure, retain the current proposal, comment, severity, reviewer UUID, and
confirmation context as appropriate. On 409, do not retry; refresh authoritative state, show the
persisted final decision, and choose the next pending proposal only after the user can see the
conflict outcome.

### 10. Add the Analysis entry point and preserve direct detail

Update `AnalysisInsightsSection` to expose one prominent `Review proposals` action whenever the
Analysis has one or more proposals. Include pending count in the label or adjacent text. When no
pending proposals remain, use outcome-oriented copy such as `Review completed decisions` rather
than hiding the route.

Keep proposal cards and `/proposals/:id` links. Do not remove or silently redirect the direct detail
page. If review presentation/decision markup is extracted into shared components, retain behavior
and focused tests for both consumers; otherwise accept limited duplication rather than creating a
premature generic workflow abstraction.

### 11. Accessibility and responsive behavior

Use semantic headings, lists, forms, labels, buttons, `<progress>` or an equivalent accessible
progress description, `role=status`, `role=alert`, and `aria-live` for queue changes. Move focus to
the newly selected proposal heading after explicit navigation or successful advance without
stealing focus during background refresh.

Previous/next controls must be native buttons and expose disabled reasons. Status and decision
meaning require text, not color alone. Confirmation remains keyboard-operable and cannot be
bypassed by a shortcut. Add responsive CSS so content and decision controls collapse to one column
without horizontal overflow at the repository's narrow breakpoint.

### 12. Backend test coverage

Add focused tests for:

* review policy defaults and invalid configuration;
* controller page parameters, standard 400/404 behavior, and serialized contract;
* deterministic proposal status/source/time/UUID ordering;
* counts independent of page;
* custom page metadata;
* payload preview within/over byte bound and stable digest;
* Unicode-safe evidence-content truncation;
* Fact/Observation deduplication, order, bounds, missing and mismatch outcomes;
* Validation/Insight batch association;
* constant repository interaction counts;
* existing proposal list/detail compatibility;
* lock-aware accept/reject unit behavior;
* real PostgreSQL concurrent decisions and promotion uniqueness.

Update existing Validation tests to mock the lock query explicitly. Keep generic proposal,
Validation, Insight, deliverable, and project-deletion tests passing.

### 13. Frontend test coverage

Add Angular tests for:

* service HTTP contract;
* Analysis review entry action and completed-state copy;
* loading, empty, error, page, and completion states;
* stable current proposal across refresh;
* pending-first initial selection and previous/next navigation;
* safe proposal/payload/evidence rendering;
* reviewer session generation, validation, reuse, replacement, clear, and storage failure;
* acceptance with severity and rejection without severity;
* explicit confirmation and duplicate-click suppression;
* success refresh/advance and proposal-form reset;
* ordinary failure state retention;
* 409 refresh without retry and visible persisted outcome;
* completed proposal read-only decision/Insight display;
* semantic labels/live regions/focus behavior;
* direct proposal-detail route compatibility;
* no manual production subscriptions where repository conventions forbid them.

Run changed-file Prettier checks before complete frontend validation.

### 14. Documentation reconciliation

Update canonical documentation after behavior is implemented:

* `README.md` — shortest Analysis → guided review → Insights → Deliverable flow, API, individual
  decision invariant, and local reviewer disclaimer;
* `docs/ui-ux.md` — queue interaction, progress, confirmation, conflict, completion, focus, and
  responsive behavior;
* `backend/.../proposal/README.md` — deterministic Analysis review projection and preserved
  proposal lifecycle;
* `backend/.../validation/README.md` — row-lock concurrency behavior and unchanged immutable
  decision boundary.

Inspect `docs/architecture.md`; update it only if implementation changes the documented read-model
boundary. Do not create an ADR unless implementation introduces a material architectural decision
beyond this approved conventional projection.

Record the exact documentation outcome in the Implementation Report.

### 15. Complete validation and live comparison

Run:

* focused backend tests;
* `./mvnw -q clean verify` including JaCoCo and PostgreSQL integration;
* focused frontend tests;
* `npm test -- --watch=false`;
* `npm run build`;
* Prettier on all changed frontend files;
* authenticated SonarQube with `-Dsonar.qualitygate.wait=true`;
* `docker compose up -d --build backend frontend`;
* live API and UI review against a safe multi-proposal Analysis;
* `git diff --check` and repository-status review.

The live comparison should demonstrate:

* one workspace entry rather than one proposal route entry per decision;
* one explicit reviewer UUID setup per browser session;
* zero forced Analysis returns between decisions;
* exactly one confirmation and one Validation write per decided proposal;
* correct persisted accepted/rejected counts;
* exactly one Insight per accepted Insight proposal and none for rejected proposals;
* direct proposal-detail routes remain functional;
* a repeated/concurrent decision cannot overwrite the winner.

Do not claim that faster review proves proposal correctness or general productivity improvement.

## Planned File Areas

Expected backend additions or modifications:

* `proposal/review/**` DTO, policy, service, and controller classes;
* proposal, Fact, Observation, Validation, and Insight repositories;
* `ValidationServiceImpl`;
* application properties;
* focused unit/WebMvc/integration tests.

Expected frontend additions or modifications:

* application routes;
* Insight/proposal models and service;
* Analysis Insights section;
* proposal review page/template/styles/tests;
* reviewer session service/tests;
* direct proposal page only if shared presentation extraction is justified.

Expected documentation changes:

* README;
* UI/UX documentation;
* proposal and Validation module READMEs;
* Story lifecycle artifacts.

## Database Impact

No schema migration is planned. Existing proposal Analysis indexes and the unique Validation
constraint remain sufficient for the normal bounded workload. Pessimistic row locking uses the
existing proposal primary key. Add a Flyway index only if the measured PostgreSQL query plan shows
a concrete regression; such a deviation must be documented in the Implementation Report.

## Security and Trust Impact

No authentication or authorization is added. The local reviewer UUID remains user-controlled,
session-scoped convenience data and must be labelled accordingly. The Core still persists the UUID
for traceability but cannot assert that it belongs to an authenticated person.

AI text remains rendered as text/JSON through Angular bindings. No `innerHTML` or executable
payload rendering is allowed. Review projection ownership checks prevent cross-Analysis evidence
confusion. No credentials or repository content access is introduced.

## Plan Risks

* A combined projection can become large; paging and per-field bounds prevent unbounded transport.
* Pessimistic locking can block briefly; one-row scope and no retry loop keep the boundary narrow.
* Session storage is unavailable in some browser/privacy contexts; memory fallback keeps the page
  usable without inventing identity.
* Extracting shared components too early could increase complexity; reuse only when two real
  consumers benefit without diverging behavior.
* The current public proposal creation request has weak payload bounds; Story 0020 bounds its new
  review projection without changing legacy creation semantics. Hardening proposal creation remains
  separate unless validation proves it is required for safety.

## Acceptance-Criteria Traceability

* AC-1, AC-2, AC-7: Analysis route, deterministic queue, progress, advance, completion.
* AC-3, AC-9: bounded Core review projection and batched evidence hydration.
* AC-4, AC-5: unchanged individual Validation API and promotion semantics.
* AC-6: explicit session-local reviewer service and disclaimer.
* AC-8: pessimistic proposal lock, refresh-on-conflict, no retry.
* AC-10: semantic interaction, announcements, focus, responsive CSS.
* AC-11: retained APIs/routes and compatibility suites.
* AC-12, AC-13: focused backend/frontend matrices.
* AC-14: disposable before/after multi-proposal workflow validation.
* AC-15: canonical docs, complete suites, JaCoCo, build/format, Sonar, Docker, hygiene.

## Recommendation

Approve this Implementation Plan and proceed to implementation.
