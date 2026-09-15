# Story 0130 - Repository Analysis

## Repository Reality

`main` and `origin/main` both point to `9cc3f9488a13db1002cdda7044292cc93d6cbc46`.
The worktree was clean at investigation start. The latest completed work is
Story 0129. ADR-068 is the latest accepted ADR. There is no Story 0130
production implementation.

## Entry Points

### DevLog analysis and human path

```text
Angular project analysis / project understanding
  -> POST /api/v1/analyses
  -> POST /api/v1/analyses/{id}/workflow
  -> AnalysisWorkflowServiceImpl.start
  -> collect Facts/Observations
  -> build AnalysisContext and ProjectProfile
  -> select SelectedKnowledge
  -> persist AiTask snapshots
  -> POST AI Engine /api/v1/ai/tasks
  -> provider generation and Python validation
  -> callback /api/v1/ai/tasks/{correlationId}/result
  -> Java validation and proposal persistence
  -> Angular result/proposal review pages
```

Relevant implementation: `AnalysisController`,
`AnalysisWorkflowServiceImpl`, `AiTaskResultServiceImpl`, and the Angular
analysis/result/proposal pages. Human analysis is a multi-step asynchronous
workflow. Its useful output is currently proposal/insight-oriented.

### AI/MCP path

```text
MCP get_engineering_context(project, intent, files, storyId)
  -> EngineeringContextTool
  -> DevlogProjectContextClient
  -> GET /api/v1/projects/{slug}/engineering-context
  -> EngineeringContextFacade
  -> RepositoryContextAdapter
  -> RepositoryContextEngine: plan, collect, rank, select, enrich
  -> EngineeringContext contract
```

The MCP server also exposes `search_project_history`, domain resources, and
`analyze_story_context`. The latter submits a story analysis and blocks while
polling for the canonical persisted result. It is not the same pipeline as
human generic Analysis: it has its own Story Context use case and Python
prompt builder, although both rely on Core-owned context and validation.

### Device path

```text
completed Analysis
  -> CommunicationDecisionService
  -> AnalysisCommunicationUseCase
  -> AgentCommunicationPort
  -> AgentPresenceCommunicationAdapter
  -> AgentPresencePort
  -> HttpAgentPresenceAdapter
  -> POST {DEVICE_BASE_URL}/message
```

`DEVICE_BASE_URL` defaults to `http://127.0.0.1:65535`. The adapter catches
transport failures and logs that the notification was dropped. No device
service exists in `docker-compose.yml`.

## Pipeline Ownership and Loss

| Stage | Current reality | Product consequence |
|---|---|---|
| Collection | Git, structure, commits/diffs, Facts, Observations and project knowledge are collected | Rich source data exists |
| Persistence | Facts, Observations, Analysis, AiTask snapshots, proposals, traces and trusted artifacts persist | Execution is auditable, but auditability is not usefulness |
| Enrichment | Ranking, selection, symbols, bounded content, relationships and freshness are available in Core | Enrichment is bounded and sometimes stale |
| Projection | `AnalysisContext`, `SelectedKnowledge`, `RepositoryContext`, `EngineeringContext`, result and MCP resources differ | No single consumer-neutral useful view |
| Selection | Human AI selection and MCP story-profile selection are different | Human and agent receive different evidence |
| Prompt | Intent-specific builders serialize selected data and grounding contracts | Correctness rules are strong; causal task framing is weak |
| Result | Human UI mainly shows proposals/results; MCP returns evidence/context | Human cannot explore what the model saw; agent does not receive a durable synthesis of why |
| History | Imported commits and changed paths are searchable; chronology is not synthesized into causal evolution | History is discoverable, not explained |
| Documents | ADR/Story paths and registry summaries are available; document bodies are not first-class retrieved context | The strongest why/constraint material is often absent |

## Live Exercises

The running stack contained these projects: `portfolio`, `trading-os`,
`devlog-ai`, and test projects. Trading OS was therefore available and was not
fabricated.

An engineering-context request for PAPER account identity and execution
returned 161 candidates and 60 selected items, with 12 commits, 12 changed
files, 8 source files, 6 tests, 2 Facts and 2 Observations. It reported
`STALE` freshness and several truncation/enrichment warnings. A more targeted
request returned 159 candidates, 60 selected items and predominantly changed
files, with no ADR or Story body and no causal synthesis.

The live DevLog project had completed generic `describe-project` analyses. One
recent result produced six accepted/proposed insights about tests, ADRs,
Docker, Maven, Spring Boot and project identity. Another produced seven
generic proposals. The Trading OS decision analysis produced three proposed
decisions: testing, ADR documentation and microservices/containerization. The
three proposals had empty evidence previews. These are correct descriptions of
repository presence but not useful explanations of the selected complex case.

## Human Utility Findings

| Representative output | Classification | Value | Why |
|---|---|---|---|
| DevLog `describe-project` result `e185d660...` | INFORMATIONAL, RESTATEMENT | LOW | Framework, Docker, Maven and test presence; obvious from files and README. Direct browsing replaces it cheaply. |
| DevLog prior `describe-project` result `636b0e98...` | INFORMATIONAL, RESTATEMENT | LOW | Repeats documentation/test/container/build facts; no causal history, impact or decision support. |
| Trading OS `analyze-engineering-decision` result `a6137ae1...` | RESTATEMENT, weak RECOMMENDATION | NONE/LOW | Proposes generic practices despite the multi-Story case; no useful account/execution explanation and no evidence previews. |
| Story Context capability | Intended HISTORICAL_SYNTHESIS and DECISION_SUPPORT | UNPROVEN | Endpoint and contract exist, but no completed live Story result was available through the project story registry during this run. |

The human UI can inspect status, diagnostics, raw context, proposals and
selected evidence endpoints, but it does not present a human investigation
workspace with searchable chronology, causal links, excluded candidates,
document expansion, or impact analysis. Correct output therefore remains low
utility.

## AI Utility Findings

The AI-facing interface exists and was exercised through the live REST
`engineering-context` endpoint, which is the backend target of MCP
`get_engineering_context`. The MCP server itself was inspected in source but
was not running as a Compose service, so no live MCP JSON-RPC call was claimed.

Available AI capability:

| Capability | Current assessment |
|---|---|
| Structured context | Bounded typed contract with trust tier, references, freshness, warnings and selection metadata |
| History | Lexical commit/path search and bounded commit/change evidence |
| Typed references | Present in recent v3 path; Story Context and several legacy paths remain raw/older contracts |
| Grounding | Strong Core-owned validation and fail-closed Story 0129 correction |
| Querying | Intent text, optional files, optional story id; separate lexical history search and resources |
| Causal reasoning | Not deterministic; not demonstrated in the live context result |
| Document understanding | ADR/Story paths/summaries, but no general document-body retrieval |
| Cost advantage | Unproven; the agent still needs multiple searches and direct files for details |

`get_engineering_context` is useful as a bounded evidence index and discovery
surface, especially for locating commits and changed files. It is not yet a
materially better answer to “why does this component exist?” than direct
repository inspection. The 60-item budget, stale source revision, Git-heavy
selection and missing document bodies reduce its advantage.

## Direct Repository Baseline

| Question | Direct repository result | DevLog result | Conclusion |
|---|---|---|---|
| Which changes led to PAPER execution behavior? | `git log`, path search and diffs identify Stories/commits and full files | Bounded commit/change summaries identify candidates, but omit a causal narrative | DevLog saves discovery steps only; no proven synthesis advantage |
| Which ADR governs behavior? | `rg`/file read finds ADR-042/043 and full rationale immediately | Context may return generic ADR practice or a path, not the governing body | Direct repo is better today |
| Which previous Stories changed this subsystem? | `git log -- path` plus `docs/stories` gives chronology and full artifacts | Story summaries may be selected, but registry/document bodies are incomplete | Direct repo is deeper and often cheaper |
| What constraints must survive a change? | Read ADR, Story acceptance criteria, tests and implementation directly | Typed trust metadata is reliable, but constraint synthesis is unproven | DevLog improves provenance, not understanding |
| What known debt affects the task? | Search docs/issues/paths and inspect current code | Challenges/insights can surface, but selection is bounded and not exhaustive | Potential advantage, not demonstrated for this case |

DevLog can know imported project-level knowledge, normalized evidence metadata,
selection diagnostics, freshness, and cross-analysis records. Today it does
not expose that knowledge in a sufficiently task-specific causal result to
beat the baseline reliably.

## Source Reachability Matrix

| Asset | Collected | Persisted | Enriched/projected | Available to analysis | Actually used in inspected prompt/result | Visible in result |
|---|---:|---:|---:|---:|---:|---:|
| Commit chronology | Yes | Yes | Bounded | Yes | Partly | Mostly summaries |
| Commit diffs | Yes | Yes | Bounded changed-file evidence | Yes | Partly | File summaries, no causal chain |
| ADR relationships | Paths/knowledge | Partial | Summary/decision projections | Sometimes | Not demonstrated | Generic ADR practice |
| Story relationships | Registry/paths | Partial | Summary and optional story scope | Sometimes | Not demonstrated in generic analyses | Registry-like evidence |
| Roadmap relationships | Partial | Partial | Bounded project knowledge | Sometimes | Not demonstrated | Low |
| Validated knowledge | Yes | Yes | Selected ACTIVE items | Yes | Yes | Generic insight prose |
| Historical evolution | Commit data | Analysis snapshots | Limited evolution context | Sometimes | Not demonstrated in generic outputs | No coherent timeline |
| Causal relationships | Explicit relation rows | Yes | Limited relation projection | Sometimes | Not demonstrated | No |
| Cross-artifact relationships | Partial | Partial | Related references | Sometimes | Weakly | IDs/links, not explanation |

The strongest assets are therefore mostly `AVAILABLE_TO_ANALYSIS`, but are not
reliably `ACTUALLY_USED_IN_PROMPT` for the question that matters and are rarely
`VISIBLE_IN_RESULT` as an actionable answer.

## Root Causes Ranked By Evidence

1. **Generic intent/output mismatch (high evidence).** Live outputs answer
   “what technologies/files are present?” instead of a bounded historical or
   change-impact question. The decision analysis generated generic practices.
2. **Historical/document context is not assembled into causal evidence (high
   evidence).** Context returns commits and changed files, but no chronology
   that links decision, implementation, review, dependency and consequence;
   ADR/Story bodies are not generally retrieved.
3. **Consumer projection and presentation gap (high evidence).** The human UI
   does not expose the selected evidence as an explorable investigation view;
   MCP exposes discovery but not a proven useful synthesis. They do not share a
   demonstrated task result.
4. **Bounded/stale selection (high evidence).** Live Trading OS responses were
   truncated to 60 items and reported stale/partial freshness. This can omit
   the exact ADR, Story or source needed for a meaningful answer.
5. **Observability gap (medium evidence).** Normal traces persist fingerprints,
   counts and outcomes, not the prompt/result bodies. This makes prompt-to-result
   usefulness difficult to audit without diagnostic mode.
6. **Provider nondeterminism (secondary).** It affects repeatability, but the
   inspected outputs are low-value even when technically valid. It is not the
   primary product bottleneck.

## Device Investigation

| Question | Result |
|---|---|
| Device integration exists | Yes, Java presence/communication ports and HTTP adapter |
| Device transport exists | Yes, HTTP POST `/message` |
| DevLog event-to-device path exists | Yes, completed Analysis can trigger communication |
| Eligible events | Current Analysis synthesis or highest-severity Insight with title/content |
| Trigger | Completion, deterministic SPEAK/SILENCE evaluation |
| Last mile | Not proven; default points to unused port 65535 and no device service is composed |
| Test coverage | Adapter/decision unit coverage exists; no end-to-end device delivery proof |
| Useful event quality | Not established; current generic analyses are not worth proactive interruption |

The device path is an architecture and local adapter, not a demonstrated
working end-to-end product path.

## Phase 2 Evaluation Refinement

Phase 1's human-reviewed conclusion is preserved unchanged: current human and
AI utility are LOW and parity is LOW/FAILING. Phase 2 adds a versioned,
machine-readable benchmark specification under `evaluation/`.

The four cases are frozen before any future implementation: historical
causality, architectural constraints, change impact, and a negative causal
control. The benchmark is pinned to Trading OS revision
`18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`; the live imported context was
observed as stale during Phase 1, so freshness is itself an evaluated field.

The oracle is a candidate only. Human validation must resolve the listed ADR,
Story, commit, source and test artifacts and approve the causal classifications.
No timing is fabricated: all human/direct timing fields remain
`PENDING_HUMAN_MEASUREMENT`.

The acceptance rule is conjunctive:

```text
STORY_ACCEPTED = ENGINEERING_CORRECTNESS_PASS
  AND AI_UTILITY_PASS
  AND HUMAN_UTILITY_PASS
```

Existing `ai-engine/evaluations` replay tests remain engineering/contract
evaluation. They do not replace this real-project product evaluation, and no
production code or executable product harness was added here.

## Human/AI Parity Matrix

| Capability | Human value | AI value | Parity | Evidence | Gap |
|---|---|---|---|---|---|
| Current-state analysis | LOW | LOW/MEDIUM | LOW | Generic live `describe-project` outputs; bounded EngineeringContext | Restatement dominates; no task-specific decision support |
| Historical reconstruction | LOW | LOW | LOW | Imported commits and changed files exist; no causal result | Chronology is not synthesized or fully exposed |
| ADR context | LOW | LOW | LOW | ADR practice summaries and paths appear | Governing ADR body/constraint is not reliably retrieved |
| Story context | LOW | LOW | LOW | Story registry/path summaries can be selected | Story bodies and dependency meaning are not reliably present |
| Commit/diff understanding | LOW | MEDIUM for discovery | LOW | Trading request returned commit and changed-file evidence | No before/after explanation or impact chain |
| Architecture constraints | LOW | LOW | LOW | Typed trust/grounding constraints are technically strong | Correctness metadata is not the same as domain constraints |
| Change-impact support | NONE demonstrated | LOW | LOW | File scoping exists | No proven impacted-component/test/dependency synthesis |
| Device communication | NONE | NOT_APPLICABLE | NONE | Adapter and trigger exist; no device delivery proof | No working last-mile service or useful event policy |
| Agent-facing context | NOT_APPLICABLE | LOW | LOW | MCP tools/resources and live REST context endpoint | Discovery interface is not yet a materially superior engineering answer |

Parity here means meaningful role-appropriate value, not identical payloads.
The current system fails that test because the human and agent surfaces differ,
and neither has proven the central historical synthesis use case.
