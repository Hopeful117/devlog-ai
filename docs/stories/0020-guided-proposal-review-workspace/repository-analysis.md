# Repository Analysis — Story 0020

## Status

Ready for human approval.

## Executive Summary

Story 0020 is feasible and should be implemented as a focused Analysis-level review read model plus
an Angular guided queue. The existing validation write path is already the correct business
authority and must remain proposal-specific. The missing capability is a bounded, deterministic
read contract that assembles the information needed for review without forcing Angular to perform
N+1 orchestration, followed by an interaction that keeps the reviewer inside one Analysis until
all pending proposals are decided.

The current user cost is structural rather than cosmetic. `AnalysisInsightsSection` lists
proposals, but every decision requires opening `/proposals/:id`, generating or entering a reviewer
UUID, confirming one decision, navigating back to the Analysis, locating the next proposal, and
repeating. Supporting Facts and Observations are displayed only as raw UUIDs even though their
domain responses contain readable type/content/provenance. Summary and detail proposal TypeScript
contracts are currently identical, so the list API does not provide a purpose-built review model.

The recommended V1 introduces `GET /api/v1/analyses/{analysisId}/proposal-review` as a bounded,
paged read projection. It returns authoritative counts, stable proposal ordering, current-page
proposal review items, batched evidence summaries, persisted decision summaries, and resulting
Insight links. Angular consumes it through a dedicated Analysis review route or embedded workspace,
retains an explicitly generated reviewer UUID in browser `sessionStorage`, submits every decision
through the existing `POST /api/v1/validations`, and advances only after Core success.

No bulk action should be introduced. The efficiency gain must come from removing navigation,
identity repetition, and read-side fan-out—not from weakening human judgment.

## DevLog Context Outcome

The configured Story adapter was invoked with the complete Story 0020 body for project
`f3d56247-aada-4a76-982b-e6802c0b309c`. It exceeded the adapter's three-second bound and returned:

`DEVLOG_CONTEXT_ERROR: DevLog request timed out. Repository Analysis continues without DevLog.`

Repository Analysis therefore used targeted direct inspection only. The current repository at
branch `main` is authoritative.

## Current Architecture

### Proposal read path

`ValidatableProposalController` exposes:

* `GET /api/v1/proposals/{id}`;
* `GET /api/v1/proposals/project/{projectId}`;
* `GET /api/v1/proposals/analysis/{analysisId}`.

`ValidatableProposalServiceImpl#getByAnalysisId` delegates to
`ValidatableProposalRepository#findByAnalysisId` and maps the full list. That repository method has
no explicit order and no page bound. The response already carries proposal payload, typed Insight
projection, confidence, supporting Fact/Observation IDs, evidence references, timestamps, Project,
and Analysis identity.

The endpoint does not include Validation details, resulting Insight identity, readable Facts, or
readable Observations. Building a rich review queue solely in Angular would therefore require the
proposal list, the Analysis Insights list, one Validation request for every decided proposal, and
additional evidence endpoints or new client-side joins. This is the N+1/coupling problem identified
by AC-9.

### Validation write path

`ValidationServiceImpl#validate` is transactional. It:

1. loads one proposal;
2. rejects non-`PROPOSED` state;
3. checks the unique Validation association;
4. maps and persists one Validation;
5. updates the proposal to `ACCEPTED` or `REJECTED`;
6. promotes an accepted Insight proposal with required severity;
7. returns the persisted Validation.

PostgreSQL enforces one Validation per proposal through `uk_validation_proposal_id`. Insight
promotion is part of the same transaction. This state machine and write API are correct ownership
boundaries and should not be replaced by a review-specific mutation endpoint.

There is one concurrency gap relevant to the new workspace. `findById` performs no explicit row
lock and the service relies on a status check plus an existence pre-check. Two concurrent
transactions may both observe `PROPOSED`; the database unique constraint will protect integrity,
but the loser is not guaranteed to receive the stable `RESOURCE_CONFLICT` behavior expected by the
UI. Story 0020 should serialize decisions on the proposal row with a focused pessimistic-write
repository query, while retaining the database constraint as final authority. A real PostgreSQL
test should prove one winner, one conflict, one Validation, and at most one Insight.

### Evidence read path

Facts and Observations already have structured domain responses:

* `FactResponse`: ID, Analysis, type, content, source, evidence references, detected time;
* `ObservationResponse`: ID, Analysis, type, content, rule identity, supporting Facts, created time.

Repositories support Analysis-scoped reads, but there is no coherent review projection and the
proposal page explicitly says detail routes are not exposed. Evidence hydration should therefore
occur inside a read-only Analysis-scoped application service using batched ID lookups and ownership
checks. It must not reinterpret, rank, or validate evidence.

### Angular review path

`AnalysisInsightsSection` performs separate proposal and Insight list requests and renders proposal
cards. Selecting a card opens `ProposalDetailPage`. That page safely displays AI text, loads the
proposal, Analysis Insights, and (for completed proposals) its Validation, then submits one accept
or reject request through `InsightProposalService`.

Useful foundations already exist:

* reactive forms;
* `exhaustMap` duplicate-action suppression;
* explicit immutable-decision confirmation;
* severity selection for acceptance;
* conflict refresh after HTTP 409;
* safe text/JSON rendering;
* semantic labels, live status/error regions, and responsive styling;
* links back to Analysis and resulting Insight.

The current page deliberately navigates back to the Analysis after every successful decision. No
browser storage abstraction exists; reviewer UUID generation is local to the detail component and
must be repeated for every proposal.

## Recommended V1 Design

### 1. Analysis proposal-review read projection

Add a focused read-only application boundary owned by the Analysis/proposal integration layer:

`GET /api/v1/analyses/{analysisId}/proposal-review?page=<n>&size=<n>`

The response should include:

* Analysis ID and Project ID;
* total/pending/accepted/rejected counts calculated authoritatively;
* deterministic ordering policy/version;
* page metadata and truncation/next-page information;
* proposal items with existing review fields;
* bounded supporting Fact summaries;
* bounded supporting Observation summaries;
* persisted Validation summary when decided;
* resulting Insight summary/link when accepted.

Ordering should be stable and review-oriented:

1. `PROPOSED` before completed proposals;
2. source index when present, preserving model output order;
3. creation time ascending;
4. proposal UUID ascending as final tie-breaker.

The AI Engine already limits one result to 20 proposals, but the public proposal creation API can
produce more. The endpoint should therefore be paged rather than assuming 20 is a universal
database invariant. The plan should choose a default page size aligned with the AI bound and a hard
maximum that remains safe.

The service should validate the Analysis first, derive its Project identity from the persisted
Analysis, and batch-load all supporting IDs for the current page. Any Fact or Observation from a
different Analysis must be excluded and reported as unavailable/mismatched, never silently
presented as supporting evidence. Missing referenced rows remain explicit.

### 2. Guided Angular workspace

Add an Analysis-scoped route such as `/analyses/:id/proposal-review` and expose one prominent Review
action from `AnalysisInsightsSection` when proposals exist. A dedicated route is preferable to
turning the already dense Analysis diagnostics page into a large state machine; it also supports
refresh, direct links, back/forward navigation, and responsive layout cleanly.

The workspace should:

* show counts and current position;
* default to the first pending proposal;
* expose previous/next navigation without route churn per proposal;
* render rationale, structured payload, evidence summaries, and traceability links;
* preserve decision form values on request failure;
* refresh the read projection after success or 409;
* advance to the next pending item only after success;
* show an explicit completion state and validated Insight/deliverable links;
* allow completed proposals to be inspected without re-enabling controls.

The existing `/proposals/:id` route should remain as the direct audit/detail route. Shared
presentational components may be extracted for proposal content and decision controls, but the plan
should avoid a premature generic workflow framework.

### 3. Session-local reviewer identity

Introduce a small Angular service around `sessionStorage` with one versioned key. It should:

* return no identity until the user explicitly generates or enters one;
* validate UUID shape on read and discard malformed data;
* store only the reviewer UUID, not comments, severity, decisions, or proposal content;
* permit replacement/clearing;
* handle unavailable storage without blocking review;
* label the value as a local unauthenticated MVP identifier.

`sessionStorage` matches the Story's active-browser-session boundary better than `localStorage`.
This does not create identity, authentication, authorization, or audit trust beyond the existing
UUID field.

### 4. Deterministic concurrent-decision handling

Add a proposal repository method using `@Lock(PESSIMISTIC_WRITE)` for the validation write path.
After acquiring the row lock, Core rechecks persisted proposal status and Validation existence.
The first decision commits normally; a waiting competitor observes the decided state and receives
the standard conflict response. The unique Validation constraint remains defense in depth.

Do not add retries, last-write-wins behavior, decision reversal, optimistic UI acceptance, or a
batch validation endpoint.

## Affected Components

### Backend

Likely affected:

* Analysis/proposal review controller and DTO package;
* a new read-only proposal-review service;
* `ValidatableProposalRepository` deterministic/page and lock queries;
* batched `FactRepository`, `ObservationRepository`, `ValidationRepository`, and
  `InsightRepository` lookup methods as required;
* `ValidationServiceImpl` lock-aware proposal resolution;
* focused service, WebMvc, repository, concurrency, and compatibility tests.

The existing proposal list/detail and Validation operations should remain compatible.

### Frontend

Likely affected:

* routes;
* `InsightProposalService` and review DTO models;
* `AnalysisInsightsSection` review entry point;
* new proposal-review page/component/template/styles/tests;
* a small session reviewer identity service and tests;
* shared proposal presentation/decision components only if extraction reduces real duplication;
* direct proposal page tests for compatibility.

### Documentation

Likely affected:

* `README.md` human-validation workflow;
* `docs/ui-ux.md` Analysis result/review interaction;
* `docs/architecture.md` only if the Analysis review projection boundary needs canonical mention;
* proposal/validation module README files for deterministic queue and row-lock concurrency
  semantics.

An ADR is probably unnecessary because the design preserves existing Human Validation ownership
and adds a conventional read projection. If planning introduces durable reviewer identity, a new
write model, batch decisions, or a new lifecycle authority, that conclusion must be revisited.

## Existing-Solution Preflight

No new third-party workflow, state-management, grid, or form library is justified. Angular reactive
forms, Router, RxJS, native `sessionStorage`, Spring MVC/Data JPA, PostgreSQL row locking, and the
existing request-error model cover the required behavior. A generic review/workflow product would
add integration and trust complexity without replacing DevLog's domain-specific immutable
Validation boundary.

## Constraints and Invariants

* Every decision remains one explicit request for one proposal.
* Core and PostgreSQL remain authoritative for decision state and uniqueness.
* AI confidence never authorizes or defaults a decision.
* Severity is human-assigned on accepted Insights.
* Rejected proposals create no Insight.
* Accepted Insights and Validations remain immutable.
* Review projections are read models, not copied lifecycle state.
* Analysis and Project ownership must be checked for every projected item and evidence row.
* Payloads and pages require deterministic hard bounds.
* Existing audit/detail routes remain available.
* Authentication remains absent and must be communicated honestly.

## Risks and Mitigations

### Risk: convenience becomes implicit approval

Mitigation: retain explicit per-proposal accept/reject selection and confirmation; no bulk actions,
default decision, auto-submit, or confidence-driven behavior.

### Risk: read projection creates hidden N+1 behavior

Mitigation: page proposals, batch-load referenced decisions/Insights/Facts/Observations, and add
query-count or repository-interaction assertions appropriate to the chosen implementation.

### Risk: evidence from another Analysis is displayed as support

Mitigation: resolve evidence through Analysis-scoped queries or explicit ownership filtering and
return a bounded unavailable/mismatch outcome for invalid references.

### Risk: concurrent reviewers receive an opaque database failure

Mitigation: lock the proposal row during validation, recheck state after lock acquisition, retain
the unique constraint, and prove behavior with real PostgreSQL concurrency coverage.

### Risk: browser UUID is mistaken for authentication

Mitigation: session-only storage, explicit generate/replace action, persistent UI disclaimer, and
no role/permission claims.

### Risk: queue refresh loses the reviewer's place or form state

Mitigation: track proposal identity rather than array index, advance only on confirmed success,
retain the current item on failure, and deterministically select the next pending item after a
refresh or conflict.

### Risk: Story scope grows into proposal editing or Analysis comparison

Mitigation: keep proposal content immutable, preserve direct routes, and defer editing,
supersession, Analysis comparison, and freshness guidance.

## Testing Strategy

### Backend

Focused tests should verify:

* Analysis existence and Project scoping;
* stable pending-first/source-index/time/UUID order;
* authoritative counts independent of page size;
* page/default/hard bounds;
* batched Fact/Observation hydration and cross-Analysis rejection;
* missing evidence outcomes;
* Validation and Insight joins for decided proposals;
* unchanged proposal list/detail and validation APIs;
* accept/reject promotion behavior;
* real PostgreSQL concurrent decisions with one winner and one stable conflict;
* query behavior without per-item repository calls.

### Frontend

Angular tests should verify:

* entry from Analysis and direct route loading;
* queue progress, stable pending-first navigation, paging, and completion;
* safe rationale/payload/evidence rendering;
* session UUID generate/reuse/replace/malformed/unavailable-storage behavior;
* explicit individual confirmation;
* acceptance severity and rejection payloads;
* duplicate-click suppression;
* success advance only after response;
* request failure retains proposal and form;
* 409 refreshes authoritative state without retry;
* completed proposal read-only mode and Insight links;
* semantic labels, live regions, focus behavior, and narrow-layout CSS;
* compatibility of `/proposals/:id`.

### Complete validation

Run complete backend and frontend suites, JaCoCo, Angular production build, changed-file formatting,
authenticated SonarQube with Quality Gate wait, Docker rebuild, live API/UI review of a
multi-proposal Analysis, and `git diff --check`.

## Representative Validation Baseline

Before implementation, capture the current workflow for an Analysis with several proposals:

* number of proposal-route navigations;
* reviewer UUID generation/entry count;
* Analysis-return navigations;
* HTTP calls required to decide all proposals;
* final Validation and Insight counts.

The target is not fewer human decisions. It is one review workspace entry, one explicit reviewer
identity setup per browser session, no forced Analysis round trip between decisions, and the same
number of authoritative individual Validation requests as proposals decided.

## Open Questions Resolved by This Analysis

### Should V1 support bulk decisions?

No. It conflicts with the deliberate Human Validation invariant.

### Should the existing proposal-detail page be removed?

No. It remains the stable direct audit route and compatibility surface.

### Should reviewer identity use local storage?

No. Session storage better matches the explicitly temporary unauthenticated boundary.

### Can Angular assemble the whole review model from current APIs?

Technically yes, but only through coupled multi-request/N+1 orchestration with raw evidence IDs.
A focused bounded Core read projection is cleaner and more deterministic.

### Is an ADR required?

Probably not. The change preserves existing architecture and introduces a conventional read model.
The Implementation Plan must reassess if scope changes materially.

## Recommendation

Approve Story 0020 Repository Analysis and proceed to Implementation Planning with the recommended
bounded Analysis-level review projection, guided Angular queue, session-local reviewer identity,
and lock-aware individual validation path.

