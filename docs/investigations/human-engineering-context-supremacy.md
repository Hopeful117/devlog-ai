# Human Engineering Context Supremacy

Investigation-only artifact. No production code, migration, ADR, Engineering
Story, commit, or remote operation was created.

Classification markers used throughout: **OBSERVED**, **INFERRED**, and
**PROPOSED**.

## Status

**READY_FOR_ARCHITECTURE_DECISION**

## Priority

**P0 - PRODUCT / GOVERNANCE GAP**

## Question

Why does the human investigation workflow currently expose less engineering
evidence than MCP/agent consumers, and how should DevLog provide a shared
engineering-context retrieval capability while preserving human authority?

## Repository State

- Branch: `main`.
- HEAD: `10e0e457abd7eeb28fbdd4ec5f01963b25ca1752`.
- Worktree: pre-existing generated/build changes under `ai-engine/app/api/__pycache__`
  and `devlog-contracts/target`, plus the unrelated untracked
  `docs/investigations/deterministic-repository-synchronization.md`.
- Worktrees: one, at `/home/ludo/Bureau/workspace/devlog-ai`.
- `git fetch`: attempted and failed because the SSH signing key agent refused
  the operation; the investigation therefore uses the locally available
  `main` and history.
- Relevant local history: `10e0e45` merge of Story 0095, preceded by
  `a1d3e41`, `0b62fd1`, `9b0e760`, `31345ed`, `a4d35fb`, `70e2ea3`, and
  `b12fc01`.

## Product / Governance Problem

**OBSERVED:** DevLog has no runtime aggregate, API, or Angular feature named
`Investigation`. The product workflow corresponding to a human engineering
investigation is the generic `Analysis` / Project Understanding workflow,
followed by proposal review and validation. Routes expose analyses, proposals,
insights, and deliverables, not investigations
(`frontend/src/app/app.routes.ts:82-125`).

**OBSERVED:** The premise that the human Analysis AI receives only Validated
Knowledge is not accurate. `AnalysisWorkflowServiceImpl.start` builds an
`AnalysisContext`, invokes `KnowledgeSelectionService.select`, persists the
result on the `AiTask`, and sends the projected `SelectedKnowledge` to the AI
engine (`AnalysisWorkflowServiceImpl.java:56-86`). That selected input includes
Facts, Observations, ACTIVE Insights, Engineering Events, human context,
evolution context, and a bounded `RepositoryContext` containing Git, changed
files, project knowledge, and source structure
(`KnowledgeSelectionServiceImpl.java:59-111`).

**OBSERVED:** The human-facing product does not expose that evidence as an
explorable investigation workspace. The Analysis page shows the pre-selection
`AnalysisContext` as raw JSON, selection and prompt digests, proposal synthesis,
and bounded proposal-support evidence. Although `AiTaskDetail` contains
`selectedKnowledgeSnapshot`, the Angular template does not display it
(`analysis.models.ts:84-111`; `analysis-detail-page.html:173-205,259-270`).
The frontend has no client for the engineering-context or project-history
search endpoints.

**OBSERVED:** The Deliverable workflow really is Validated-Insight-only. It
queries Insights and sends only id, analysis id, type, severity, title, and
content to the AI engine (`DeliverableServiceImpl.java:35-57,87-90`). If a
human used a Deliverable as the investigation result, the high-level,
validated-knowledge-centered output is an expected projection boundary, not a
prompt failure.

**INFERRED:** The reported symptom can therefore arise through either human
surface:

1. Analysis: rich evidence reaches AI but is not presented as a human evidence
   capability.
2. Deliverable: rich repository evidence is removed before AI generation and
   only promoted Insight prose remains.

Both undermine supervision, but they have different technical causes.

## Reproduction Scenario

The Trading OS Paper Account question requires both trusted architectural
knowledge and implementation evidence. The high-level findings cited in the
problem statement are consistent with Insights or AI synthesis. They are not
enough to establish the actual fields and coupling of `TradingAccount`,
`BrokerAccount`, `AccountRiskConfiguration`, Risk Engine inputs,
`ExecutionIntent`, `ExecutionPipeline`, or `BrokerSubmissionStep`.

**OBSERVED:** DevLog already has evidence kinds capable of representing much
of that missing evidence: `SOURCE_FILE`, `TEST_FILE`, Java symbol declarations,
selected file content, `FACT`, `OBSERVATION`, `COMMIT`, `CHANGED_FILE`,
`DECISION`, `ENGINEERING_STORY`, and `INSIGHT`. It does not yet retrieve ADR,
roadmap, or Story markdown bodies as first-class evidence; persisted Decision
and Story registry records are not equivalent to those documents (ADR-063
section 11; `context-composition-trusted-knowledge.md:301-307`).

**INFERRED:** The benchmark failure is therefore a combination of human
projection loss and remaining retrieval/expansion debt. Human UI parity alone
cannot manufacture document bodies or source content that the shared backend
did not retrieve, but the human must at least be able to inspect everything an
agent received and search/expand beyond its initial subset.

## Current Human Workflow

### Entry and execution

```text
Human
  -> Angular New Analysis or Project Understanding
  -> POST /api/v1/analyses + POST /api/v1/analyses/{id}/workflow
     or POST /api/v1/projects/{projectId}/understanding-executions
  -> AnalysisWorkflowServiceImpl.start
  -> KnowledgeCollectionService.collect
  -> AnalysisContextServiceImpl.build
  -> KnowledgeSelectionServiceImpl.select
  -> RepositoryContextEngine.build
  -> SelectedKnowledgePromptProjectionService.toMap
  -> AiTask.selectedKnowledgeSnapshot + AI PromptRequest
  -> ValidatableProposal
  -> ProposalReviewService
  -> Human validation
  -> promoted Insight / Engineering Event
```

Evidence:

- UI execution: `project-analyses-section.ts:73-89` and
  `project-understanding-section.ts:51-67,88-99`.
- Analysis endpoints: `AnalysisController.java:23-77`.
- Project Understanding preparation pins a source/revision and imports history:
  `ProjectUnderstandingPreparationService.java:24-64`.
- Context construction: `AnalysisContextServiceImpl.java:45-106`.
- Selection and AI submission: `AnalysisWorkflowServiceImpl.java:56-86`.
- Proposal review resolves bounded Facts/Observations:
  `ProposalReviewService.java:41-113`.

### Context supplied to human-workflow AI

**OBSERVED:** The AI receives the projected `SelectedKnowledge`, not merely
Insights:

- up to 40 Facts and 25 Observations, with observation-to-fact closure;
- up to 10 ACTIVE Insights;
- up to 5 existing architecture knowledge items for the architecture intent;
- up to 10 validated Engineering Events;
- up to 5 human context inputs;
- one bounded `RepositoryContext`, itself up to 60 evidence items and 6,000
  estimated tokens;
- optional commit comparison/evolution context;
- diagnostics, selection metadata, and digest.

Evidence: `KnowledgeSelectionServiceImpl.java:32-33,59-111` and
`SelectedKnowledgePromptProjectionService.java:33-49,120-190`.

**OBSERVED:** `SelectedKnowledgePromptProjectionService` preserves repository
kind, layer, reference, summary, time, related references, selected content,
and Java symbols. It drops internal provenance, scores, ranking reasons,
diagnostics, and allocation details before the AI boundary
(`SelectedKnowledgePromptProjectionService.java:61-84`).

### Context accessible to the human product

**OBSERVED:** The Analysis page exposes:

- Analysis identity/status/intent/revisions;
- collection counts and warnings;
- profile and pipeline status;
- AI provider/model and context/selection/prompt digests;
- raw pre-selection `AnalysisContext` JSON;
- AI proposals and their bounded supporting Facts/Observations;
- proposal evidence-reference strings;
- resulting validated Insights and Deliverables.

**OBSERVED:** It does not provide:

- a category list over selected or available engineering evidence;
- search or temporal/category filtering;
- ranked `EngineeringEvidence` with selection reasons;
- rejected candidate visibility or expansion beyond the model budget;
- direct commit, Decision, Story, or Insight resources;
- source-file content/symbol exploration;
- project-history search;
- manual evidence attachment to an Analysis;
- a freshness declaration bound to the exact Analysis evidence set.

## Current MCP / Agent Workflow

```text
Agent
  -> MCP get_engineering_context(projectSlug, intent)
  -> EngineeringContextTool
  -> DevlogProjectContextClient
  -> GET /api/v1/projects/{slug}/engineering-context
  -> EngineeringContextController
  -> EngineeringContextFacadeImpl
  -> ProjectContextProviderImpl
  -> RepositoryContextAdapter
  -> RepositoryContextEngine
  -> EngineeringContextContractMapper
  -> EngineeringContext JSON
  -> optional MCP resources / search_project_history
```

Evidence:

- MCP tool: `mcp-server/.../tool/EngineeringContextTool.java:11-39`.
- Backend facade: `EngineeringContextFacadeImpl.java:21-41`.
- Story-specific adapter: `RepositoryContextAdapter.java:30-38,58-84`.
- Core composition: `RepositoryContextEngine.java:66-113`.
- Contract/resource projection:
  `EngineeringContextContractMapper.java:43-128,179-321`.
- Independent history search:
  `ProjectHistorySearchServiceImpl.java:28-32,54-90`.

**OBSERVED:** MCP is transport-thin. The backend performs retrieval and
composition. However, `RepositoryContextAdapter` hard-codes
`engineering-story-preparation`, an `ARCHITECTURE_REVIEW` synthetic Analysis,
the `engineering-story-v1` profile, and story-specific guidance
(`RepositoryContextAdapter.java:44-45,102-129,202-228`). The MCP-facing
application path is therefore not fully consumer-neutral even though the
`RepositoryContextEngine` has no MCP imports.

**OBSERVED:** A live self-query at this HEAD returned 253 candidates, 60
selected items, and a budget warning. The selection contained 36
`CHANGED_FILE`, 17 `COMMIT`, 3 `ENGINEERING_STORY`, and one each of
`CHALLENGE`, `DECISION`, `INSIGHT`, and `FACT`; no `OBSERVATION` survived. Six
items were `SELECTED_BY_CATEGORY_FLOOR`. This verifies richer mixed evidence,
continued Git dominance, boundedness, and non-guaranteed per-kind coverage.

## Architecture Comparison

| Concern | Human Analysis workflow | MCP / agent workflow | Finding |
|---|---|---|---|
| Entry point | Angular Analysis/Understanding; `/api/v1/analyses` | MCP tool `get_engineering_context` | Separate consumer entries |
| Application service | `AnalysisWorkflowServiceImpl` | `EngineeringContextFacadeImpl` | Separate orchestration |
| Retrieval service | `AnalysisContextServiceImpl`, `KnowledgeSelectionServiceImpl`, then `RepositoryContextEngine` | `ProjectContextProviderImpl`, `RepositoryContextAdapter`, then `RepositoryContextEngine` | Partially shared |
| Knowledge source | Facts, Observations, ACTIVE Insights, events, human inputs, domain projections | ACTIVE Insights plus project snapshot and bounded baseline Facts/Observations | Overlapping, not identical |
| Projection source | `AnalysisContext` and persisted `SelectedKnowledge` snapshot | `EngineeringContextContractMapper` | Different contracts |
| History source | Evolution context only; no human search UI | commit collector plus MCP `search_project_history` | Agent discovery advantage |
| Source-code source | Nested `RepositoryContext`; hidden in human UI | repository structure collector plus selected content/symbol enrichment | Same engine, unequal exposure |
| Ranking | Fact/Observation intent ranking plus repository multi-criteria ranking | repository multi-criteria ranking under forced story profile | Shared inner ranker, different outer policy |
| Budgeting | 40 Facts, 25 Observations, 10 Insights, 5 architecture items, 60 repository items | 60 repository items / 6,000 tokens | Consumer-specific as ADR-063 requires |
| Category floors | Inner repository aggregate knowledge floor | Same aggregate knowledge floor | Story 0095 shared behavior |
| Freshness | Separate project freshness UI; Analysis has target/resolved revisions | freshness attached to EngineeringContext plus resource | Partial, inconsistent projection |
| Provenance | Fact source, evidence refs, raw context; selected provenance not presented | source type, file, id, occurrence, metadata, related refs | MCP richer per evidence |
| Drill-down | Analysis/proposal/Insight links; bounded Fact/Observation projection | resources for Decision, Insight, Story, Event, Commit plus history search | MCP richer, still incomplete |
| Output | AI proposals/validated knowledge and raw diagnostics | ranked machine-oriented context JSON | Representation may legitimately differ |
| Authorization | No application auth found | No credentials/auth; project-ownership checks on resources | No transport scope difference; systemic gap |

## Evidence Category Matrix

Actual names below are `RepositoryEvidence.kind` values or named domain
projections. `ADR`, `ROADMAP`, `VALIDATED_INSIGHT`, and
`RELATED_SOURCE_CODE` are layers, not evidence kinds.

| Evidence category | Human access | MCP access | Source / retrieval path | Drill-down | Gap? |
|---|---|---|---|---|---|
| `FACT` | In AnalysisContext, selected AI snapshot, proposal review; no general browse | Bounded candidate, inline summary | Fact repository -> deterministic collector | No MCP resource; proposal review only | Yes |
| `OBSERVATION` | In AnalysisContext, selected AI snapshot, proposal review | Bounded candidate, if it survives selection | Observation repository -> deterministic collector | No MCP resource | Yes |
| `INSIGHT` | Full product pages and selected AI snapshot | Ranked evidence; ACTIVE Insight resource | Insight repository | Both, but different navigation | Partial |
| `DECISION` | Raw AnalysisContext and project surfaces | Ranked ADR-layer evidence and Decision resource | Decision repository | MCP direct resource; no equivalent evidence navigation | Yes |
| `MILESTONE` | Type-dependent AnalysisContext/project surfaces | Ranked ROADMAP-layer evidence | Milestone repository | No resource | Partial |
| `ENGINEERING_STORY` | Raw AnalysisContext registry data | Ranked ROADMAP-layer evidence and Story resource | EngineeringStory repository | Registry detail only; markdown body absent | Yes |
| `ARTIFACT` | Type-dependent AnalysisContext | Ranked PROJECT_DOCUMENTATION evidence | Artifact repository | No resource; path may be present | Yes |
| `ENGINEERING_EVENT` | AnalysisContext and product event/proposal surfaces | Ranked GIT_HISTORY evidence and Event resource | EngineeringEvent repository | MCP resource | Partial |
| `CHALLENGE` | Raw AnalysisContext/project state | Ranked ROADMAP evidence | Challenge repository | No resource | Partial |
| `ANALYSIS` | First-class human page and raw related analyses | Current/previous Analysis evidence | Analysis repository | No MCP Analysis resource | Human stronger |
| `COMMIT` | Evolution scope and backend history API; no search UI | Ranked evidence, commit resource, history search | ProjectCommit repository | MCP resource | Yes |
| `CHANGED_FILE` | Evolution context/evidence strings in scoped cases | Ranked COMMIT_DIFF evidence | ChangedFile repository | Related refs only | Yes |
| `SOURCE_FILE` / `TEST_FILE` | May be inside persisted selected snapshot, not displayed | Ranked RELATED_SOURCE_CODE evidence; optional content/symbols | live revision-pinned workspace scan | Inline only | Yes |
| `CONFIG_FILE` / `MODULE` and summaries | Same hidden selected-snapshot condition | Ranked RELATED_SOURCE_CODE evidence | repository structure collector | Inline only | Yes |
| ADR markdown | No first-class retrieval | No first-class retrieval; commit/path discovery only | Git repository document | None | Shared retrieval gap |
| Roadmap markdown | No first-class retrieval | No first-class retrieval; path discovery only | Git repository document | None | Shared retrieval gap |
| Story artifacts | Registry row only; docs manually outside product | Registry resource plus commit/path discovery | registry + Git documents | No artifact-body expansion | Shared retrieval gap |
| `KnowledgeRelation` | Present in raw AnalysisContext | Loaded but not collected; no MCP expansion | KnowledgeRelation repository | None | Shared retrieval gap |
| `ProjectHumanContextInput` | Raw AnalysisContext and AI selection | Project context notes, not ranked evidence | active human-input repository | Project context only | Partial |
| Validatable Proposal | Human review only, correctly untrusted | Excluded from engineering evidence | Proposal repository | Human review | No; governance intentional |

The requested generic labels map as follows:

- `VALIDATED_KNOWLEDGE` is not an evidence enum. ACTIVE `INSIGHT`, accepted
  Decision/Event domain rows, and other promoted objects carry that role.
- `VALIDATED_INSIGHT` is a `RepositoryContextLayer`; its kind is `INSIGHT`.
- `OBSERVATION`, `DECISION`, `COMMIT`, `CHANGED_FILE`, `CHALLENGE`, and `FACT`
  are actual kinds.
- `ADR`, `ROADMAP`, and `RELATED_SOURCE_CODE` are actual layers.
- `STORY` corresponds to kind `ENGINEERING_STORY`.
- `ENGINEERING_ARTIFACT` corresponds to kind `ARTIFACT`.

## Validated Knowledge Path

**OBSERVED:** Validated knowledge is queried in three materially different
ways:

1. Analysis selection queries ACTIVE Insights and includes them alongside
   deterministic and repository evidence
   (`KnowledgeSelectionServiceImpl.java:68-82`).
2. MCP engineering context queries ACTIVE Insights and passes them into the
   same `RepositoryContextEngine`
   (`RepositoryContextAdapter.java:76-83`).
3. Deliverable generation queries Insights and sends only the reduced Insight
   snapshot (`DeliverableServiceImpl.java:45-57,87-90`).

**ROOT BOTTLENECK:** The general human Analysis is not retrieval-limited to
Validated Knowledge. Its bottleneck is that the rich selected context is an AI
input/persisted task snapshot, not a human evidence read model. The
validated-knowledge-only bottleneck exists at `DeliverableServiceImpl`, before
prompt construction. Prompt engineering cannot repair either boundary.

## Engineering Context Composition

`RepositoryContextEngine.build` performs:

1. context planning;
2. candidate collection;
3. multi-criteria ranking;
4. diversity and budget selection;
5. Java symbol enrichment;
6. file content enrichment;
7. deterministic ordering, diagnostics, warnings, and digest.

Evidence: `RepositoryContextEngine.java:66-113`.

**OBSERVED:** Defaults are 60 evidence items, 500 summary characters, 20
history items, and 6,000 tokens (`RepositoryContextEngine.java:47-63`).

**OBSERVED:** Story 0095 added an aggregate knowledge-kind floor between
diversity and normal ranking. At the default budget it reserves up to six
eligible items, while retaining relevance, concentration, item, and token
gates (`BudgetedDiverseEvidenceSelector.java:21-24,33-37,67-97`). It is not one
floor per kind; any individual category can remain absent.

**OBSERVED:** The core has no MCP imports and is reused by Analysis knowledge
selection, Engineering Story context, and MCP engineering context. It is
transport-neutral but not a consumer-neutral retrieval layer: it accepts
`AnalysisContext`, `IntentDefinition`, `UserGuidance`, and `Insight` entities;
collectors poll repositories/workspaces directly; and it owns one selected,
budgeted `RepositoryContext` output.

## Story 0095 / ADR-063 Context

**OBSERVED:** ADR-063 is Accepted and already decides:

- capability parity, not payload parity;
- shared retrieval/reference/trust/temporal/expansion capabilities;
- consumer-owned composition, projection, budget, and grounding;
- `RepositoryContextEngine` as one composition consumer, not the universal
  retrieval platform;
- progressive expansion;
- repository documents as retrievable HUMAN_AUTHORED evidence;
- no premature ContextPack or vector infrastructure.

Evidence: `docs/decisions/ADR-063.md:80-115,117-206,216-296`.

**OBSERVED:** Story 0095 implemented only the first bounded composition slice:
top 8 Facts and 6 Observations from recent windows of 200, plus the aggregate
knowledge floor (`RepositoryContextAdapter.java:47-50,132-200` and
`BudgetedDiverseEvidenceSelector.java:67-97`). It did not implement canonical
KnowledgeReference metadata, shared history recall, document bodies,
progressive expansion for every kind, freshness alignment, or human consumer
adoption (`story.md:87-92`; `engineering-report.md:122-140`).

**INFERRED:** ADR-063 was not accidentally exposed only through MCP; its first
implementation Story deliberately targeted the RepositoryContext composer that
MCP uses. Human Analysis AI also benefits indirectly because
`KnowledgeSelectionServiceImpl` calls that engine. Human evidence exploration
was outside Story scope, so product exposure remained asymmetric.

## Retrieval Sources

| Source | Source of truth | Retrieval/projection | Current consumers | Freshness model |
|---|---|---|---|---|
| Facts | persisted deterministic Analysis facts | bounded repository queries and Fact snapshots | Analysis AI/UI raw context, proposal review, context engine | analysis/revision lineage plus `detectedAt` |
| Observations | persisted rule-derived observations | bounded queries with supporting-Fact closure | same as Facts | analysis/revision lineage plus `createdAt` |
| Insights | human-promoted Core domain rows | ACTIVE queries | Analysis AI, MCP, human UI, Deliverables | lifecycle status; source revision may be older |
| Decisions | Core domain rows | project snapshot -> `DECISION` | Analysis context, MCP, project state | creation time; no ADR-document status link |
| Commits/files | imported Git history | commit/diff collectors and history search | MCP/context engine; scoped human evolution | current only to latest deterministic ingestion |
| Source code | Git workspace at resolved revision | structure scan, content and symbol enrichment | context engine | per-evidence resolved revision |
| Engineering Stories | Core registry plus separate Git docs | registry snapshot -> evidence/resource | MCP, project state, raw Analysis context | registry can lag Git; docs not loaded |
| ADR/roadmap/docs | Git repository files | paths in commits/Facts; no body retrieval | discovery only | commit provenance when discovered |
| Challenges | Core domain rows | project snapshot -> evidence | MCP and project state/raw Analysis context | lifecycle status and creation time |
| Relations | Core relation rows | loaded into snapshot, not context evidence | raw Analysis/project context | creation time only |
| Freshness | persisted source checkpoints | summary and EngineeringContext mapper | project UI, MCP | observed/ingested/baseline model |

## Projections

**OBSERVED:** DevLog currently has at least these distinct projections:

- `AnalysisContext`: broad, type-dependent, pre-selection domain snapshot.
- `SelectedKnowledge`: persisted and AI-facing bounded selection.
- `RepositoryContext`: ranked, budgeted technical/project evidence with rich
  internal provenance and selection diagnostics.
- `EngineeringContext`: public selected evidence plus project and freshness.
- `ProjectContext`: broad project/domain snapshot.
- `ProjectHistorySearchResult`: lexical commit/path recall.
- proposal review projection: bounded support evidence and validation lineage.
- Deliverable input: Insight-only narrative source.

**INFERRED:** The system does not need another universal ContextPack now.
ADR-063 correctly defers it. It needs a shared candidate retrieval capability
and consumer-specific read models over canonical references.

## Freshness

**OBSERVED:** The canonical project model distinguishes observed/current,
ingested, and Understanding baseline revisions and classifies `NO_BASELINE`,
`UNKNOWN`, `STALE`, `PARTIALLY_FRESH`, or `CURRENT`
(`ProjectFreshnessClassifier.java:5-45`). The human project page exposes
freshness separately from an Analysis. MCP attaches a freshness block to each
EngineeringContext and has a dedicated freshness resource.

**OBSERVED:** The EngineeringContext contract omits `ingestedRevision`, and
its mapper can override a persisted single-source state based on revisions
found only in selected evidence (`EngineeringContextFreshness.java:30-44`;
`EngineeringContextContractMapper.java:205-275`).

**VERDICT:** Freshness parity is **PARTIAL**. Both consumers have awareness,
but neither surface gives the complete three-checkpoint state consistently
bound to the exact evidence set. A human Analysis can show requested/resolved
revisions without showing whether each inspected item is current.

## Provenance

**OBSERVED:** Internal `RepositoryEvidence` carries source type, repository
location, originating file, identifier, full scoring, extraction metadata,
ranking reasons, related references, content, and symbols. MCP projection
preserves source type, file, identifier, final relevance, selection reason,
occurrence time, related references, extraction metadata, content/symbols, and
some resource URIs, but drops repository location and full score/decision
diagnostics (`EngineeringContextContractMapper.java:67-89`).

**OBSERVED:** Human proposal review can explain a proposal through Fact and
Observation content/source plus evidence-reference strings. The Analysis page
also exposes snapshots and digests. It does not present the selected evidence's
ranking/provenance as a navigable evidence set.

**VERDICT:** Provenance parity is **PARTIAL** and agent-favoring for selected
engineering evidence. Neither projection exposes all internal provenance.

## Drill-Down

**OBSERVED:** MCP has exact resource mappings for `DECISION`, `INSIGHT`,
`ENGINEERING_STORY`, `ENGINEERING_EVENT`, and `COMMIT`
(`EngineeringContextContractMapper.java:92-128`). It has no resource for Fact,
Observation, Artifact, Milestone, Challenge, Changed File, or source file.
History search provides additional commit/path discovery.

**OBSERVED:** Human navigation centers on Analysis -> Proposal -> supporting
Fact/Observation summary -> Validation/Insight. There is no generic evidence
inspect/related/return workflow, and no frontend use of MCP-equivalent backend
resources or history search.

**VERDICT:** Drill-down parity is **NO**. MCP is richer but incomplete. The
required backend direction is canonical reference expansion at application
level, projected separately to MCP resources and human routes; the UI must not
call MCP.

## Authorization

**OBSERVED:** No Spring Security dependency/configuration, `SecurityFilterChain`,
`@PreAuthorize`, bearer-token handling, Angular auth guard, or MCP backend
credential propagation was found. The MCP `RestClient` is configured with a
base URL only. MCP resources do perform project-ownership checks for global
identifiers (`ResourceSupport.java:39-118`), but that is object isolation, not
user authorization.

**VERDICT:** Authorization difference is **NO in repository code**. Human and
agent are not separated by different user scopes because no application user
scope is implemented. This is a systemic security limitation, not permission
to weaken future security. Any future retrieval capability must accept one
authorization scope before retrieval and apply it identically to every
projection.

**SECURITY TEST DIRECTION:** At the shared retrieval query boundary, contract
tests should prove that a principal authorized for project A cannot retrieve
project B references through list, filter, expansion, direct id, history, MCP,
or human routes. Existing `ResourceSupport.getWithProjectOwnership` behavior is
a useful object-isolation pattern, but not sufficient authorization.

## Context Budget

**OBSERVED:** Agent contexts must remain automatically bounded. The current
engine exposes candidate/selected counts, truncation, used tokens, warnings,
and selection reasons. Story 0095 preserves Git usefulness while reserving a
small aggregate knowledge floor.

**PROPOSED:** A human consumer should receive a bounded ranked initial view for
latency and comprehension, but the budget must be a presentation page, not the
boundary of the authorized evidence universe. Humans should be able to filter,
paginate, search, inspect excluded candidates, and expand canonical references.
This is stronger than payload parity and consistent with ADR-063 progressive
expansion.

## Investigation Evidence Model

**OBSERVED:** `Analysis` persists project, selected source/snapshot, type,
Intent, immutable user guidance, target revision, status, and timestamps
(`Analysis.java:25-81`). It has no explicit question, finding, conclusion, or
evidence-attachment collection.

**OBSERVED:** Auditability is distributed:

- `AiTask.contextSnapshot` copies pre-selection context;
- `AiTask.selectedKnowledgeSnapshot` copies the AI input;
- selection/context/prompt digests identify immutable snapshots;
- `ValidatableProposal` stores payload, supporting Fact/Observation ids, and
  evidence references (`ValidatableProposal.java:27-83`);
- Validation and promoted objects retain proposal lineage.

**OBSERVED:** No domain capability lets a human search evidence, select it,
attach/reference it to an Analysis, write a finding against it, and record a
conclusion. Evidence is copied into machine-execution snapshots or referenced
from AI proposals, not manually curated for a human investigation.

**INFERRED:** This is a domain/read-model gap if DevLog intends investigations
to be auditable human artifacts. It does not require changing ADR-006 proposal
governance: retrieved evidence remains evidence; AI synthesis remains a
`ValidatableProposal` until human validation.

## Human vs Agent Capability Gap

**OBSERVED:** For the same project and question, an agent has product-supported
access to:

- ranked mixed-category EngineeringContext;
- selection reasons and per-item provenance;
- context freshness and warnings;
- project history search;
- direct resources for five evidence kinds;
- iterative expansion within the MCP interaction.

**OBSERVED:** A human has product-supported access to:

- Analysis execution, raw pre-selection context, and diagnostics;
- AI synthesis as proposals;
- bounded support Fact/Observation evidence during review;
- validated knowledge and project-level freshness;
- an undisplayed selected snapshot in the API response.

**INFERRED:** An HTTP-capable human could manually call the same unauthenticated
engineering-context and history endpoints. That does not satisfy the product
invariant: an undocumented/manual transport call is not an accessible human
engineering workflow, and it still cannot explore rejected candidates or all
missing document/source expansion targets.

## Root Cause

**ROOT_CAUSE_ESTABLISHED:** The human and MCP paths diverge above and around
`RepositoryContextEngine`:

1. `AnalysisWorkflowServiceImpl` builds rich `SelectedKnowledge` through
   `KnowledgeSelectionServiceImpl` and persists/sends it as AI input, but the
   Angular Analysis page displays only `AnalysisContext`, digests, and proposal
   support. It does not project the selected `RepositoryContext` as human
   evidence and has no search/filter/expansion client.
2. `EngineeringContextFacadeImpl` explicitly invokes
   `RepositoryContextAdapter` and `EngineeringContextContractMapper`, and MCP
   adds `search_project_history` plus resources. This gives the agent a
   supported ranked evidence/discovery/navigation path.
3. `DeliverableServiceImpl`, when used for human-facing synthesis, removes all
   non-Insight evidence before AI invocation.
4. The common `RepositoryContextEngine` is a bounded composer over private
   collectors, not the consumer-neutral candidate retrieval/expansion boundary
   decided by ADR-063. Consequently neither consumer can yet enumerate the
   complete authorized evidence universe, and adding a separate human
   retrieval service would deepen the existing fragmentation.

The immediate asymmetry is therefore **product projection and capability
exposure**, not a human authorization restriction and not merely a prompt.
The deeper cause is incomplete implementation of ADR-063's shared retrieval
and canonical expansion primitives.

## Human Context Supremacy

Proposed invariant:

> A human engineer must never have less accessible engineering evidence than
> an AI agent operating on the same project and authorization scope.

**Classification: SUPPORTED_WITH_REFACTOR.**

Rationale:

- ADR-063 already adopts capability parity, consumer-specific projections,
  shared retrieval primitives, and progressive expansion.
- The inner context engine is reusable and transport-neutral.
- Existing backend REST capabilities already expose EngineeringContext,
  history search, domain detail, and freshness without requiring an MCP call.
- Human supremacy requires completing/refactoring the retrieval/reference
  boundary and adding a human projection; it does not conflict with ADR-006 or
  require a separate service/microservice.
- Current architecture does not support the invariant as-is because the UI
  lacks access and the retrieval universe itself is incomplete.

## Parity vs Supremacy

**PROPOSED:** Byte-identical payload parity is undesirable. Capability parity
is the minimum; human supremacy should additionally guarantee:

- search and category/temporal filtering across authorized candidates;
- provenance and source inspection;
- expansion beyond an automatic context budget;
- visibility into evidence excluded by ranking, with exclusion reason where
  available;
- manual evidence selection/attachment to the investigation;
- revision comparison;
- explicit freshness before architectural validation.

This does not give humans a broader authorization scope. It gives them a less
constrained presentation of the same authorized evidence universe.

## Shared Retrieval Core

**Verdict: PARTIAL.**

Existing reusable components:

- `RepositoryContextService` / `RepositoryContextEngine`;
- collector, ranker, selector, content, and symbol enrichment SPIs;
- `ProjectContextProvider` and its domain projections;
- `ProjectHistorySearchService` lexical recall;
- freshness summary/classifier;
- domain detail services currently wrapped by MCP resources;
- `RepositoryEvidence` and its reference/provenance metadata.

Missing consumer-neutral capabilities already required by ADR-063:

- one authorized candidate query boundary by project/source/revision/intent;
- canonical reference/trust/citability/temporal metadata across categories;
- reusable history recall inside retrieval rather than as an orphan tool;
- category filtering/paging and progressive expansion;
- first-class repository document retrieval;
- consistent freshness/revision semantics;
- authorization applied before retrieval;
- diagnostics for sources queried and candidates excluded.

## Consumer-Neutral Domain and ContextPack

**OBSERVED:** `RepositoryEvidence` is MCP-neutral, but its containing request
depends on backend Analysis and Insight models. The public `EngineeringEvidence`
contains a `devlog://` resource URI generated in the backend contract mapper,
which couples that projection to MCP semantics. The core itself does not know
about MCP; the public projection does.

**OBSERVED:** Existing context concepts have distinct meanings:

- `AnalysisContext`: broad deterministic/domain input for an Analysis.
- `SelectedKnowledge`: governed AI input snapshot.
- `RepositoryContext`: one ranked/budgeted composition.
- `EngineeringContext`: external MCP-oriented projection.
- `ProjectContext`: broad project snapshot.

**PROPOSED:** Do not introduce a new ContextPack now. Expose/reuse canonical
retrieval candidates and references, then let MCP retain `EngineeringContext`
while the human workflow gets an evidence read model. Revisit a common pack
only at ADR-063's trigger of multiple consumers sharing a composed structure.

## Architectural Options

### Option A - Expose the existing EngineeringContext facade to the human UI

Use the existing backend endpoint directly, not MCP, and present its 60-item
selection.

Benefits: smallest change, immediate category/provenance/freshness improvement,
exact initial selection parity with agents, low implementation cost.

Costs: preserves story-profile coupling, MCP `devlog://` semantics, incomplete
resource coverage, fixed model budget, no access to rejected candidates, and
private collector retrieval. It closes the most visible P0 symptom but not the
strong invariant.

### Option B - Add a human projection over RepositoryContextEngine

Invoke the engine through a human application service with human-specific
composition/paging and project the result for exploration.

Benefits: reuses rankings/collectors and avoids MCP calls; can tailor human
presentation.

Costs: the engine emits a selected bounded set rather than an authorized
candidate universe. Adding paging/filter semantics around it risks a second
ranking/freshness/reference implementation and incorrectly promotes one
composer to universal retrieval, which ADR-063 rejects.

### Option C - Complete ADR-063 shared retrieval primitives, keep consumer composition

Extract/refactor an application-level authorized engineering-evidence query
capability from current collectors, project-context queries, history search,
freshness, and canonical expansion. `RepositoryContextEngine` consumes its
candidates for MCP/AI composition; a human evidence read model consumes the
same candidates with paging/filtering/expansion and optional attachment.

Benefits: one evidence universe, authorization and freshness boundary;
consumer-specific budgets; strongest provenance and testability; avoids MCP
coupling; supports human exploration beyond model budget; creates the correct
future hybrid-retrieval seam.

Costs: higher than Option A; requires incremental reference and collector
convergence; repository-document status semantics need care.

## Option Evaluation

| Criterion | A: expose facade | B: human engine projection | C: shared retrieval primitives |
|---|---|---|---|
| Human context coverage | Initial agent subset | Better presentation, still selected subset | Full authorized candidate capability |
| Agent coverage | Unchanged | Unchanged | Improves incrementally |
| Single retrieval truth | Partial | Partial/risky | Yes, target state |
| Domain coupling | Existing | Existing plus human policy | Explicit shared candidate boundary |
| MCP coupling | High in contract | Medium | Low; MCP is projection |
| Authorization | Can reuse endpoint later | Must duplicate/inject | One pre-retrieval scope |
| Freshness | Existing incomplete mapper | Risk of second mapper | Shared metadata, separate display |
| Provenance | Existing selected fields | Internal fields available | Canonical across consumers |
| Drill-down | Existing five resources | Must add separately | Shared expansion capability |
| Testability | Initial parity test easy | Policies entangled | Capability and policy independently testable |
| Cost | Low | Medium | Medium-high, incremental |
| Future RAG fit | Poor | Partial | Strong; candidate producer seam |
| Divergence risk | Medium | High | Lowest |

## Recommended Direction

**PROPOSED:** Adopt Option C, delivered incrementally, with Option A's direct
backend reuse as an optional temporary first slice only if it does not become
the permanent architecture.

Target:

```text
Authorized project/source/revision/query
                  |
       Shared engineering-evidence retrieval
       canonical references + trust + freshness
                  |
       +----------+-----------+
       |                      |
 RepositoryContext       Human evidence
 composition             read model
       |                      |
 EngineeringContext      browse/filter/expand/
 MCP projection          select/attach
```

The backend application/projection layer should own this capability. No new
microservice is justified. Domain repositories remain authoritative; Git at a
pinned revision remains technical source evidence; indexes remain rebuildable
projections.

## Minimum P0 Outcome

The smallest outcome that closes the unacceptable agent-over-human category
gap is:

1. Make the existing selected EngineeringContext categories and provenance
   directly accessible from the human Analysis workflow through a backend
   human projection, not through MCP.
2. Provide inspect/expand capability for every resource type already available
   to MCP, plus project-history search.
3. Bind the view to project/source/revision freshness and expose truncation.
4. Establish a parity contract test that the human capability can retrieve
   every category returned to MCP for the same scope/query.
5. Explicitly keep the first slice on the path to shared candidate retrieval,
   rather than copying mapper/selector logic.

Manual exploration beyond the 60-item automatic subset and evidence attachment
are the next P0-completion slices for full Human Context Supremacy.

## Performance

**OBSERVED:** Story 0095 added two bounded queries of at most 200 rows and kept
the final response at 60 items. Repository scanning/content/symbol enrichment
can perform workspace work; history search currently scans all imported
project commits in memory before limiting
(`ProjectHistorySearchServiceImpl.java:72-90`).

**PROPOSED:** Human exploration must paginate/filter at retrieval boundaries,
not return the whole estate. Candidate counts, source-specific caps, revision
pinning, batched expansion, and no N+1 detail loading should be acceptance
concerns. Humans do not need all evidence in the initial response.

## Testability

Future capability-parity contract:

```text
Given same principal, project, source, revision, and equivalent query
When MCP composition returns evidence category K with canonical reference R
Then the human retrieval capability can find or expand K/R
And both projections report compatible trust, provenance, and revision
Even if ranking order and initial budgets differ.
```

Additional tests should prove:

- human can search beyond the agent's selected budget;
- agent remains bounded;
- category filters do not bypass authorization;
- stale and partially fresh contexts are visible to both;
- direct reference expansion enforces project ownership;
- unsupported/missing expansion is explicit, not silently null;
- unvalidated proposals never enter authorized evidence candidates;
- changing presentation does not alter ADR-006 promotion semantics.

## Observability

**OBSERVED:** General backend request correlation exists, and
`RepositoryContext` carries in-memory diagnostics/selection decisions. The MCP
client does not propagate correlation. `EngineeringContextFacadeImpl` and the
engine emit no dedicated telemetry for consumer, queried sources, candidate
counts by category, exclusions, or revision. Most rejected decisions are not
in the public contract.

**PROPOSED:** Future retrieval should record/measure consumer, authorization
scope identifier, project/source/revision, channels queried, candidates by
category, selected/excluded counts and reasons, freshness, duration, and
expansion failures. Do not log evidence bodies or sensitive credentials.

## Migration

**INFERRED:** Closing the access gap is primarily additive/refactoring.
Existing Analyses, Facts, Observations, Insights, proposals, validations,
selected snapshots, and Deliverables remain valid. A future manual evidence
attachment model may require persistence, but no migration is required merely
to expose a shared read capability. Canonical references should be introduced
additively and mapped from existing `git:`, `diff:`, `fact:`, `observation:`,
Insight, Story, Decision, and resource identifiers.

## Future RAG Compatibility

**PROPOSED:** No immediate RAG/vector solution. Complete structured retrieval,
canonical identity, document expansion, freshness, and authorization first.
A future lexical/vector channel may produce additional candidates through the
same retrieval seam. It must preserve source/revision/trust metadata, and its
index must remain a rebuildable projection, never the source of truth.

## ADR Candidates

**ADR CANDIDATE: YES.**

Decision question:

> Should Human Context Supremacy strengthen ADR-063 capability parity by
> requiring that, within the same authorization scope, every canonical
> engineering-evidence category available to an agent is retrievable and
> inspectable by a human, with human exploration allowed beyond automatic model
> budgets?

Architectural significance:

- establishes a governance invariant across transports and future consumers;
- clarifies authorization-before-retrieval;
- clarifies that human exploration is not constrained by model context;
- requires consumer-neutral expansion and parity tests;
- does not alter ADR-006 proposal/promotion authority.

Affected components: ADR-063 retrieval/reference primitives,
`RepositoryContextEngine` inputs, EngineeringContext projection, Analysis
read capabilities, history/detail services, freshness, authorization, and
future evidence attachment.

This could amend/extend ADR-063 rather than create a competing retrieval ADR.

## Possible Story Decomposition

No Stories were created. Suggested later slices:

1. Shared authorized evidence-reference/retrieval foundation over current
   structured sources.
2. Human Analysis evidence read capability reusing that foundation and the
   existing EngineeringContext categories.
3. Canonical evidence expansion for current MCP resources plus Facts,
   Observations, changed files, and source files.
4. Repository document retrieval for ADR, roadmap, and Story artifacts with
   status/revision provenance.
5. Human filtering, search, revision comparison, and exploration beyond the
   automatic budget.
6. Investigation evidence selection/attachment and audit projection.
7. Freshness/provenance alignment across human and MCP projections.
8. Capability-supremacy, authorization, performance, and observability tests.

## Paper Account Benchmark

After the minimum fix, the same human Paper Account investigation should be
able to:

- search for and inspect `TradingAccount`, `BrokerAccount`,
  `AccountRiskConfiguration`, `ExecutionIntent`, `ExecutionPipeline`, and
  `BrokerSubmissionStep` source/symbol evidence;
- inspect deterministic Facts/Observations describing their relationships;
- discover commits and changed files introducing or modifying those concepts;
- inspect relevant Decision/ADR evidence and Story 0030 registry/artifacts;
- compare the evidence revision with current/ingested/Understanding baselines;
- select and attach the evidence supporting findings about shared Risk Engine,
  authorization, and the late REAL/PAPER execution divergence;
- distinguish source evidence from AI synthesis and validate any resulting
  proposal under ADR-006.

This does not require dumping all source into an LLM prompt. The initial model
selection remains bounded; the human can search and expand the same authorized
evidence universe.

## Risks

- Treating `RepositoryContextEngine` as universal retrieval would conflict
  with ADR-063 and make human needs another configuration profile.
- Copying MCP mapper/resource logic into Angular/backend human services would
  create two reference, freshness, and authorization implementations.
- Exposing all candidates without pagination would create history volume,
  workspace scan, and response-size problems.
- Repository documents need status/supersession semantics; HUMAN_AUTHORED does
  not mean currently authoritative.
- Persisted selected snapshots may contain evidence appropriate for audit but
  stale for a new decision; snapshot and current retrieval must be distinct.
- Existing absence of authentication makes any richer endpoint security
  sensitive.
- Aggregate Story 0095 floors must not be described as per-category guarantees.
- Manual attachment must reference canonical evidence, not copy mutable
  unversioned text without provenance.

## Open Questions

- What existing or planned principal/project authorization model will scope
  the shared retrieval query?
- Should the first human slice expose the exact agent selection, current
  Analysis `SelectedKnowledge`, or both as separately labeled snapshots?
- Which application capability should resolve repository files safely at a
  pinned revision without coupling read requests to hidden synchronization?
- What lifecycle/status model is required before ADR and Story markdown can be
  used as current architectural evidence?
- Should manually attached evidence be immutable snapshot content, canonical
  references, or references plus a digest/revision snapshot?
- How should multi-source projects represent commit/file resource identity,
  given the current commit resource URI omits source id?
- Which exclusion diagnostics are safe and useful for humans without exposing
  internal ranking implementation excessively?

## Recommendation

**PROPOSED:** Accept Human Context Supremacy as a P0 governance requirement and
extend ADR-063 at architecture review. Implement no separate
`HumanRetrievalService` with private ranking or evidence semantics. Complete
the shared authorized candidate/reference/expansion primitives incrementally,
retain consumer-owned composition, project them to MCP and a human evidence
read model, and let humans explore beyond the automatic agent budget.

The repository evidence supports **READY_FOR_ARCHITECTURE_DECISION**. The next
step is **HUMAN_ARCHITECTURE_REVIEW**, not implementation.
