# Canonical Analysis Information Pipeline & Invocation Parity Investigation

## Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `REPORTING_ONLY`
- Date: `2026-08-31`

## 1. Investigation Metadata

- Investigation type: `ARCHITECTURE_INVESTIGATION`
- Story: `0106-intent-aware-structured-context-utilization-for-analysis-prompts`
- Governing ADR: `ADR-064` (KEEP_PAUSED)
- Branch: `story/0106-intent-aware-context-utilization`
- Baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`

## Superseding Precision Note

The single canonical invocation-path findings remain valid. Later investigation identified an Analysis-local Fact UUID ranking dependency and isolated it into Story 0107. Statements below attributing the historical `4/1/1` variance solely to LLM nondeterminism are preserved as intermediate investigation history but are superseded: sole historical causality was not demonstrated.

## 2. Investigation Question

> Does the Analysis pipeline have one canonical process for constructing Analysis information, independent of who consumes or triggers it (HUMAN vs MCP/Agent)?

## 3. Executive Summary

**Answer: YES — there is exactly one canonical Analysis invocation path. There is NO second invocation path from MCP/Agent.**

The MCP server has **zero Analysis capabilities**. It cannot create, launch, query, or read Analysis entities. The HUMAN path through the backend REST API is the sole entry point for Analysis. The original concern about HUMAN vs MCP/Agent invocation parity is architecturally moot — there is only one consumer of the Analysis pipeline.

The `4/1/1` corrective runtime variance is **not** caused by divergent invocation paths. It is caused by LLM nondeterminism applied to identical prompt construction fed by nondeterministic knowledge selection from the same pipeline.

## 4. HUMAN Invocation Path (Canonical)

### 4.1 Entry Points

```
POST /api/v1/analyses          (AnalysisController.create)
POST /api/v1/analyses/{id}/workflow  (AnalysisController.startWorkflow)
```

### 4.2 Pipeline Steps (AnalysisWorkflowServiceImpl.start)

1. `AnalysisService.start()` — PENDING → IN_PROGRESS
2. `IntentCatalog.resolve()` — Resolve `IntentDefinition` from hardcoded catalog
3. `AnalysisAiTaskTypeResolver.resolve()` — Map `AnalysisType` + `Intent` → `AiTaskType`
4. `KnowledgeCollectionService.collect()` — Run collectors (facts, observations)
5. `DeterministicAnalysisService.analyze()` — Count facts/observations
6. `ProjectProfileService.build()` — Build project profile snapshot
7. `AnalysisContextService.build()` — Build immutable `AnalysisContext` record
8. `UserGuidance.from()` — Parse user guidance from JSONB
9. `AiTaskService.create()` — Create `AiTask` entity
10. `KnowledgeSelectionService.select()` — Rank + select knowledge (budgeted, closure-safe)
11. `AiTaskService.attachSelectedKnowledge()` — Attach knowledge to task
12. `SelectedKnowledgePromptProjectionService.toMap()` — Project to `Map<String, Object>`
13. `AIEngineClient.submit(PromptRequest(...))` — HTTP POST to AI Engine
14. `AiTaskService.submit()` — Mark task as SUBMITTED

### 4.3 PromptRequest Construction

```java
new PromptRequest(
    createdTask.correlationId(),           // requestId
    createdTask.correlationId(),           // correlationId
    analysisId,                            // analysisId
    createdTask.id(),                      // aiTaskId
    createdTask.taskType(),                // taskType (DECISION_PROPOSAL_GENERATION)
    intent,                                // IntentDefinition
    guidance,                              // UserGuidance
    promptProjectionService.toMap(selectedKnowledge),  // selectedKnowledge (projected)
    intent.outputSchema(),                 // expectedOutputContract
    Map.of("source", "devlog-ai-core", "analysisContextId", analysisId.toString())
)
```

### 4.4 AI Engine Prompt Construction

`EngineeringDecisionPromptBuilder.build(request)` constructs:

- **System message**: `SYSTEM_MESSAGE` — Contains emission gate, independence rule, convergence definition, selectivity guidance, negative guardrails
- **User message**: Composed of 7 sections:
  1. `BUSINESS INTENT` — Serialized `IntentDefinition`
  2. `SHARED_STRUCTURED_CONTEXT_CONTRACT` — `structured_context.py` constant
  3. `INTENT-SPECIFIC SYNTHESIS` — `_decision_strategy()` method output
  4. `BEGIN UNTRUSTED SELECTED KNOWLEDGE` — Serialized `selectedKnowledge` map
  5. `GROUNDING CONTRACT` — Allowed evidence references/IDs
  6. `BEGIN OPTIONAL UNTRUSTED USER GUIDANCE` — Serialized `UserGuidance` (or empty)
  7. `EXPECTED OUTPUT CONTRACT` — Serialized output schema

### 4.5 Information Construction Ownership

| Layer | What It Owns | Deterministic? |
|---|---|---|
| `AnalysisContextService` | Assembles `AnalysisContext` from DB queries | YES (fixed query, fixed limits) |
| `KnowledgeSelectionService.select()` | Ranks observations/facts by intent-specific scoring, applies budget, closure-safe selection | PARTIALLY (scoring functions are deterministic, but selection depends on DB state) |
| `SelectedKnowledgePromptProjectionService` | Projects `SelectedKnowledge` to flat Map, builds relationship highlights, composes semantic sections | YES (fixed projection logic) |
| `EngineeringDecisionPromptBuilder` | Assembles prompt from `PromptRequest` fields | YES (fixed template) |

## 5. MCP/Agent Invocation Path

### 5.1 MCP Server Capabilities

| Type | Name | File | Launches Analysis? |
|---|---|---|---|
| Tool | `echo_message` | `EchoTool.java` | No |
| Tool | `search_project_history` | `SearchProjectHistoryTool.java` | No |
| Tool | `get_engineering_context` | `EngineeringContextTool.java` | No |
| Resource | `server-info` | `ServerInfoResource.java` | No |
| Resource | `projects` | `ProjectsResource.java` | No |
| Resource | `project-context` | `ProjectContextResource.java` | No |
| Resource | `project-commit-context` | `CommitContextResource.java` | No |
| Resource | `project-decision` | `DecisionResource.java` | No |
| Resource | `project-insight` | `InsightResource.java` | No |
| Resource | `project-story` | `EngineeringStoryResource.java` | No |
| Resource | `project-engineering-event` | `EngineeringEventResource.java` | No |
| Resource | `project-freshness` | `FreshnessResource.java` | No |

**Total: 3 tools, 9 resources, 0 Analysis interactions.**

### 5.2 MCP Backend Clients

`DevlogResourceClient` — reads projects, decisions, insights, stories, events, commit context, freshness. **No `/api/v1/analyses/*` endpoints.**

`DevlogProjectContextClient` — reads `ProjectContext` and `EngineeringContext`. **No Analysis endpoints.**

### 5.3 What MCP's `get_engineering_context` Actually Does

```
MCP Agent → EngineeringContextTool.getEngineeringContext(slug, intent)
  → DevlogProjectContextClient.getEngineeringContext(slug, intent)
    → GET /api/v1/projects/{slug}/engineering-context?intent=...
      → EngineeringContextFacadeImpl.getEngineeringContext()
        → ProjectContextProvider.build(projectId)     // builds project context
        → RepositoryContextAdapter.buildRepositoryContext() // builds repo context
        → EngineeringContextContractMapper.toContract() // maps to contract DTO
      → Returns EngineeringContext contract
```

This is a **read-only context retrieval** endpoint. It builds a project context snapshot and returns it as a contract DTO. It does NOT:
- Create an Analysis entity
- Run knowledge collection
- Run knowledge selection
- Submit to AI Engine
- Generate proposals/insights

### 5.4 What MCP Cannot Reach

The backend exposes a full Analysis REST API at `/api/v1/analyses/`:
- `POST /` (create), `POST /{id}/workflow` (start), `GET /{id}` (get), `GET /{id}/result` (results), etc.

**None of these endpoints are referenced in any MCP client interface.** The MCP server cannot create, launch, query, or read Analysis entities.

## 6. Semantic Duplication Audit

### 6.1 Is There Semantic Duplication Between HUMAN and MCP Paths?

**NO.** The two paths serve fundamentally different purposes:

| Aspect | HUMAN Path (Analysis) | MCP Path (EngineeringContext) |
|---|---|---|
| Purpose | Generate NEW proposals/insights via LLM | Provide READ-ONLY project context to external agents |
| Entity | Creates `Analysis` entity | Returns `EngineeringContext` contract DTO |
| Knowledge Selection | Full ranking + budget + closure-safe | None — returns raw project context |
| AI Engine | Submits to AI Engine for generation | Not involved |
| Output | Proposals, insights, deliverables | Project context snapshot |
| Trigger | POST workflow endpoint | GET context endpoint |

They are **complementary**, not duplicative. The MCP path provides context; the Analysis path generates knowledge.

### 6.2 Is There Shared Infrastructure?

Both paths share:
- `ProjectContextProvider` — builds project context
- `RepositoryContextAdapter` — builds repository context
- `ProjectFreshnessService` — project freshness

But they use it differently:
- Analysis: `ProjectContextProvider` is used inside `AnalysisContextService.build()` as one of many inputs to `AnalysisContext`
- MCP: `ProjectContextProvider` is the primary data source for the `EngineeringContext` contract

**No semantic duplication detected.**

## 7. PromptRequest Comparison (Three Corrective Runtime Runs)

### 7.1 Prompt Construction Identical Across Runs

All three corrective runtime runs use:
- Same `SYSTEM_MESSAGE` (corrective version with all 7 rules)
- Same `SHARED_STRUCTURED_CONTEXT_CONTRACT`
- Same `_decision_strategy()` output
- Same `IntentDefinition` (analyze-engineering-decision v1)
- Same `expectedOutputContract` (engineering-decision-proposal-v1)
- Same `GenerationPolicy(10, 2_000, True)`

### 7.2 Selected Knowledge Differs Between Runs

The `selectedKnowledge` map content varies between runs because `KnowledgeSelectionService.select()` operates on the same `AnalysisContext` but with potential nondeterminism in:
- Observation/fact scoring (intent-specific scoring functions applied to ranked lists)
- Budget enforcement (when ties occur at budget boundaries)
- Deduplication (content-key based, but ordering may vary)

This is **expected** — knowledge selection is a ranking operation, and ranking at the margin can vary.

### 7.3 Grounding Contract Differs

The grounding contract (`allowedEvidenceReferences`, `allowedSupportingFactIds`, `allowedSupportingObservationIds`) is derived from the selected knowledge, so it varies with selection.

### 7.4 User Guidance Consistent

All three runs have `userGuidance = null` (no user guidance provided for benchmark runs).

### 7.5 Classification of PromptRequest Differences

| Dimension | Identical? | Notes |
|---|---|---|
| System message | YES | Same corrective version |
| Structured context contract | YES | Same shared constant |
| Intent-specific strategy | YES | Same `_decision_strategy()` |
| Intent definition | YES | Same analyze-engineering-decision v1 |
| Expected output contract | YES | Same schema |
| Generation policy | YES | Same parameters |
| Selected knowledge content | NO | Varies per selection run |
| Grounding contract | NO | Derived from selected knowledge |
| User guidance | YES | Both null |

**Conclusion: The prompt construction process is identical. The only variance is in the input data (selected knowledge), which is expected behavior for a ranking-based selection system.**

## 8. Root Cause of 4/1/1 Variance

### 8.1 Variance Source Identification

The `4/1/1` pattern (Run 1: 4 proposals, Runs 2-3: 1 proposal each) is caused by:

1. **LLM nondeterminism** — The same prompt with the same (or similar) input data produces different outputs across runs
2. **Knowledge selection variance** — Minor differences in selected knowledge may tip the LLM's emission gate decision at the margin
3. **Temperature non-zero** — `gpt-4.1-mini` with default temperature introduces randomness

### 8.2 What the Variance Is NOT Caused By

- ❌ Divergent invocation paths (MCP has no Analysis capability)
- ❌ Different prompt construction logic (same prompt builder, same template)
- ❌ Different system messages (same corrective version)
- ❌ Semantic duplication between HUMAN and MCP paths (no overlap)
- ❌ Different `IntentDefinition` (same intent for all runs)
- ❌ Different output schemas (same schema for all runs)

### 8.3 What Would Eliminate the Variance

Options to reduce LLM nondeterminism:
- Set `temperature=0` in `GenerationPolicy` (currently `True` for `temperature` — meaning model default)
- Use `seed` parameter for OpenAI API (if supported by model)
- Increase corrective prompt strictness further (but diminishing returns)
- Accept 2/3 success rate as within acceptable bounds for stochastic generation

## 9. Architecture Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                        HUMAN (UI/API)                        │
│  POST /api/v1/analyses  →  POST /{id}/workflow               │
└──────────────────────┬───────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│              AnalysisWorkflowServiceImpl.start()              │
│                                                              │
│  1. AnalysisService.start()                                  │
│  2. IntentCatalog.resolve()                                  │
│  3. AnalysisAiTaskTypeResolver.resolve()                     │
│  4. KnowledgeCollectionService.collect()                     │
│  5. DeterministicAnalysisService.analyze()                   │
│  6. ProjectProfileService.build()                            │
│  7. AnalysisContextService.build()          ──┐              │
│  8. UserGuidance.from()                        │              │
│  9. AiTaskService.create()                     │              │
│ 10. KnowledgeSelectionService.select() ───────────────┐      │
│ 11. AiTaskService.attachSelectedKnowledge()           │      │
│ 12. SelectedKnowledgePromptProjectionService.toMap() ──┐│     │
│ 13. AIEngineClient.submit(PromptRequest) ───────────────────┐│
│ 14. AiTaskService.submit()                                  ││
└─────────────────────────────────────────────────────────────┘│
                                                               │
                       ▼                                       │
┌──────────────────────────────────────────────────────────────┐│
│                    AI Engine (ai-engine)                      ││
│                                                              ││
│  EngineeringDecisionPromptBuilder.build(PromptRequest)       ││
│  ├── SYSTEM_MESSAGE (corrective rules)                       ││
│  ├── BUSINESS INTENT                                         ││
│  ├── SHARED_STRUCTURED_CONTEXT_CONTRACT                      ││
│  ├── INTENT-SPECIFIC SYNTHESIS (_decision_strategy)          ││
│  ├── SELECTED KNOWLEDGE (from PromptRequest.selectedKnowledge)││
│  ├── GROUNDING CONTRACT                                      ││
│  ├── USER GUIDANCE                                           ││
│  └── EXPECTED OUTPUT CONTRACT                                ││
│                                                              ││
│  → LLM generates proposals                                   ││
│  → Callback to Backend POST /api/v1/ai/tasks/{id}/result     ││
└──────────────────────────────────────────────────────────────┘│
                                                               │
┌──────────────────────────────────────────────────────────────┐│
│                   MCP Server (mcp-server)                     ││
│                                                              ││
│  Tools: echo_message, search_project_history,                ││
│         get_engineering_context                              ││
│                                                              ││
│  get_engineering_context:                                    ││
│    → GET /api/v1/projects/{slug}/engineering-context          ││
│    → EngineeringContextFacade → ProjectContextProvider        ││
│    → Returns EngineeringContext contract DTO                 ││
│                                                              ││
│  ❌ NO /api/v1/analyses/* endpoints in MCP client            ││
│  ❌ NO Analysis creation, launch, or query capabilities      ││
│  ❌ NO Analysis result retrieval                             ││
└──────────────────────────────────────────────────────────────┘│
                                                               │
                       ┌───────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────┐
│              AiTaskResultController (Callback)                │
│                                                              │
│  POST /api/v1/ai/tasks/{correlationId}/result                │
│  → AiTaskResultService.handle()                              │
│  → Proposals/Insights persisted to DB                        │
└──────────────────────────────────────────────────────────────┘
```

## 10. Conclusions

### 10.1 Canonical Pipeline Question

**There is exactly one canonical Analysis invocation path.** The MCP server has zero Analysis capabilities. The HUMAN path through `AnalysisWorkflowServiceImpl.start()` is the sole entry point.

### 10.2 Invocation Parity Question

**Not applicable.** There is no second invocation path to achieve parity with. The MCP server serves a complementary (read-only context retrieval) purpose, not a competing (Analysis generation) purpose.

### 10.3 Variance Root Cause

The `4/1/1` corrective runtime variance is caused by **LLM nondeterminism**, not by architectural divergence. The prompt construction process is identical across runs; only the input data (selected knowledge) varies slightly due to ranking nondeterminism.

### 10.4 Implications for Story 0106

The corrective prompt implementation (Options A+B+C+D) is architecturally clean — it modifies exactly one prompt template, which is used by exactly one invocation path. There is no risk of divergent behavior across callers because there is only one caller.

## 11. Explicit Non-Actions

- no production code changes
- no prompt changes
- no test changes
- no schema changes
- no commits
- no pushes
- no merges

## 12. HUMAN Review Gate

This investigation provides evidence for HUMAN review. It does NOT:

- declare Story 0106 accepted
- declare the implementation approved
- authorize commit
- authorize push
- authorize merge

---

`CANONICAL_ANALYSIS_INFORMATION_PIPELINE_INVESTIGATION_COMPLETE`
