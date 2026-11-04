# Repository Analysis - Story 0178

## Status

**ANALYSIS COMPLETE - AWAITING HUMAN APPROVAL**

This artifact analyzes the approved Story 0178. It does not authorize
Implementation Planning or production changes.

## Analysis Context

### Story

Story 0178 requests a bounded, loss-aware accounting model across Story Context
selection, enrichment, projection, grounding, prompt preflight, and provider
execution. Its purpose is to identify the dominant information-loss stage before
considering any retrieval-hybrid architecture.

### DevLog context

The repository contains `TOOLS.md`, but no exact repository-to-DevLog project
mapping or usable `devlog_devlog_story_agent` capability was available in this
session. Repository analysis therefore continued from current repository
evidence and the canonical Story 0178 investigation. No DevLog context is being
presented as validated evidence.

### Vault context

The curated Obsidian vault was not consulted. The repository contains sufficient
current architectural and implementation evidence for this Story, and no vault
note was required to resolve ownership or scope.

## Story Understanding

The Story does not ask to improve retrieval directly. It asks to make the path
from collected candidate to provider-visible evidence explainable, with bounded
stage outcomes and budgets. The intended output is an evidence-based decision
about whether the next bottleneck is selection, enrichment, projection,
preflight, provider execution, or retrieval recall.

The Story explicitly excludes:

- RAG, embeddings, vector search, and hybrid retrieval runtime changes;
- production budget increases;
- Python-side context reconstruction;
- weakening grounding, trust, scope, or revision rules;
- parsing incomplete provider output as success.

## Business Capability and Ownership

The affected capability is the Core-owned Engineering Story Context preparation
and execution boundary.

| Responsibility | Current owner | Relevant implementation |
|---|---|---|
| Candidate collection/ranking/selection | Java Core | `RepositoryContextEngine`, `DeterministicEvidenceRanker`, `BudgetedDiverseEvidenceSelector` |
| File/symbol enrichment | Java Core | `SelectedFileContentEnricher`, `SelectedJavaSymbolEnricher` |
| Repository context accounting | Java Core | `RepositoryContext`, `RepositoryContextDiagnostics`, selection decisions and warnings |
| Agent projection | Java Core | `AgentContextProjectionService`, `AgentRepositoryContext` |
| Prompt/provider preflight | Java Core | `StoryContextBudgetPreflight`, `StoryContextSubmissionService` |
| Provider execution and defensive validation | Python AI Engine | provider adapters, prompt processing, callback contract |
| Authoritative grounding/callback acceptance | Java Core | Story Context callback and grounding validation services |

No frontend or MCP implementation owner is required unless a later approved
plan explicitly exposes the ledger through those transports. The first
implementation should remain inside existing Core snapshot/trace/evaluation
boundaries.

## Existing Implementation

### Core retrieval and selection

`RepositoryContextEngine.build(...)` currently:

1. Builds a `ContextPlan` and `ContextRequest`.
2. Collects candidates from registered collectors.
3. Adds explicitly supplied candidates.
4. Ranks candidates deterministically.
5. Applies `EvidenceSelector`.
6. Enriches selected Java symbols.
7. Enriches selected file content.
8. Sorts selected evidence for the final `RepositoryContext`.
9. Computes diagnostics, discarded count, truncation, warnings, and a context
   digest.

Default repository context limits are configured in the constructor:

- maximum evidence items: `60`;
- maximum summary characters: `500`;
- maximum history items: `20`;
- maximum repository-context tokens: `6000`.

The current `RepositoryContext` already contains candidate count, selected
evidence, discarded count, truncation, selection decisions, warnings, used
tokens, budget, diagnostics, and context digest. It does not contain a complete
stage ledger or per-reference enrichment/projection/provider outcome.

### Selection behavior

`BudgetedDiverseEvidenceSelector` deduplicates references, then applies:

- requested-file selection;
- preferred-layer diversity;
- knowledge floor;
- category ceilings and kind allowance;
- relevance thresholds;
- item and token budgets;
- ordinary ranked selection.

Selection decisions already carry a reference, selected flag, reason, relevance
score, and estimated tokens. This is a reusable source for the selection part of
the ledger. The implementation must not replace these decisions or infer a new
selection universe.

### Enrichment behavior

`SelectedFileContentEnricher` operates only on selected source/test/configuration
evidence, with configuration content requiring requested-file relevance. It
allocates content budget before reading files and tracks:

- maximum enriched files;
- maximum characters per file;
- maximum total characters;
- remaining repository-context tokens;
- metadata reservation;
- workspace availability.

Enrichment returns `COMPLETE`, `TRUNCATED`, `SKIPPED`, or `UNAVAILABLE` content
states and warnings including:

- `CONTENT_ENRICHMENT_LIMIT_APPLIED`;
- `CONTENT_BUDGET_EXHAUSTED`;
- `CONTENT_ALLOCATION_METADATA_BUDGET_EXHAUSTED`;
- `CONTENT_ENRICHMENT_TRUNCATED`;
- `CONTENT_ENRICHMENT_UNAVAILABLE`;
- `CONTENT_ENRICHMENT_SKIPPED`.

`RepositoryEvidence.withContent(...)` recalculates estimated tokens, so the
ledger must capture before/after values rather than treating selection tokens as
the final content cost.

### Projection behavior

`AgentContextProjectionService` converts a `ProjectContextSnapshot` and a
`RepositoryContext` into `AgentEngineeringStoryContext`. It applies the
following limits by default:

- maximum canonical bytes: `32768`;
- maximum estimated tokens: `8192`;
- maximum reasons per evidence: `3`;
- maximum related references per evidence: `3`.

When the projection does not fit, it performs deterministic reduction in this
order:

1. remove related references;
2. compact reasons;
3. remove declarations;
4. remove content text;
5. compact summaries;
6. remove profile details;
7. compact human context inputs;
8. remove project-context lists;
9. reduce project context to minimal form;
10. remove tail evidence or all evidence as a last resort.

`AgentRepositoryContext.Accounting` already records canonical bytes, estimated
tokens, and counts of removed related references, reasons, declarations,
content payloads, and evidence items. Projection warnings identify reduction
steps. The missing capability is a bounded correlation from each source
reference to the reduction step and the prior pipeline stage.

### Prompt preflight and provider execution

`StoryContextSubmissionService` builds the authorized prompt, then calls
`StoryContextBudgetPreflight` before provider submission. The preflight measures:

- serialized input estimate;
- fixed prompt-policy overhead;
- effective output allowance;
- model context capacity;
- safety margin;
- policy and complexity class;
- output-limit source;
- accepted/rejected outcome.

It distinguishes `AUTHORIZED`, `PROJECTION_REDUCED`, and `REJECTED`, including
bounded failure codes such as `BUDGET_CAPACITY_UNKNOWN`,
`BUDGET_OUTPUT_ALLOWANCE_INSUFFICIENT`, and
`BUDGET_COMPLETION_ALLOWANCE_INSUFFICIENT`.

The current boundary already preserves context and projection digests and
records budget accounting in the execution snapshot. Story 0178 should add
loss attribution without duplicating prompt content or changing preflight
authority.

## Relevant Contracts and Persistence

The primary existing contracts are Java records and immutable snapshots:

- `RepositoryContext`;
- `RepositoryContext.SelectionDecision`;
- `RepositoryContextDiagnostics`;
- `AgentRepositoryContext`;
- `AgentRepositoryContext.Accounting`;
- `PromptRequest`;
- `StoryContextBudgetPreflight.Result`;
- Story Context execution snapshot and interaction-trace metadata.

The repository already distinguishes the immutable Core-authorized execution
snapshot from normal bounded diagnostics. ADR-079 permits the snapshot to retain
the exact context/projection needed for grounding and audit, while normal traces
must remain metadata-only. This distinction is required for any ledger design.

No new database entity is required by the Story as currently written. A plan
must decide whether bounded ledger summaries belong in existing snapshots,
interaction traces, evaluation artifacts, or an additive combination. That is an
implementation-planning decision, not resolved by this analysis.

## Architecture and Governing Constraints

The following accepted decisions govern the Story:

- **ADR-006:** AI output remains non-trusted and cannot become authoritative
  knowledge through diagnostics or evaluation.
- **ADR-063:** retrieval, composition, projection, grounding, and expansion are
  distinct responsibilities; composition and projection remain consumer-owned.
- **ADR-067:** Java/Core is the sole authority for context construction,
  scoping, authorization, grounding, and acceptance; Python receives immutable
  authorized input.
- **ADR-069:** projections may reduce or reorganize authorized context but may
  not broaden it; context, selection, and projection identities must remain
  unambiguous.
- **ADR-079:** selection, prompt input, provider output, model capacity, and
  safety margin are separate budget layers; unknown capacity fails closed and
  provider incomplete output is terminal.

No conflict was found between Story 0178 and these decisions. The Story is a
diagnostic/composition extension of the existing boundary, not a second
retrieval authority.

## Existing Tests and Coverage

Relevant existing tests include:

- `RepositoryContextServiceTest`: deterministic context assembly, truncation,
  discarded evidence, token budgets, context digest, and enrichment integration.
- `BudgetedDiverseEvidenceSelectorTest`: item/category/diversity/selection
  decision behavior.
- `SelectedFileContentEnricherTest`: complete/truncated/skipped/unavailable
  content, file limits, content budgets, and requested-file allocation.
- `SelectedJavaSymbolEnricherTest`: symbol allocation and truncation behavior.
- `AgentContextProjectionServiceTest`: deterministic projection, compaction
  order, removed payload accounting, tail evidence removal, digest stability,
  and fail-closed impossible projections.
- `StoryContextBudgetPreflightTest`: model capacity, projection-reduced state,
  output allowance, unknown capacity, and preflight rejection.
- Story Context submission, callback identity, grounding, and provider
  incomplete tests covering existing execution contracts.
- Story 0177 offline evaluator and corpus tests covering retrieval controls and
  safety filters.

Important missing coverage for Story 0178:

- one reference followed across all stages;
- stage-specific loss reason correlation;
- enrichment before/after token accounting in the same ledger;
- projection compaction correlated to the original selection decision;
- provider-visible reference set after prompt construction;
- aggregate loss metrics across targeted, broad, empty, multi-source, and
  truncated cases;
- redaction and bounded-size validation for the new ledger surface.

## Dependencies

Relevant dependencies are internal only for the initial implementation:

- Java Core repository-context pipeline;
- Story Context projection and preflight services;
- existing AI task/execution snapshot and interaction-trace metadata;
- existing Story 0177 evaluation corpus;
- existing JUnit/Maven test infrastructure.

Python changes should be unnecessary unless the final approved plan requires
consuming an additive diagnostic field. The Python engine must not calculate or
authorize the ledger.

## Worktree and Scope Constraints

The current worktree is modified before this Story's implementation:

- backend selection, ranking, enrichment, adapter, and related test files;
- `docker-compose.yml`;
- Story 0177 evaluation files;
- benchmark/notebook files;
- Playwright logs and OAuth-related artifacts.

These changes must not be reverted or silently included. In particular, the
current uncommitted requested-file prioritization and enrichment allocation
changes may affect observed selection outcomes. Any implementation plan must
define how to test against this state without attributing those changes to Story
0178.

## Risks

### Cross-stage identity drift

The same evidence can be represented by `RepositoryEvidence`, projected
`AgentRepositoryContext.Evidence`, grounding references, and provider payload
structures. Incorrect joining can produce false loss attribution. The existing
canonical reference and digest rules must be reused.

### Diagnostic payload growth

A per-reference ledger can itself become large. It must be bounded by counts,
reason enums, capped entries, and stable digests. Full prompts and repository
content must remain excluded from normal diagnostics.

### Snapshot versus trace semantics

Putting the ledger in the immutable snapshot improves auditability but may grow
the persisted artifact. Putting it only in normal traces improves persistence
cost but weakens replay explanation. This requires an explicit implementation
decision and bounded measurements.

### False causal conclusions

An evidence item being discarded does not prove it was answer-critical. The
evaluation must compare against expected evidence and grounding outcomes without
promoting evaluation labels to trusted project knowledge.

### Contract compatibility

The Python request contract uses strict validation in relevant paths. Any field
crossing the Core/AI boundary requires additive versioning and old/new
compatibility tests. The preferred initial design is Core/evaluation-side
accounting that does not change the provider request.

### Concurrent worktree changes

Existing uncommitted changes can alter selection and enrichment behavior. The
implementation must inspect and preserve them rather than applying assumptions
from the clean `HEAD` implementation.

## Missing Information and Decisions for Planning

Implementation Planning must resolve:

1. Whether the first ledger implementation is evaluation-only, Core snapshot
   accounting, interaction-trace metadata, or a bounded combination.
2. The maximum number of per-reference entries and the aggregate behavior when
   the ledger itself reaches its bound.
3. The exact definition of `provider-visible` when the provider receives a
   serialized prompt rather than a structured reference list.
4. Whether provider-visible references can be measured deterministically from
   the existing prompt builder without persisting prompt content.
5. Whether any additive wire-contract change is necessary. The current analysis
   finds no requirement for one.
6. Which live capture source, if any, may be used without secrets and with
   revision-pinned evidence.

These are implementation-level decisions within the approved Story scope. No
architectural conflict currently blocks planning, but the first two decisions
must not be silently guessed during implementation.

## Readiness Recommendation

The Story appears ready for Implementation Planning after human approval of this
Repository Analysis. The existing Core boundaries, contracts, tests, and
diagnostics provide reusable seams. The plan should begin with an evaluation/
Core-side ledger and avoid changing the provider request contract unless a
measured gap requires it.

## Validation Performed

- Read Story 0178 and its governing investigation.
- Read ADR-006, ADR-063, ADR-067, ADR-069, and ADR-079.
- Inspected current repository-context, enrichment, projection, preflight, and
  Story Context submission implementations.
- Inspected relevant selector, enricher, projection, repository-context, and
  preflight tests.
- Verified the repository contains unrelated pre-existing worktree changes.
- No production files were modified for this analysis.

## Next Gate

`WAITING_FOR_ANALYSIS_APPROVAL`

Explicit human approval of this current Repository Analysis is required before
Implementation Planning may begin.
