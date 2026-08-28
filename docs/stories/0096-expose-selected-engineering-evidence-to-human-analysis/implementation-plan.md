# Story 0096 - Implementation Plan

## Status

**APPROVED**

Human Implementation Plan Review: **APPROVED**

## Summary

Add one Analysis-scoped, read-only Core endpoint that resolves the newest `AiTask` by the existing
`createdAt DESC, id DESC` convention and projects only that task's persisted selected-knowledge
snapshot into category-specific human DTOs. Add one focused Angular component that loads this
projection once per observed newest-task readiness state and presents historical input evidence before
the generated Insight output.

The implementation does not collect, select, rank, enrich, refresh, or persist context. It does not
change task cardinality, create a canonical Analysis task, change the generic AI Task API, or involve
MCP.

## Execution Ownership

| Label | Meaning for this Story |
|---|---|
| `[AGENT]` | The agent may implement and verify the backend slice after plan approval. |
| `[HUMAN]` | The human implements the Angular checkpoint to learn the local Angular/TypeScript patterns. |
| `[PAIR/REVIEW]` | Human and agent review behavior, tests, security, or integration before proceeding. |

Angular checkpoints are deliberately incremental. The agent may explain concepts, review diffs,
diagnose failures, and suggest focused corrections, but should not replace the human implementation
unless the human explicitly changes that ownership decision.

## Governing Decisions

- `AiTask.selectedKnowledgeSnapshot` is the only evidence source.
- Evidence ownership is task-specific; the response always identifies the selected task when one
  exists.
- `GET /api/v1/analyses/{analysisId}/selected-evidence` accepts no task ID and selects the newest task
  by `createdAt DESC, id DESC` for this first Analysis-detail presentation.
- The ordering is not a uniqueness rule, primary-task marker, or domain invariant.
- Missing category key means `NOT_RECORDED`; present empty array means `RECORDED` with count zero.
- A nonterminal null snapshot is `SNAPSHOT_PENDING`; a terminal null snapshot is
  `SNAPSHOT_UNAVAILABLE`; no task is `NO_AI_TASK`.
- A valid non-null snapshot is `AVAILABLE`, including a globally empty snapshot.
- Malformed, contradictory, or unsupported-version data follows the existing sanitized 500 error
  path. `READ_FAILURE` is the Angular load-state meaning, not a successful backend response state.
- `selectionDigest` is displayed as an opaque selection digest/identifier, never as a snapshot
  checksum.
- Persisted strings, including `contentMarkdown`, are rendered as escaped plain text.
- ADR-063 governs the work. `ADR_REQUIRED = NO`.

## Exact Production Files

### New Backend Files

| File | Responsibility |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/dto/AiTaskSelectedEvidenceResponse.java` | Immutable outer response, task identity, snapshot metadata, category-specific sections, and typed item records. |
| `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidenceService.java` | Analysis-scoped read contract. |
| `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidenceServiceImpl.java` | Analysis/project resolution, newest-task selection, outer-state classification, and projector delegation. |
| `backend/src/main/java/com/hopeful117/devlogai/analysis/evidence/projection/HistoricalSelectedEvidenceSnapshotProjector.java` | Presence-aware, version-aware validation and strict-whitelist projection from persisted JSONB maps. |

### Modified Backend Files

| File | Change |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/analysis/controller/AnalysisController.java` | Inject the focused service and add `GET /{id}/selected-evidence`. |

No repository method is added. The implementation reuses
`AnalysisRepository.findWithProjectById` and
`AiTaskRepository.findFirstByAnalysisIdOrderByCreatedAtDescIdDesc`.

### New Frontend Files

| File | Responsibility |
|---|---|
| `frontend/src/app/features/analyses/ai-task-selected-evidence.models.ts` | Story-specific readonly TypeScript contract and discriminated unions. |
| `frontend/src/app/features/analyses/ai-task-selected-evidence-section.ts` | Focused standalone component and one-shot task-keyed reactive loading state. |
| `frontend/src/app/features/analyses/ai-task-selected-evidence-section.html` | Semantic state/category presentation and safe progressive disclosure. |
| `frontend/src/app/features/analyses/ai-task-selected-evidence-section.scss` | Focused responsive, wrapping, and disclosure styling. |

### Modified Frontend Files

| File | Change |
|---|---|
| `frontend/src/app/features/analyses/analysis.service.ts` | Add the Analysis-scoped selected-evidence GET. |
| `frontend/src/app/features/analyses/analysis-detail-page.ts` | Import the evidence component and expose the route Analysis ID to the template without manual subscription. |
| `frontend/src/app/features/analyses/analysis-detail-page.html` | Host evidence immediately after AI execution metadata and before generated Insights. |
| `frontend/src/app/features/analyses/analysis-detail-page.scss` | Only host spacing needed by the new section; category styling stays local to the child. |

Do not add the Story-specific contract to `analysis.models.ts`; a focused model file avoids growing
the existing broad task model and makes the raw-map boundary explicit.

## Backend Contract

### Outer Response

`AiTaskSelectedEvidenceResponse` has these bounded fields:

- `state`: `NO_AI_TASK`, `SNAPSHOT_PENDING`, `SNAPSHOT_UNAVAILABLE`, or `AVAILABLE`;
- `analysisId` and `projectId` from the owning persisted Analysis;
- nullable `task` identity with `id`, `taskType`, `status`, and `createdAt`;
- nullable `selectionVersion` and `selectionDigest` from the task columns;
- nullable typed `snapshotMetadata`;
- nullable typed `categories`, present only for `AVAILABLE`.

Static factory methods should create the four valid outer shapes and defensively copy all lists. The
Java response may use nullable fields for JSON serialization; the TypeScript model narrows those
fields with a discriminated union keyed by `state`.

### Category Contract

Use Option B from repository analysis: each named section has `availability`, `count`, and its own
typed items. `availability` is `RECORDED` or `NOT_RECORDED`. Do not introduce `GenericEvidence`, a
`kind + payload` union, arbitrary maps, or a universal context DTO.

The single DTO file contains compact nested records for:

- `FactsSection` / `FactItem`;
- `ObservationsSection` / `ObservationItem`;
- `PriorInsightsSection` / `PriorInsightItem`;
- `ArchitectureKnowledgeSection` / `ArchitectureKnowledgeItem`;
- `EngineeringEventsSection` / `EngineeringEventItem`;
- `HumanContextSection` / `HumanContextItem`;
- `EvolutionContextSection` / `EvolutionContextItem` and typed commit-diff children;
- `RepositoryEvidenceSection` / `RepositoryEvidenceItem`, content, symbols, declarations, parameters,
  and source locations.

The section record names stay category-specific even if constructors share a small internal helper
for count validation. A missing category key yields `NOT_RECORDED`, count zero, and an empty list. A
present empty list yields `RECORDED`, count zero, and an empty list.

`evolutionContext` and `repositoryContext` are persisted nullable objects rather than top-level
arrays. For these two categories:

- missing key means `NOT_RECORDED`;
- present null means `RECORDED` with count zero;
- present object means `RECORDED`, with count derived from its displayable item/value shape;
- wrong non-null type is malformed and fails the entire read.

### Snapshot Metadata Whitelist

Expose only typed fields already justified by repository analysis:

- persisted project identity, name, slug, description, and status;
- persisted Analysis identity, type, intent identity, status, and lifecycle times;
- project-profile identity/version, renderer version, generated time, requested revision,
  deterministic summary, characteristic count, and typed completeness counts/status;
- aggregate collection diagnostics;
- selection metadata version, applied rules, selected/discarded counts, typed budget, and
  completeness;
- repository context version, profile, warnings, and context digest.

Do not pass through project-profile `sections`, `sourceObservations`, or `resolvedRevisions` as
arbitrary maps. Do not expose `intentSnapshot`, `contextSnapshot`, user-guidance snapshots, prompt or
provider request bodies, correlation/provider internals, `externalJobId`, failure details, or any
field from the broad `AiTaskResponse` that is outside this contract.

## Backend Projection Rules

`HistoricalSelectedEvidenceSnapshotProjector` is a pure component with `ObjectMapper` as its only
dependency. It receives the persisted task selection version/digest, expected Analysis/project IDs,
and raw map, then returns typed metadata and categories.

The projector must:

1. Support the observed `knowledge-selection-v1`, `knowledge-selection-v2`,
   `knowledge-selection-v3`, and `knowledge-selection-v4` values.
2. Reject null/blank or unknown versions when a snapshot exists.
3. Interpret category presence from `containsKey`, not from version alone, because V4 shapes drifted.
4. Ignore unknown extra keys.
5. Validate every known present field's container and value type; do not coerce malformed known
   values to empty lists, zeroes, or strings.
6. Convert only whitelisted nested structures into category-specific records; never deserialize the
   map into current `SelectedKnowledge`.
7. Compare persisted snapshot `analysis.id` and `project.id` with the owning Analysis/project when
   those identities are present.
8. Compare embedded selection version/digest with task columns when embedded values are present.
   Equality is a consistency check only; do not recompute the digest.
9. Preserve repository content statuses `COMPLETE`, `TRUNCATED`, `SKIPPED`, and `UNAVAILABLE` and all
   persisted policy/version/allocation fields in the approved whitelist.
10. Throw a focused validation exception or `IllegalStateException` whose message names only task,
    version, and failing field path. Never include persisted evidence values or bodies in logs or
    exception messages.

The existing global exception handler supplies the sanitized `500 INTERNAL_ERROR` envelope. Do not
add a public corruption payload or partial-response mode.

## Backend Read Flow

`AiTaskSelectedEvidenceServiceImpl.getSelectedEvidence(UUID analysisId)` is
`@Transactional(readOnly = true)` and follows this exact order:

1. Resolve the Analysis with its project through `AnalysisRepository.findWithProjectById`; on miss,
   throw the existing `EntityNotFoundException` form for an Analysis.
2. Resolve the newest associated task through
   `findFirstByAnalysisIdOrderByCreatedAtDescIdDesc`.
3. If absent, return `NO_AI_TASK` with Analysis/project identity and no task.
4. Verify the returned task's Analysis/project association defensively.
5. If the snapshot is null and status is `CREATED`, `SUBMITTED`, or `PROCESSING`, return
   `SNAPSHOT_PENDING`.
6. If the snapshot is null and status is `COMPLETED` or `FAILED`, return
   `SNAPSHOT_UNAVAILABLE`.
7. If the snapshot exists, invoke the projector and return `AVAILABLE`, regardless of task lifecycle
   state.

The service constructor must contain only `AnalysisRepository`, `AiTaskRepository`, and the focused
projector. It must have no collection, selection, context, repository-workspace, Git, MCP, prompt, or
provider dependency.

## Implementation Slices

### Slice 1 - `[AGENT]` Typed Backend Contract

Files:

- Add `AiTaskSelectedEvidenceResponse.java`.

Work:

- Define the four outer states, category availability, task identity, whitelisted metadata, and all
  category-specific records.
- Enforce immutable list ownership and section count/list consistency.
- Keep all Story records in one DTO file unless Java readability makes a concrete split necessary;
  do not create a generic evidence hierarchy.

Completion signal: DTO compiles, contains no raw `Map<String,Object>` response field, and exposes no
forbidden AI Task/provider field. Covers AC2, AC4, AC5, and AC6.

### Slice 2 - `[AGENT]` Historical Snapshot Projector

Files:

- Add `HistoricalSelectedEvidenceSnapshotProjector.java`.
- Add its focused unit test listed under **Exact Test Files**.

Work:

- Implement strict V1-V4, presence-aware mapping and association validation.
- Build fixtures from the authoritative Java prompt projection test and observed historical shapes,
  not the stale Python fixture.
- Cover every category, nested repository content/symbol data, empty versus missing, unknown extras,
  malformed known fields, unknown versions, and identity contradictions.

Completion signal: all projector state/shape tests pass and failure messages contain no fixture
evidence content. Covers AC2-AC6 and AC8.

### Slice 3 - `[AGENT]` Analysis-Scoped Read Endpoint

Files:

- Add `AiTaskSelectedEvidenceService.java`.
- Add `AiTaskSelectedEvidenceServiceImpl.java`.
- Modify `AnalysisController.java`.
- Add/modify the service and controller tests listed below.

Work:

- Implement Analysis-first scope resolution, newest-task default selection, outer-state matrix, and
  projector delegation.
- Add `GET /api/v1/analyses/{id}/selected-evidence` returning `200` for valid outer states, existing
  `404` for missing Analysis, and existing sanitized `500` for read failure.
- Do not add a caller-supplied task ID, repository query, cache, ETag, or write.

Completion signal: focused service/controller tests pass, constructor dependency review proves the
no-recompute boundary, and existing Analysis routes remain unchanged. Covers AC1-AC3, AC8, and AC10.

### Slice 4 - `[AGENT]` Persistence and Historical Fidelity Proof

Files:

- Add the PostgreSQL integration test listed below.

Work:

- Persist a representative JSONB snapshot, read it through the service, mutate underlying project
  Facts/Insights/profile data, and prove the response meaning remains derived from the stored map.
- Persist two tasks for one Analysis with controlled equal/different timestamps and prove
  `createdAt DESC, id DESC` selection and returned task identity.
- Persist separate projects/Analyses/tasks and prove route association and snapshot identity mismatch
  failure.
- Include all-null legacy selection columns and malformed JSONB cases.

Completion signal: PostgreSQL integration tests prove persisted-only behavior and task/project
isolation without calling context construction. Covers AC1, AC3, AC8, and AC10.

### Slice 5 - `[PAIR/REVIEW]` Backend Boundary Review

Review before Angular work depends on the contract:

- response contains only approved fields;
- V1-V4 and same-version missing keys are presence-aware;
- no direct current-domain deserialization occurs;
- errors and logs contain identifiers/paths only, never evidence bodies;
- no forbidden service dependencies or writes exist;
- selection digest wording does not claim byte integrity;
- no migration, task-cardinality rule, or generic AI Task response change was introduced.

Completion signal: human approves the JSON contract and outer/category semantics.

## Human Angular Checkpoints

### Checkpoint A - `[HUMAN]` Model the Contract

File: add `ai-task-selected-evidence.models.ts`.

What and why: mirror the approved backend response with readonly category-specific interfaces and an
outer discriminated union. This lets TypeScript narrow `task`, `snapshotMetadata`, and `categories`
from `state`, rather than relying on non-null assertions.

Pattern/concept: discriminated unions; difference between absent, null, recorded empty, and
`NOT_RECORDED`; no `JsonValue`, `Record<string, unknown>`, or generic evidence payload.

Verify: `npx tsc --noEmit` through the repository's normal Angular build/test commands, plus model
fixtures that compile for every outer state. Review with the agent before proceeding. Covers AC2,
AC4, and AC8.

### Checkpoint B - `[HUMAN]` Add the HTTP Method

Files:

- Modify `analysis.service.ts`.
- Modify `analysis.service.spec.ts`.

What and why: add `getSelectedEvidence(analysisId)` as one typed GET to
`/api/v1/analyses/{analysisId}/selected-evidence`.

Pattern/concept: Angular `HttpClient` returns a cold Observable; the component owns when it executes.
The service must not read the raw snapshot from `AiTaskDetail`.

Verify: `HttpTestingController` asserts method, encoded Analysis route, typed fixture, and backend
error propagation. Covers AC2 and AC10.

### Checkpoint C - `[HUMAN]` Build the Reactive Shell

Files: add `ai-task-selected-evidence-section.ts`, initial HTML, and initial SCSS.

What and why: create a standalone child with required `analysisId` input and nullable newest-task
input. Convert input changes and an explicit retry trigger into a `LoadState` Observable consumed only
with `AsyncPipe`.

Pattern/concept:

- derive a stable key from newest task ID plus snapshot readiness/terminal state;
- use `distinctUntilChanged` and `switchMap` so a new task cancels stale reads;
- use `startWith` for loading, `catchError` for `READ_FAILURE`, and `shareReplay` only if the template
  has multiple consumers;
- do not use manual `.subscribe()` and do not start a timer in the child.

The stable key permits one request for no-task, one as a newly observed task is pending, another when
that same task gains selection identity or becomes terminal, and one for a replacement newest task.
Once `AVAILABLE` is loaded for a task key, ordinary parent polling emissions must not refetch it.

Verify: focused component tests assert request counts for repeated parent emissions, pending-to-ready,
terminal-unavailable, retry, and task replacement. Covers AC1, AC8, and AC10.

### Checkpoint D - `[HUMAN]` Render Outer States

File: extend the evidence component template/spec.

What and why: render loading, `READ_FAILURE` with retry, `NO_AI_TASK`, `SNAPSHOT_PENDING`,
`SNAPSHOT_UNAVAILABLE`, and `AVAILABLE` separately. Display response task identity rather than
assuming the input task remained newest across a request race.

Pattern/concept: Angular `@switch`, `role="status"`, `role="alert"`, and honest historical wording.
No state substitutes current Analysis Context.

Verify: one DOM test per outer state and retry behavior. Covers AC6, AC8, and AC9.

### Checkpoint E - `[HUMAN]` Render the First Typed Categories

File: extend the evidence component template/spec.

What and why: implement Facts and Observations first, showing section heading, availability, count,
identity, content, source/rule data, references, and timestamps.

Pattern/concept: `@for` with stable IDs, semantic lists/description lists, and interpolation. Empty
copy must say "none selected"; `NOT_RECORDED` must say the historical snapshot did not record that
category.

Verify: populated, recorded-empty, and not-recorded tests for both categories. Covers AC4, AC6, and
AC8.

### Checkpoint F - `[HUMAN]` Complete Knowledge Categories

File: extend the evidence component template/spec.

What and why: add prior Insights, existing architecture knowledge, Engineering Events, and human
context sections without flattening their distinct fields.

Pattern/concept: label prior Insights and architecture knowledge as pre-existing input. Render
`contentMarkdown` as plain interpolated text, not `[innerHTML]`, a Markdown renderer, or sanitizer
bypass.

Verify: representative details and category labels; hostile human Markdown remains text and creates
no executable/image/script DOM nodes. Covers AC4, AC7, and AC9.

### Checkpoint G - `[HUMAN]` Add Evolution and Repository Evidence

File: extend the evidence component template/spec/SCSS.

What and why: show evolution revision/diff metadata and repository context metadata, then each
repository item's layer, kind, reference, summary, occurrence time, related references, bounded
content, and symbols.

Pattern/concept: use native `<details>/<summary>` for long content, commit details, and symbols while
keeping category names/counts visible. Distinguish `COMPLETE`, `TRUNCATED`, `SKIPPED`, and
`UNAVAILABLE`; never imply absent content or omitted internal ranking fields were supplied.

Verify: all four content statuses, warnings/digest, declarations/source locations, long path/hash
wrapping, and script-like repository text. Covers AC4, AC5, and AC9.

### Checkpoint H - `[HUMAN]` Add Audit Metadata

File: extend the evidence component template/spec.

What and why: display task ID, selection version/digest, snapshot project/Analysis identity,
diagnostics, project-profile completeness, selected/discarded counts, budgets, completeness, and
applied rules under progressive disclosure.

Pattern/concept: separate concise human evidence from technical audit details. Label digest exactly
as "Selection digest"; do not use "checksum", "verified", or "integrity hash". Derive the globally
empty message only when no displayable category has entries while preserving metadata.

Verify: metadata fields, globally empty copy, and digest wording tests. Covers AC6 and AC8.

### Checkpoint I - `[HUMAN]` Integrate with Analysis Detail

Files:

- Modify `analysis-detail-page.ts`.
- Modify `analysis-detail-page.html`.
- Modify `analysis-detail-page.scss` only if host spacing is needed.
- Modify `analysis-detail-page.spec.ts`.

What and why: expose the route Analysis ID as a shared readonly Observable, import the child, and host
it after execution metadata and before `AnalysisInsightsSection`. Pass `tasks.data[0] ?? null`; do not
move snapshot parsing into the page.

Pattern/concept: parent orchestration versus focused child presentation; the existing task polling
continues to discover no-task-to-task and pending-to-ready transitions. Preserve existing execution,
diagnostics, and generated-output behavior.

Verify: DOM order proves supplied evidence precedes and is separate from "Insight Proposals" and
"Validated Insights"; existing polling, terminal-stop, and no-task tests remain green. Covers AC7 and
AC10.

### Checkpoint J - `[HUMAN]` Accessibility, Responsive, and Regression Pass

Files:

- Complete the evidence component spec and SCSS.
- Modify `frontend/tests/projects-flow.spec.ts` only if a deterministic seeded Analysis with selected
  evidence is available to Playwright; otherwise document why semantic component tests provide the
  reliable coverage.

What and why: finish semantic headings, lists/description lists, keyboard-operable native disclosure,
status/error announcements, visible focus, and mobile overflow behavior.

Pattern/concept: reuse global `.metadata-list` and focus-visible conventions; use
`overflow-wrap:anywhere` and bounded scrolling for code/content. Do not create custom disclosure
keyboard behavior when native details works.

Verify: keyboard toggles disclosure, headings have unique associations, narrow-viewport layout has no
page-level horizontal overflow, long content remains inspectable, and production build succeeds.
Covers AC9 and AC10.

## Exact Test Files

### New Backend Tests

| File | Required coverage |
|---|---|
| `backend/src/test/java/com/hopeful117/devlogai/analysis/evidence/projection/HistoricalSelectedEvidenceSnapshotProjectorTest.java` | Every category; V1-V4; same-version missing key; recorded empty; global empty; unknown extras; wrong types; unknown version; identity/version/digest contradiction; repository content/symbol fidelity; safe failure messages. |
| `backend/src/test/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidenceServiceTest.java` | Missing Analysis; no task; pending; terminal unavailable; available regardless of status; newest-task query/delegation; task association defense; exact dependencies. |
| `backend/src/test/java/com/hopeful117/devlogai/analysis/evidence/service/AiTaskSelectedEvidencePersistenceIntegrationTest.java` | PostgreSQL JSONB fidelity after source-data mutation; newest ordering including ID tie-break; project/Analysis isolation; legacy nulls; malformed persisted shape. |

### Modified Backend Tests

| File | Required coverage |
|---|---|
| `backend/src/test/java/com/hopeful117/devlogai/analysis/controller/AnalysisControllerWebMvcTest.java` | New success route and identifier-rich response; service delegation; existing routes remain compatible. |
| `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/ApiErrorHandlingWebMvcTest.java` | Add a focused malformed/read-failure case only if the existing generic 500 assertion does not already lock the sanitized envelope. |

### New Frontend Tests

| File | Required coverage |
|---|---|
| `frontend/src/app/features/analyses/ai-task-selected-evidence-section.spec.ts` | Outer states, all categories, metadata, recorded-empty versus not-recorded, global empty, request-key lifecycle, retry, safe rendering, semantics, disclosure keyboard behavior, and narrow-layout classes/behavior. |

### Modified Frontend Tests

| File | Required coverage |
|---|---|
| `frontend/src/app/features/analyses/analysis.service.spec.ts` | New GET contract and errors. |
| `frontend/src/app/features/analyses/analysis-detail-page.spec.ts` | Host inputs/order, no manual subscription regression, polling continuity, and output separation. |
| `frontend/tests/projects-flow.spec.ts` | Optional deterministic desktop/narrow viewport smoke coverage under Checkpoint J's fixture condition. |

Do not weaken existing assertions to accommodate the feature. Update test construction only where the
new controller dependency or standalone import requires it.

## AC Traceability

| Acceptance criterion | Implementation slices | Primary verification |
|---|---|---|
| AC1 persisted-only/newest task | 2-4, C | Service and PostgreSQL fidelity tests; task-key request-count tests. |
| AC2 stable typed read contract | 1-3, A-B | DTO/projector/controller and HttpClient contract tests. |
| AC3 association/isolation | 2-4 | Analysis-first, identity mismatch, and cross-project tests. |
| AC4 distinct faithful categories | 1-2, E-G | Projector category matrix and DOM category tests. |
| AC5 inspectable repository evidence | 1-2, G | Nested projector fixture and repository DOM/status tests. |
| AC6 selection identity/limitations | 1-3, H | Metadata and wording tests. |
| AC7 input/output separation | F, I | Host DOM order and explicit input labels. |
| AC8 honest states | 2-4, C-E, H | Backend state matrix and frontend state/empty tests. |
| AC9 safe/accessible/responsive | F-G, J | Hostile text, semantic/keyboard, and narrow viewport tests. |
| AC10 compatibility | 3-4, B-C, I-J | Existing suites, request counts, build, and additive route tests. |

**AC mapping: 10/10.**

## Quality Gates

Run focused backend tests first:

```bash
cd backend
./mvnw -Dtest=HistoricalSelectedEvidenceSnapshotProjectorTest,AiTaskSelectedEvidenceServiceTest,AiTaskSelectedEvidencePersistenceIntegrationTest,AnalysisControllerWebMvcTest,ApiErrorHandlingWebMvcTest test
```

Then full backend verification:

```bash
cd backend
./mvnw clean verify
```

Run frontend gates after every human checkpoint as narrowly as practical, then run all gates:

```bash
cd frontend
npm run lint
npm run format:check
npm test -- --watch=false
npm run build
```

Run `npm run e2e` if Checkpoint J can use deterministic seeded evidence without making the test depend
on mutable local data. Otherwise record that limitation and retain focused responsive component tests.

Final repository checks:

```bash
git diff --check
git status --short
```

## Scope Guards

Implementation must leave these areas unchanged:

- `SelectedKnowledge`, `KnowledgeSelectionService`, and
  `SelectedKnowledgePromptProjectionService` behavior;
- `RepositoryContextEngine`, collectors, rankers, budgets, floors, repository adapters, Git/workspace,
  and current context construction;
- prompt composition, AI provider submission/callback, AI Engine, and MCP;
- `AiTask` entity/schema, Flyway migrations, snapshot attachment, and task cardinality;
- existing broad `AiTaskResponse` compatibility;
- authentication/authorization architecture;
- proposal/Insight validation and lifecycle;
- manual task navigation, retry implementation, comparison, editing, export, or sharing.

No new ADR, cache framework, generic evidence framework, Markdown renderer, sanitizer bypass, or
Angular polling loop is allowed.

## Residual Risks and Technical Debt

- **HIGH systemic:** the application has no authentication/authorization boundary. Story 0096
  narrows fields and enforces Analysis/task/project association but cannot establish caller identity.
- **RESIDUAL_TECHNICAL_DEBT:** existing AI Task polling continues to retransmit raw selection and
  context snapshots. This Story does not break the public task response; a future lightweight task
  summary may address payload size.
- Persisted V1-V4 JSONB has no schema constraint. The focused parser and fail-closed behavior limit
  exposure but do not repair rows.
- Persisted source/human text may itself contain sensitive project content. The endpoint must remain
  whitelist-only and same-scope; secret scanning/redaction is not introduced here.
- The selection digest is not a persisted-snapshot checksum and does not cover every displayed field.
- Snapshot update prevention is application-level rather than database-enforced; persistence
  hardening is outside scope.

## Documentation and Completion Artifacts

After implementation:

- create `docs/stories/0096-expose-selected-engineering-evidence-to-human-analysis/implementation-report.md`;
- update `docs/ui-ux.md` only with concise historical-input versus generated-output behavior if that
  document remains the active Analysis UI inventory;
- update API inventory documentation only if an existing inventory is identified during
  implementation;
- do not add or amend an ADR;
- record Playwright fixture limitations and the broad task-polling payload as residual debt rather
  than expanding implementation scope.

## Approval Checklist

- [x] Human approves newest-task default as presentation-only and task-specific ownership.
- [x] Human approves the four successful outer states and `READ_FAILURE` error handling.
- [x] Human approves category-specific DTO Option B and the strict field whitelist.
- [x] Human approves missing-key versus recorded-empty semantics.
- [x] Human approved backend `[AGENT]` ownership and subsequently delegated Angular checkpoints A-J
  to OpenCode.
- [x] No retrieval, recomputation, persistence, MCP, prompt, provider, or ADR change was introduced.
- [x] No raw map or universal evidence DTO crosses the new endpoint.
- [x] No caller-supplied task ID or canonical task invariant was introduced.
- [x] Safe plain-text rendering and responsive/accessibility verification are complete.
- [x] AC1-AC10 and all Definition of Done areas have implementation and test coverage.

Completion: **FINAL_HUMAN_REVIEW_APPROVED** - **STORY_COMPLETE**
