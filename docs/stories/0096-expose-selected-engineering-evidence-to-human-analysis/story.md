# Story 0096 - Expose Selected Engineering Evidence to Human Analysis

## Status

**IMPLEMENTATION_COMPLETE**

**HUMAN_BACKEND_REVIEW_APPROVED**

**ANGULAR_CHECKPOINTS_A_TO_J_COMPLETE**

**FINAL_HUMAN_REVIEW_APPROVED**

**STORY_COMPLETE**

## Priority

**P0 - FIRST SLICE**

## Objective

Let a human reviewing an Analysis answer:

> What context did DevLog actually give the model for this Analysis?

Expose the persisted execution-time selected-evidence snapshot already stored on the default
displayed `AiTask` as a structured, readable section of the Analysis detail experience. The human view must
preserve the snapshot's evidence categories and provenance-bearing details, distinguish supplied
context from newly generated AI synthesis, and state honestly when historical evidence is empty or
unavailable.

This Story is the first P0 delivery slice of Human Context Supremacy, governed by the Accepted and
amended `ADR-063`.

## Human Story

As a human engineer reviewing an Analysis,
I want to inspect the exact evidence snapshot selected for the displayed AI Task execution,
so that I can evaluate whether the output was grounded in the right project knowledge before I
trust, reject, or act on that output.

## Problem

The Core already persists the prompt-safe selected-knowledge projection in
`AiTask.selectedKnowledgeSnapshot`, together with its selection version and digest. The backend's
generic AI Task response currently transports that JSON map, but the Analysis detail page does not
present it as human-usable evidence. The page instead exposes execution metadata, generated
Insights, and a separate current Analysis Context JSON dump.

This creates an auditability gap:

* humans cannot readily see which Facts, Observations, prior Insights, architecture knowledge,
  Engineering Events, human context, evolution context, or repository evidence were supplied;
* current project/Analysis context can be mistaken for the historical context used at execution;
* a null legacy snapshot and an available snapshot with an empty category are not meaningfully
  distinguished;
* exposing the raw persisted map directly would couple the Angular UI to an internal prompt
  projection rather than establish a stable human read contract.

## Scope

### In Scope

* An Analysis-scoped backend read capability that projects the default displayed AI Task's persisted
  `selectedKnowledgeSnapshot` into a dedicated, stable human evidence response.
* Reuse of the persisted snapshot only. The read path must not collect, select, rank, enrich,
  refresh, or otherwise recompute context.
* Explicit response semantics for an available snapshot, a recorded but empty category, a historical
  category not recorded by that snapshot, no associated AI Task, a pending snapshot attachment, and
  a terminal AI Task whose snapshot is unavailable.
* Selection identity suitable for audit: Analysis ID, AI Task ID, selection version, selection
  digest, and the snapshot's own selection metadata when present.
* Category-preserving projection and human-readable presentation for:
  * selected Facts;
  * selected Observations;
  * selected prior Insights;
  * existing architecture knowledge;
  * selected Engineering Events;
  * selected human context inputs;
  * evolution context;
  * RepositoryContext evidence.
* Snapshot-level project, Analysis, project profile, diagnostics, repository warnings/digest, and
  completeness metadata where persisted and useful to interpret the selection.
* A structured evidence section in the existing Angular Analysis detail experience, with category
  labels, counts, readable entries, and explicit empty/unavailable states rather than a raw JSON
  dump.
* Clear visual and textual separation between "context supplied to the model" and AI synthesis
  produced by the Analysis.
* Safe rendering of repository content, human-authored Markdown, evidence references, symbols, and
  all other persisted strings as untrusted text unless they pass through an existing safe renderer.
* Backend and frontend tests for projection fidelity, association/isolation, state semantics,
  category rendering, safety, responsiveness, and accessibility.

### Persisted Source Boundary

The authoritative source for this view is the selected-knowledge snapshot persisted on the AI Task
at Analysis execution time. Repository analysis must confirm the exact stored schema, but the
current projection includes these top-level structures:

* `project`, `analysis`, `projectProfile`, and `diagnostics`;
* `selectedFacts` and `selectedObservations`;
* `selectedInsights`;
* `existingArchitectureKnowledge`;
* `selectedEngineeringEvents`;
* `selectedHumanContextInputs`;
* `repositoryContext`;
* `evolutionContext`;
* `selectionMetadata` and `selectionDigest`.

The human read projection must expose only fields actually persisted in that snapshot. It must not
infer omitted selector internals. In particular, current RepositoryContext snapshot evidence
preserves `layer`, `kind`, `reference`, `summary`, `occurredAt`, `relatedReferences`, bounded
`content`, and `symbols`, but does not preserve per-item relevance scores, selector reasons, or the
full internal retrieval candidate provenance. Those absent values must not be fabricated.

### AI Task Ownership and Default Selection

Selected evidence belongs to the specific `AiTask` execution that received it, not directly to an
Analysis as one permanent canonical evidence set. The response and UI must identify that AI Task.

For this first Analysis-detail slice, the backend resolves the default displayed task using the
existing deterministic ordering:

```text
createdAt DESC
id DESC
```

This is a presentation/default-selection convention only. It does not create a primary or canonical
Analysis task, a uniqueness rule, or a claim that older tasks are irrelevant. The read model remains
task-specific so future task-specific evidence navigation can reuse it without changing evidence
ownership. Manual navigation between tasks is outside this Story.

## Required Human Experience

The Analysis detail page provides a clearly named section such as **Evidence supplied to the
model** near the AI execution and generated Insight surfaces.

The section must:

1. Explain that it is the persisted execution-time snapshot selected for the identified AI Task, not
   a view of current project knowledge or a permanent Analysis evidence set.
2. Show the AI Task identity and associated selection version and digest when available.
3. Group items by their persisted evidence category rather than flattening them into one generic
   list.
4. Show a count and an honest empty state for each supported category.
5. Present the fields needed to identify and understand each item, including source/reference,
   timestamps, evidence references, policy/status metadata, or symbols when those fields are
   present in the persisted item.
6. Keep long content and technical details inspectable without making the default Analysis page
   unusable; progressive disclosure is permitted, but category existence and counts must remain
   visible.
7. Preserve usable semantics and navigation at desktop and mobile widths.
8. Use semantic headings, lists or description structures, keyboard-operable disclosure controls,
   and accessible loading/error/status messaging.
9. Keep generated Analysis Insights/proposals outside this supplied-evidence section and label both
   surfaces so a human cannot reasonably mistake evidence input for AI output.

## State Semantics

The read contract and UI must distinguish these cases:

* **Available with entries:** render persisted category entries.
* **Recorded category empty:** a present category key with `[]` means that the snapshot recorded the
  category and selected no items. This does not claim that the project had no such evidence.
* **Category not recorded:** a missing historical category key means `NOT_RECORDED`, not empty and
  not evidence that zero items were selected.
* **Available, snapshot globally empty:** state that an evidence snapshot exists but selected no
  displayable evidence, while retaining available selection metadata.
* **Snapshot pending:** a nonterminal AI Task with a null snapshot means `SNAPSHOT_PENDING`; selection
  attachment may still be in progress.
* **Snapshot unavailable:** a terminal AI Task with a null snapshot means `SNAPSHOT_UNAVAILABLE`.
  Do not show current context as a substitute.
* **No AI Task:** retain a distinct message that no AI Task exists for the Analysis.
* **Loading or failed read:** malformed/contradictory snapshots and infrastructure failures use
  `READ_FAILURE` through the existing sanitized API/UI error conventions. Do not replace corruption
  or a failed read with empty evidence, and do not log evidence bodies.

Legacy rows are expected: migration `V27__add_selected_knowledge_snapshot.sql` permits snapshot,
selection version, and selection digest to be null together. No data backfill is required by this
Story.

## Acceptance Criteria

### AC-1: The response is based only on historical persisted selection

Given an Analysis whose default displayed AI Task has a persisted selected-knowledge snapshot,
when its human evidence projection is requested,
then the response is derived from that task's stored snapshot and identifies the Analysis and AI
Task. When multiple tasks exist, the first slice selects the newest by `createdAt DESC, id DESC`.

The read performs no context collection, selection, ranking, repository inspection, MCP call, or
snapshot mutation. A later change to project knowledge does not change the historical response.

### AC-2: A dedicated human read contract shields the client from raw prompt JSON

The backend exposes a typed, documented projection designed for human inspection. The Angular
client does not parse arbitrary `Map<String, Object>` structures or render
`selectedKnowledgeSnapshot` with the JSON pipe.

The contract is additive and bounded to this Analysis evidence use case. It is not introduced as a
universal Evidence, KnowledgeReference, ContextPack, or MCP DTO.

### AC-3: Analysis and project association are enforced

The capability resolves evidence through the requested Analysis and its newest AI Task using the
existing deterministic ordering. It cannot return a task or evidence snapshot belonging to another
Analysis or project through a caller-supplied task identifier. Snapshot Analysis/project identities,
when recorded, must agree with the owning Analysis or the read fails closed.

Missing Analysis and invalid/missing association behavior follows existing API error conventions
and is covered by controller/service tests.

### AC-4: Categories remain distinct and faithful

The response and UI preserve distinct sections for Facts, Observations, prior Insights, existing
architecture knowledge, Engineering Events, human context inputs, evolution context, and repository
evidence. Entries retain the meaningful identity, content, provenance/reference, status/policy, and
temporal fields present in the persisted source.

No category is silently merged into another, and no absent score, reason, trust claim, or provenance
field is invented.

### AC-5: Repository evidence remains inspectable

For each persisted RepositoryContext evidence item, the human can inspect its kind, layer,
reference, summary, occurrence time, related references, and available bounded content/symbol
details. Repository context version, digest, and warnings are visible when present.

Content or symbol omission/truncation status and persisted policy metadata remain understandable;
the UI does not imply that omitted content was supplied to the model.

### AC-6: Selection identity and limitations are explicit

Selection version, selection digest, selected/discarded counts, applied rules, budget, completeness,
and collection diagnostics are exposed when persisted. Technical metadata may use progressive
disclosure.

The page explicitly states that this is historical Analysis-time context. An empty selected category
is described as "none selected", not "none exists in the project".

### AC-7: Input evidence and generated synthesis are unambiguous

The supplied-evidence section is visually and semantically separate from generated Analysis
Insights/proposals. Prior selected Insights and architecture knowledge are labeled as pre-existing
knowledge supplied to the model, not as output generated by the current execution.

### AC-8: Unavailable and empty states are honest

The API and UI represent all states defined under **State Semantics** without substituting current
`AnalysisContext`, current project state, or a newly computed selection. A terminal task's legacy null
snapshot does not fail the whole Analysis page. Missing historical category keys remain
`NOT_RECORDED`, recorded
empty arrays remain empty, nonterminal null snapshots remain pending, and malformed/contradictory
snapshots produce a sanitized read failure.

### AC-9: Rendering is safe, accessible, and responsive

Persisted content is escaped by default. Test fixtures containing HTML/script-like repository or
human-context text render as text and create no executable DOM nodes.

The section has an accessible heading hierarchy, keyboard-operable controls, meaningful status/error
announcements, and remains readable at the repository's supported mobile and desktop widths.

### AC-10: Existing behavior remains compatible

Existing AI Task execution metadata, polling, generated Insight/proposal workflows, and direct
diagnostic routes continue to work. The new capability is additive and does not require MCP from
Angular or change AI execution behavior.

## Test Strategy

### Backend

* Service tests prove projection from the persisted snapshot and verify that no context-building or
  selection dependency participates in the read.
* Projection tests cover every supported category and representative nested RepositoryContext
  content/symbol metadata.
* Tests cover available populated, recorded-empty, `NOT_RECORDED`, globally empty,
  `SNAPSHOT_PENDING`, terminal `SNAPSHOT_UNAVAILABLE`, no-task, V1-V4-compatible shapes, unknown
  extra keys, malformed/unknown-version reads, missing Analysis, and mismatched association cases.
* A multiple-task test proves the newest task is selected and identified without creating canonical
  Analysis-task semantics.
* Controller tests lock the response shape, status/error semantics, and Analysis-scoped identifiers.
* A persistence/integration test proves a stored snapshot is returned unchanged in meaning after
  underlying project knowledge changes.

### Frontend

* Service/model tests cover the typed human evidence response and backend error handling.
* Component tests verify category labels/counts, representative evidence details, selection
  metadata, and separation from generated Insights.
* Component tests distinguish every empty/unavailable state and preserve retry behavior.
* Security tests verify HTML/script-like repository and human-context content is rendered as inert
  text.
* Accessibility and responsive tests cover semantic structure, disclosure controls, focus/keyboard
  behavior, and narrow viewport presentation.

### Regression

* Existing backend and frontend suites pass.
* Angular production build and repository formatting/quality checks pass.
* No existing API contract is broken.

## Architectural Constraints

* **ADR_REQUIRED = NO.** This Story implements the first bounded delivery slice of amended,
  Accepted `ADR-063`; it does not introduce a new architecture decision.
* **Retrieval Changes = NONE.** Existing retrieval, selection, ranking, budgets, floors, and prompt
  construction remain unchanged.
* **RepositoryContextEngine = UNCHANGED.** The Story consumes no live RepositoryContextEngine output.
* PostgreSQL/Core persistence remains authoritative for which snapshot was used by an AI Task.
* Java Core owns the stable human read projection and Analysis/task association semantics.
* Angular owns presentation only and must call the Core backend directly, never MCP.
* The persisted prompt projection is historical evidence. Current `EngineeringContext`, current
  `AnalysisContext`, or current project state is not an acceptable fallback.
* The smallest focused projection and UI that satisfy these criteria are preferred over a generic
  evidence framework.

## Out of Scope

* Re-running, refreshing, enriching, re-ranking, or recomputing selected knowledge.
* Changes to `SelectedKnowledge`, `SelectedKnowledgeService`, prompt composition, prompt templates,
  provider payloads, or AI Task execution.
* Changes to RepositoryContext retrieval, `RepositoryContextEngine`, evidence ranking, category
  floors, relevance scoring, budgets, or selection reasons.
* Exposing current `EngineeringContext` or current `AnalysisContext` as though it were execution-time
  evidence.
* Persisting a second evidence snapshot, changing snapshot schema, backfilling legacy AI Tasks, or
  adding a new database migration.
* Raw JSON display as the primary human experience.
* A universal Evidence DTO, KnowledgeReference abstraction, ContextPack, RAG/vector retrieval,
  indexing platform, or MCP schema redesign.
* New MCP tools/resources or Angular-to-MCP communication.
* Comparing evidence across Analyses or AI Task attempts.
* Manual navigation between an Analysis's multiple AI Tasks, a canonical/primary Analysis task, task
  uniqueness, or retry implementation.
* Editing, annotating, approving, rejecting, exporting, or sharing selected evidence.
* Revealing rendered prompts, provider requests, credentials, hidden system instructions, or
  internal candidate pools not present in the persisted snapshot.
* Authentication, authorization, roles, or a new project-permission architecture. Existing
  ownership/isolation conventions still apply.
* Changes to Insight/proposal validation, project knowledge lifecycle, duplicate detection,
  freshness maintenance, or Engineering Event grounding.
* Broad redesign of the Analysis detail page outside what is needed to make this evidence readable.

## Definition of Done

* All acceptance criteria are implemented and verified.
* The backend provides the stable Analysis-scoped human evidence projection from persisted data only.
* The Angular Analysis detail view presents all supported categories with clear source/output and
  historical/current distinctions.
* Legacy, empty, missing, loading, and error states are explicit and tested.
* Project/Analysis isolation and safe rendering are tested.
* Focused and complete backend/frontend tests, Angular production build, formatting, and required
  repository quality gates pass.
* API/UI documentation is updated only where required by the implemented contract and experience.
* No retrieval, persistence, RepositoryContextEngine, MCP, or ADR scope has been introduced.

## Dependencies and References

* `docs/decisions/ADR-063.md` - Accepted governing architecture and Human Context Supremacy
  amendment.
* `docs/investigations/human-engineering-context-supremacy.md` - approved investigation and first
  P0 slice rationale.
* `AiTask.selectedKnowledgeSnapshot`, `selectionVersion`, and `selectionDigest` - authoritative
  persisted execution-time source.
* `SelectedKnowledgePromptProjectionService` - current prompt-safe persisted projection boundary.
* Existing Analysis detail AI execution and generated Insight surfaces - host experience to extend,
  not redesign.

## Expected Deliverables

* `story.md`
* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
* focused backend/frontend implementation and tests as approved by the plan
* contract and UI documentation updates required by the implementation
