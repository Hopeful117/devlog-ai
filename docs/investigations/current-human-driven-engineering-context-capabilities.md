# Current Human-Driven Engineering Context Capabilities

Investigation-only artifact. No production code, domain knowledge, migration, ADR, Engineering Story,
or remote operation was created or modified.

## Status

**INVESTIGATION_COMPLETE**

## Question

Can current DevLog reliably answer an open-ended, detailed engineering-context question submitted by
a human, and was Documentation Generation a valid workflow for the first CV Analyzer benchmark?

## Repository State

- Branch: `main`.
- HEAD: `1761f3384d1a9cfd795a37f7d06bd90dcd37c013` (Story 0096 merge).
- Worktree before this investigation artifact: clean.
- DevLog self-context reported the same repository revision but a knowledge baseline at `10e0e45`,
  with `PARTIALLY_FRESH` / `REFRESH_RECOMMENDED`. Conclusions below therefore use current source and
  tests as authority, not absence from the bounded self-context selection.

## Executive Conclusion

Current DevLog has substantially more engineering evidence capability than Documentation Generation
can consume:

- Analysis can collect deterministic Facts and Observations, build a Project Profile, retrieve active
  Insights and human context, compose bounded repository/history/source evidence, persist the exact
  selected snapshot, and submit it to an Intent-specific AI prompt
  (`AnalysisWorkflowServiceImpl.java:50-101`; `KnowledgeSelectionServiceImpl.java:40-111`).
- Documentation Generation is the Deliverable workflow. It does no engineering-context collection or
  selection. It sends only reduced persisted Insight snapshots to a fixed document-synthesis prompt
  (`DeliverableServiceImpl.java:35-65`; `DeliverableGenerationRequest.java:8-15`).
- Analysis is richer but is not a general engineering Q&A interface. The human chooses a catalog
  Intent and supplies bounded structured guidance that explicitly refines rather than replaces that
  Intent (`analysis-form.html:3-5,16-56`; `IntentCatalog.java:33-85`). The AI must return grounded,
  structured proposals, not an arbitrary answer (`insight.py:24-48,114-150`).
- Story 0096 exposes the persisted evidence already selected for the newest displayed AI Task. Its
  endpoint accepts only an Analysis ID and has no retrieval or selection dependency. It answers
  "what context did this execution receive?", not "answer this new engineering question"
  (`AnalysisController.java:29-36`; `AiTaskSelectedEvidenceServiceImpl.java:26-71`).

**CV Analyzer benchmark validity: NO.** Documentation Generation is a valid benchmark of document
synthesis from existing Insight prose. It is not a valid or fair benchmark of DevLog's ability to
retrieve and reason over open-ended engineering context.

## A. Current DevLog Capability Map

### Capability matrix

| Category | Current collection or derivation | Persistence / projection | Can enter Analysis AI context? | Important current limit |
|---|---|---|---|---|
| Repository structure | Metadata collectors plus live module/source/test/config path scan | Metadata as Analysis Facts; selected live evidence in `RepositoryContext` and task snapshot | Yes | First active Source; live scan uses default/current revision, not necessarily Analysis target |
| Source/code evidence | Selected `SOURCE_FILE`/`TEST_FILE` content enrichment | `RepositoryEvidence.content`; selected task snapshot; MCP projection | Yes | Six files by default; revision can differ from Analysis Facts; config/docs excluded |
| Java symbols | JavaParser declaration extraction after file selection | `RepositoryEvidence.symbols`; selected task snapshot; MCP projection | Yes | Java only; declarations, not call graph/semantic resolution |
| Commits | Git history import | `project_commits` and parent rows | Yes | Metadata and messages, not raw patches |
| Changed files/diffs | Git name/status and line statistics; path aggregation; commit context | `commit_changed_files`; transient `CHANGED_FILE`; optional evolution context | Yes | No persisted or prompt-visible patch hunks |
| Engineering history | Recent commit evidence, changed-path aggregation, lexical history search | Persisted history plus transient repository context | Yes, bounded | Generic Analysis does not import history; rows may be stale and search is separate from UI |
| Facts | Deterministic collectors over repository/build/Spring/Docker/docs/tests/history | Analysis-scoped `facts` | Yes, max 40 selected | Facts are tied to one Analysis/revision; not a general human browser |
| Observations | Six versioned deterministic rules over supporting Facts | Analysis-scoped `observations` and join table | Yes, max 25 with Fact closure | Small fixed rule set; no broad semantic inference |
| Project Profile | Deterministic projection from Observations and diagnostics | Immutable `project_profile_snapshots` | Yes, mandatory | Represents supported observed characteristics only |
| Validated knowledge | Human-accepted proposals promoted to Insights | `insights` with proposal/validation provenance | Yes, ACTIVE only in Analysis | Deliverable query does not filter status |
| Architecture knowledge | Profile characteristics, active architecture Insights, Decisions, architecture Artifacts | Domain rows plus selected projections | Yes, Intent/type dependent | No unified architecture corpus; relation rows are not selected |
| ADRs | Markdown metadata Facts; structured Decision rows; changed ADR paths | Facts, Decisions, commit context | Partially | Generic ADR body is not loaded as first-class evidence |
| Engineering Stories | Explicit Story registry rows | `engineering_stories`; transient `ROADMAP/ENGINEERING_STORY` evidence | Yes, when selected | Story path/body is not read; registration is explicit |
| Roadmap/project docs | Markdown inventory Facts, Milestones, Stories, Challenges, Artifacts | Domain rows/Facts and transient evidence | Partially | Filesystem document bodies are generally absent |
| Human context | ACTIVE project human-context inputs; per-Analysis structured guidance | `project_human_context_inputs`; Analysis guidance JSON; task snapshots | Yes | Selected inputs capped at five; guidance is subordinate to Intent |
| Prior Insights | ACTIVE project Insights | `insights`; selected summaries | Yes, max 10 | Normal selected summary drops Insight/Analysis identity |
| Prior Analyses | Recent Analysis metadata | `analyses`; `PREVIOUS_ANALYSIS` evidence | Partially | Only type/status/time, not old AI output or old snapshots |
| Decisions/events | Structured domain rows and validated Engineering Events | Domain tables; selected repository/evolution projections | Yes, depending on context | Not every workflow consumes them |
| Knowledge relations | Loaded in broad Project Context | Relation rows and `ProjectContextSnapshot` | No direct selected projection | Exists but current selector/collector does not emit it |
| Legacy Documentation rows | CRUD title/type/content/version | `documentations` | No | Not used by current context composition or AI generation |

### Repository structure and source evidence

`RepositoryMetadataCollector` emits deterministic file/directory/source/config/build metadata Facts
(`RepositoryMetadataCollector.java:13-72`). Separately, `RepositoryStructureCollector` synchronizes
the first active Source and emits module summaries, source/test/config inventories, and up to 40
file-path candidates (`RepositoryStructureCollector.java:91-132,342-553`).

After ranking, only selected source/test files are eligible for content. Default policy allows six
files, 4,000 characters per file and 12,000 total characters
(`SelectedFileContentEnricher.java:23-27,52-100`; `RepositoryContentPolicy.java:6-15`). Java symbol
enrichment is likewise post-selection and bounded; it extracts declarations and locations, not
semantic relationships (`JavaDeclarationExtractor.java:24-149`; `RepositorySymbolPolicy.java:8-21`).

These live projections are not independently persisted. They become durable for an Analysis only
inside `AiTask.selectedKnowledgeSnapshot` (`AiTask.java:51-85`;
`V27__add_selected_knowledge_snapshot.sql:1-18`).

Current Analysis context is not guaranteed to be revision-coherent. Deterministic collection resolves
the Analysis target revision (`KnowledgeCollectionServiceImpl.java:81-95`), while
`RepositoryStructureCollector` independently chooses the first active Source and calls
`workspaceManager.synchronize(source, null)`, which resolves its default/current revision
(`RepositoryStructureCollector.java:91-112`). Generic `AnalysisWorkflowServiceImpl` also does not
import project history before selection (`AnalysisWorkflowServiceImpl.java:56-72`); commit evidence
depends on rows imported by an earlier history, Understanding, Event, or synchronization operation.
One selected snapshot can therefore combine target-revision Facts, current/default-revision source
evidence, and older imported history. Persisting the snapshot makes that mixture auditable, not
revision-consistent.

### Commits, diffs and evolution

`CommandLineGitHistoryProvider` collects commit identity, parents, author, timestamps, messages,
root/merge flags, changed paths, rename/copy status, binary markers and insertion/deletion statistics
(`CommandLineGitHistoryProvider.java:28-123`). They are stored in `project_commits`,
`commit_parents`, and `commit_changed_files` (`V29__create_project_history.sql:1-45`).

AI-visible history is derived in several bounded forms:

- recent commit summaries from `GitHistoryContextCollector` (`GitHistoryContextCollector.java:32-54`);
- 90-day changed-path aggregation, max 50, from `CommitDiffEvidenceCollector`
  (`CommitDiffEvidenceCollector.java:25-56,68-173`);
- commit-scoped Facts from `CommitScopedFactCollector` (`CommitScopedFactCollector.java:18-143`);
- detailed first-parent evolution context from `CommitDiffContextBuilder`
  (`CommitDiffContextBuilder.java:14-140`).

No current path persists or supplies raw patch hunks. A request requiring exact line-level historical
diff reasoning may therefore encounter both a `KNOWLEDGE_GAP` in persisted data and a
`RETRIEVAL_GAP` against Git, depending on workspace/revision availability.

### Facts, Observations and Project Profile

Collectors create normalized, revision-sensitive, fingerprinted Facts with evidence references
(`CollectedFact.java:13-59`). Current collector coverage includes repository metadata, Git HEAD,
build descriptors, Spring, Docker, Markdown inventory, tests and commit-scoped history
(`FactType.java:3-62`; `KnowledgeCollectionServiceImpl.java:60-177`).

`DeterministicObservationEngine` has six implemented rules covering containerization, Spring Boot REST,
ADR documentation presence, test/integration-test suites and multi-module builds
(`DeterministicObservationEngine.java:11-50`). Observations persist exact supporting Fact IDs
(`Observation.java:15-57`; `V12__create_facts_and_observations_tables.sql:21-41`).

`ProjectProfileServiceImpl` derives and persists an immutable profile from Observations and execution
diagnostics (`ProjectProfileServiceImpl.java:33-68,94-128`;
`ProjectProfileSnapshot.java:16-45`). Analysis selection treats that profile as mandatory.

### Trusted knowledge, architecture and prior analyses

Human acceptance promotes proposals to persisted Insights with Proposal/Validation provenance,
severity, status, content, rationale and evidence references (`InsightPromotionService.java:23-61`;
`Insight.java:21-89`). Analysis selection queries only ACTIVE Insights, keeps up to ten summaries and,
for `architecture-overview`, up to five richer architecture items
(`KnowledgeSelectionServiceImpl.java:68-80,267-304`).

Structured Decisions, architecture Artifacts, Milestones, Challenges, Engineering Stories, Engineering
Events and recent Analyses are loaded through `ProjectContextProviderImpl` with fixed limits
(`ProjectContextProviderImpl.java:42-180`). Their availability to a particular Analysis depends on
Analysis type, Intent profile, ranking and budget. Prior Analyses become only coarse
`type + status + createdAt` evidence; old task outputs are not replayed automatically
(`AnalysisContextServiceImpl.java:66-80`; `ProjectKnowledgeContextCollector.java:53-56`).

Knowledge Relations are loaded into broad Project Context but are not iterated by the current
repository evidence collector and have no `SelectedKnowledge` section. This is a concrete
`CONTEXT_ASSEMBLY_GAP`.

### ADRs, Stories and repository documentation

Current DevLog must not be described as ingesting complete repository documentation:

- `DocumentationCollector` stores path, size and first heading as Facts; it does not store Markdown
  bodies (`DocumentationCollector.java:21-71`).
- A persisted `Decision` can become structured `ADR/DECISION` evidence, but a file under
  `docs/decisions` is not automatically a Decision row
  (`ProjectKnowledgeContextCollector.java:30-43`).
- Engineering Stories persist number/title/status/path/commits, and evidence carries those fields;
  `storyPath` is not dereferenced (`EngineeringStory.java:13-61`;
  `ProjectKnowledgeContextCollector.java:68-92`).
- roadmap paths can be identified from Facts and commit context, but their bodies are not generally
  selected (`DeterministicKnowledgeContextCollector.java:54-63`;
  `CommitDiffContextBuilder.java:116-132`).
- the legacy `Documentation` CRUD aggregate persists supplied content but is not queried by
  `ProjectContextProviderImpl`, `KnowledgeSelectionServiceImpl`, or Deliverable generation
  (`DocumentationServiceImpl.java:29-56`; `documentation/README.md:90-107`).

These are implemented limitations. ADR-063's future repository-document retrieval direction is not
current capability.

## B. Human Workflow Comparison

| Workflow | Human input | Model context | Selection / prompt | Output purpose | Open-ended engineering query suitability |
|---|---|---|---|---|---|
| Generic Analysis | Type, catalog Intent, optional revision, bounded structured guidance | Rich selected knowledge: profile, Facts, Observations, active Insights, events, human context, repository/history/source evidence, optional evolution | Intent-specific bounded selection; fixed versioned prompt; guidance lowest priority | Grounded structured proposals for human validation | **PARTIAL/LOW**: rich evidence, but no arbitrary objective or answer contract |
| Project Understanding | UI: Source + revision only; API can carry Analysis guidance | Same as `describe-project-v1` Analysis | Fixed `ARCHITECTURE_REVIEW` + `describe-project-v1` | Project-description Insight proposals | **LOW** through UI; no question input |
| Documentation Generation / Deliverable | Document type, audience, style, language, max-1,000 guidance, optional Analysis scope | Reduced persisted Insight snapshots only | No evidence retrieval/selection; fixed document prompt | One coherent generated document | **NO** for investigation; suitable for synthesis from existing Insights |
| Engineering Decision Analysis | Same generic Analysis form | Generic selected Analysis context | Fixed `analyze-engineering-decision-v1` Intent and structured Decision proposal contract | Engineering Decision proposals | **LOW** for arbitrary Q&A; useful only for its fixed objective |
| Dedicated Engineering Event | Dedicated Source + target commit request | Evolution context plus selected grounding | Dedicated event preparation/Intent/prompt, one retry for invalid grounding | Engineering Event proposals | **NO** outside commit-event analysis |
| Story 0096 selected-evidence view | Analysis ID implicitly from route | Existing persisted task snapshot; no AI call | No retrieval or selection | Human audit of supplied input | **NO**: inspection only |
| Engineering Context REST/MCP | Project slug + free-text intent | On-demand bounded `RepositoryContext`/Engineering Context | Repository composer, max 60/default token budget | Agent context, not human AI answer | Useful evidence API, but revision coherence and full evidence-universe access are not guaranteed |
| History search REST/MCP | Project + lexical query + limit | Imported commit messages/paths | Deterministic AND search | Commit matches/resources | Narrowly useful; not cross-category analysis |
| Legacy Documentation CRUD | Supplied title/type/content | None | None | Store human-provided documentation | Not an AI workflow |

### Generic Analysis input boundary

The Angular form exposes only `ARCHITECTURE_REVIEW` and `PROJECT_EVOLUTION`, a catalog Intent, optional
revision, and structured guidance (`analysis-form.ts:22-45`; `analysis-form.html:1-58`). Bounds are:

- focus: 500;
- audience: 200;
- level of detail: 100;
- writing style: 100;
- output context: 500;
- at most ten priorities of 300 characters.

The backend trims but does not summarize/truncate valid values (`UserGuidance.java:10-26,54-56`).
However, the UI and prompt explicitly state that guidance refines rather than replaces the selected
Intent. The prompt makes Intent the exclusive objective and User Guidance the lowest-priority,
untrusted input (`analysis-form.html:40-56`; `insight.py:24-30,129-149`).

The current catalog has only these generic objectives:

- describe project;
- propose README information;
- architecture overview;
- analyze engineering decision.

`analyze-engineering-event` is dedicated, not generic (`IntentCatalog.java:33-65`). There is no
generic "answer this engineering question" Intent and no arbitrary answer output contract.

The Intent endpoint and Angular form do not filter execution mode, so the generic form can display
`analyze-engineering-event`; Core then rejects it because dedicated Intents cannot be created through
generic Analysis (`IntentController.java:12-21`; `AnalysisServiceImpl.java:53-58`). The real Event path
requires its dedicated endpoint/preparation with an active Source and complete target commit
(`EngineeringEventExecutionPreparationService.java:27-52`). This is an additional human-workflow/UI
gap, not a free-form Analysis capability.

### Generic Analysis context and output boundary

The workflow collects knowledge, builds deterministic analysis/profile/context, selects evidence,
persists the selection and submits it (`AnalysisWorkflowServiceImpl.java:50-101`). The actual model
payload is `SelectedKnowledge`, not the broader `AnalysisContext` snapshot
(`SelectedKnowledge.java:13-29`; `SelectedKnowledgePromptProjectionService.java:33-190`).

Default selection limits are 40 Facts, 25 Observations, 10 prior Insights, 5 architecture items and 60
repository-evidence items; events and human inputs are additionally capped at 10 and 5
(`KnowledgeSelectionServiceImpl.java:25-38,68-111`). Guidance terms from priorities, focus and output
context provide only a small lexical boost to Facts/Observations; audience, detail and writing style do
not affect that ranking (`KnowledgeSelectionServiceImpl.java:150-160`).

The AI must return zero to ten grounded proposals under an Intent-owned schema, not a direct report or
question answer (`IntentCatalog.java:88-125`; `insight.py:121-150`). Thus Analysis has the best current
evidence assembly but still has a `HUMAN_INPUT_GAP` and `PROMPT_OR_WORKFLOW_GAP` for the CV Analyzer
request.

### Project Understanding

The UI accepts only Source and optional revision (`project-understanding-section.html:16-59`). Its
TypeScript request model and backend DTO can carry `userGuidance`, but the current component does not
populate it (`project-understanding.models.ts:3-7`;
`project-understanding-section.ts:41-42,88-99`; `ProjectUnderstandingRequest.java:10-14`). Backend
preparation fixes the Intent to `describe-project-v1`, synchronizes the repository and imports history
before launching the normal Analysis path (`ProjectUnderstandingPreparationService.java:25-63`;
`ProjectUnderstandingService.java:28-49`). It is therefore not an alternate free-form interface.

### Documentation Generation / Deliverable

The human-facing "Project documentation" feature calls `POST /api/v1/deliverables`
(`project-deliverables-section.html:1-15`; `DeliverableController.java:15-40`). Inputs are one fixed
document type, audience, style, language, optional Analysis scope and `additionalGuidance` capped at
1,000 characters (`deliverable-form.ts:11-61`; `CreateDeliverableRequest.java:10-18`).

`DeliverableServiceImpl` loads all project or Analysis Insights, rejects an empty set, and maps every
Insight to exactly ID, Analysis ID, type, severity, title and content. It does not invoke context
collection, repository context, selection, history, source, profile, Fact, Observation, Decision,
Story, Event or human-context services (`DeliverableServiceImpl.java:35-65,87-90`). It also does not
filter Insight status (`InsightRepository.java:23-30`), so archived Insights may be included.

The AI prompt orders and summarizes those Insight texts into one document and forbids claims absent
from them (`deliverable.py:9-55`). This is an intentional synthesis workflow, not an investigation
workflow.

## C. Evidence / Context Pipelines

### Analysis pipeline

```text
Human
  -> Analysis type + catalog Intent + optional revision + structured guidance
  -> POST /api/v1/analyses
  -> POST /api/v1/analyses/{id}/workflow
  -> KnowledgeCollectionService
       repository/build/framework/docs/test/history collectors
       -> persisted Analysis Facts
       -> deterministic Observations with Fact closure
  -> DeterministicAnalysisService
  -> ProjectProfileService -> persisted immutable profile
  -> AnalysisContextService
       broad project/domain/history/evolution context
  -> KnowledgeSelectionService
       bounded Facts/Observations/active Insights/events/human inputs
       + RepositoryContextEngine
           collectors -> ranking -> diversity/budget -> content/symbol enrichment
  -> SelectedKnowledgePromptProjectionService
  -> AiTask.selectedKnowledgeSnapshot
  -> PromptRequest(Intent + guidance + selected knowledge + output schema)
  -> versioned AI Engine prompt
  -> grounded structured Proposal(s)
  -> human review/acceptance
  -> promoted Insight/Decision/Event
  -> Analysis detail + Story 0096 selected-evidence audit
```

Primary implementation: `AnalysisWorkflowServiceImpl.java:50-101`,
`KnowledgeCollectionServiceImpl.java:60-177`, `AnalysisContextServiceImpl.java:33-105`,
`KnowledgeSelectionServiceImpl.java:40-111`, `RepositoryContextEngine.java:39-113`, and
`insight.py:53-170`.

### Documentation Generation pipeline used by CV Analyzer

```text
Human
  -> document type + audience + style + language + additionalGuidance
  -> POST /api/v1/deliverables
  -> DeliverableServiceImpl
       project/Analysis ownership check
       -> query persisted Insights only
       -> reduce each to id, analysisId, type, severity, title, content
  -> DeliverableGenerationRequest
  -> DeliverablePromptBuilder
       fixed system instruction: synthesize only supplied Insights
       + canonical request JSON
       + canonical reduced Insight array
  -> one structured provider generation
  -> title + Markdown-compatible plain-text content
  -> persisted GeneratedDeliverable
  -> human Deliverable detail page
```

There is no collection, retrieval, repository-context composition, evidence selection, follow-up tool
call or source expansion anywhere in this path.

### Exact treatment of the detailed CV Analyzer instruction

`additionalGuidance` follows this implemented path:

1. Angular caps it at 1,000 characters and trims outer whitespace
   (`deliverable-form.ts:41-60`; `deliverable-form.html:17-24`).
2. Core validates the same maximum and trims outer whitespace; blank becomes null
   (`CreateDeliverableRequest.java:14-17`; `DeliverableServiceImpl.java:52-65,102-104`).
3. AI Engine Pydantic validation caps it at 1,000 and strips outer whitespace
   (`schemas/deliverable.py:13-14,26-35`).
4. `DeliverablePromptBuilder` embeds the complete remaining string as the
   `additionalGuidance` value in canonical JSON (`deliverable.py:18-33,57-58`).

It is not summarized, rewritten, split or deliberately truncated. JSON serialization escapes
newlines/quotes without changing the string value. But it is constrained in two more important ways:

- the prompt treats it as untrusted data, never as a system instruction;
- it can only shape a document whose technical claims already exist in supplied Insight content.

Therefore preservation of the instruction does not imply capability to execute the investigation.

### Story 0096 pipeline

```text
Analysis route ID
  -> GET /api/v1/analyses/{analysisId}/selected-evidence
  -> resolve owning Analysis/project
  -> select newest AiTask by createdAt DESC, id DESC
  -> read persisted selectedKnowledgeSnapshot
  -> strict V1-V4 historical projector
  -> typed eight-category response
  -> Angular historical evidence display
```

The service depends only on `AnalysisRepository`, `AiTaskRepository` and
`HistoricalSelectedEvidenceSnapshotProjector` (`AiTaskSelectedEvidenceServiceImpl.java:26-28`). A test
locks that dependency boundary (`AiTaskSelectedEvidenceServiceTest.java:167-180`). The endpoint has no
question, filter, category, source, revision, pagination or expansion input. It neither collects nor
reselects evidence.

Story 0096 therefore closes a historical `PRESENTATION_GAP` for Analysis tasks. It does not implement
the broader search/filter/page/expand capability described as future intent in ADR-063. The two must
not be treated as equivalent.

## D. Gap Classification

### HUMAN_INPUT_GAP

- Generic Analysis has no arbitrary question/instruction field and no replaceable objective. Bounded
  guidance is subordinate to one catalog Intent.
- Project Understanding's current UI sends no guidance at all.
- Deliverable has one free-form field but no engineering query scope, source/revision selection,
  evidence categories, expected analytical structure or follow-up interaction.

This gap exists even when the human wrote a detailed CV Analyzer request, because the selected workflow
cannot represent it as an authoritative investigation objective.

### PROMPT_OR_WORKFLOW_GAP

- Analysis prompts are fixed to proposal generation under four generic Intents.
- Deliverable asks for one coherent document and expressly forbids new engineering claims or
  investigation beyond supplied Insights.
- Neither workflow supports iterative human engineering Q&A, retrieval/tool use or evidence-driven
  follow-up.

For CV Analyzer this is primarily a workflow mismatch, not evidence that the LLM ignored a valid
general-analysis prompt.

### CONTEXT_ASSEMBLY_GAP

- Deliverable excludes Facts, Observations, profile, repository evidence, source content/symbols,
  commits/diffs, Decisions, Stories, Events, human context and evidence references before the AI call.
- Analysis loads broad Project Context fields such as Knowledge Relations and recent Knowledge Events
  that are not directly represented in `SelectedKnowledge`.
- Bounded Analysis composition can omit relevant candidates even when collectors found them.
- Analysis can assemble target-revision Facts, current/default-revision source evidence and older
  imported history into one selection; no current workflow-level invariant requires one revision.

This is the primary technical bias in the CV Analyzer benchmark.

### RETRIEVAL_GAP

- No current human UI searches or pages the complete authorized engineering-evidence universe.
- Repository document/ADR/Story bodies are not generally retrievable as first-class evidence.
- Project history search covers imported commit messages and paths only and is disconnected from the
  Analysis UI.
- Generic Analysis does not import history before selection, so persisted commit/path retrieval may
  lag the requested or current repository revision.
- Story 0096 cannot retrieve beyond one persisted selected task snapshot.
- Engineering Context accepts free-text intent but returns another bounded agent composition, not a
  complete pageable human evidence set.

### KNOWLEDGE_GAP

Confirmed examples:

- no persisted raw patch/hunk data;
- no generic persisted repository document bodies from the collection pipeline;
- no semantic source call graph or cross-language symbol model;
- no automatic replay of previous Analysis outputs unless promoted into durable domain knowledge.

An ADR/Story file that exists in Git but is not loaded is a `RETRIEVAL_GAP`, not a
`KNOWLEDGE_GAP`. A technical claim present nowhere in Git, Facts, domain knowledge or human context is
a true `KNOWLEDGE_GAP`.

### PRESENTATION_GAP

- Story 0096 substantially closes selected-evidence presentation for Analysis tasks.
- Deliverables show generated content and source Insight links but not the full prompt/request or any
  underlying source evidence, because that evidence was never supplied.
- There remains no human presentation for cross-category search, expansion, excluded candidates or
  revision comparison.

### UNKNOWN

Use only when the exact execution cannot be reconstructed, for example a legacy/terminal Analysis task
with no selected snapshot, malformed historical data, unavailable repository revision, or missing
record of the exact CV Analyzer Insight set. Story 0096 explicitly reports unavailable snapshots
rather than substituting current context.

### Gap summary for the CV Analyzer run

| Gap | Applies? | Reason |
|---|---:|---|
| `KNOWLEDGE_GAP` | Unknown from this benchmark | The workflow did not attempt broad evidence retrieval |
| `RETRIEVAL_GAP` | Yes, systemically | Several evidence/document expansion paths remain unavailable |
| `CONTEXT_ASSEMBLY_GAP` | **Yes, primary** | Deliverable sends reduced Insight snapshots only |
| `HUMAN_INPUT_GAP` | **Yes** | Guidance is not an authoritative arbitrary investigation objective |
| `PROMPT_OR_WORKFLOW_GAP` | **Yes, primary** | Fixed document synthesis was used for open-ended analysis |
| `PRESENTATION_GAP` | Partial | No underlying Deliverable evidence audit; Analysis audit now exists |
| `UNKNOWN` | Yes for exact causal attribution | Actual benchmark Insight set/output was not found in repository artifacts |

## E. CV Analyzer Benchmark Validity Assessment

### Verdict

**NO**

The Documentation Generation workflow was not a valid and fair way to benchmark DevLog's ability to
answer an open-ended human engineering-context query.

Concrete reasons:

1. The workflow objective is communication from existing Insights, not investigation. Its system
   prompt says to use only supplied human-validated Insights and never create new knowledge
   (`deliverable.py:11-16`).
2. Core sends only reduced Insight snapshots (`DeliverableServiceImpl.java:45-57,87-90`). It omits
   the richer evidence categories that Analysis and Engineering Context can assemble.
3. The detailed guidance is preserved within 1,000 characters, but it is subordinate data embedded
   in a documentation request, not a replacement system objective (`deliverable.py:18-33`).
4. There is no retrieval, selection, source inspection, history search, reference expansion or
   iterative tool loop in Deliverable generation.
5. A weak or incomplete output can therefore demonstrate that the required claims were absent from
   the selected Insight texts or unsuitable for the document prompt. It cannot establish that DevLog
   lacked the underlying Facts, repository evidence, history or source capability.

The benchmark remains useful only for this narrower question:

> Can DevLog turn the currently persisted project/Analysis Insight prose into the requested document,
> without inventing unsupported claims?

It must not yet be interpreted as a benchmark of open-ended engineering reconstruction.

## F. Recommended Next Investigation Step

Run a controlled capability-isolation experiment; do not implement a new workflow yet.

For one intended CV Analyzer revision and one unchanged engineering question:

1. Record the effective revision of every evidence path separately: Analysis requested/resolved
   revision, Fact/profile revision, repository-evidence revision, and latest imported-history commit.
2. Inventory the evidence actually present in DevLog storage and at the intended repository revision: Facts,
   Observations, profile, ACTIVE Insights, Decisions, Stories, Events, imported commits/changed paths,
   human context and source files.
3. Run the closest supported Analysis Intent with guidance mapped explicitly into focus, output
   context and priorities.
4. Capture its Story 0096 selected-evidence snapshot and classify each expected evidence item as
   collected, candidate-but-not-selected, selected, or unavailable.
5. Separately capture the exact reduced Insight array used by the existing Deliverable benchmark.
6. Compare both actual model inputs against a manually prepared, revision-pinned repository-evidence
   ground truth; do not call the run fixed-revision if the effective revisions differ.

This next investigation would isolate:

- true missing knowledge;
- retrieval failure;
- bounded selection/context assembly loss;
- fixed Intent/guidance limitations;
- model/prompt use of evidence;
- presentation-only loss.

It should stop at measured capability attribution. It should not yet choose an implementation
architecture, amend an ADR, modify project knowledge, or create an Engineering Story.

## Evidence Quality Notes

- Current source and focused tests are treated as implementation authority.
- ADR-063 and Story 0096 documents are used only to explain explicit scope boundaries, not to claim
  planned search/expansion capabilities are implemented.
- Some older investigation text predates Story 0095/0096 and is stale where it claims
  `RepositoryContextAdapter` always supplies empty Facts/Observations. Current adapter code performs
  bounded profile-Analysis Fact/Observation retrieval; that older statement is not used here as
  current capability evidence.
- The exact CV Analyzer request, selected Insight array, provider prompt and generated output were not
  found in this repository. Conclusions assess workflow capability, not the quality of that output.
