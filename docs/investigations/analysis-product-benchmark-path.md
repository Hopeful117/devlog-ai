# Investigation: Analysis Product Benchmark Path — End-to-End Execution Trace

Investigation-only artifact — zero production changes.

---

# 1. Objective

Determine exactly how a human-triggered Analysis is executed today and whether there is a
reproducible REST/API path that exercises the complete product flow. The current concern is that
the proposed benchmark route (`/api/v1/projects/{project}/engineering-context?intent=`) exercises
the MCP / engineering-context path rather than the actual human-facing Analysis product workflow.

---

# 2. ENGINEERING_CONTEXT_ROUTE_TRACE

```
GET /api/v1/projects/{projectSlug}/engineering-context?intent={intent}
  │
  ▼
EngineeringContextController.getEngineeringContext()           [backend REST]
  │
  ▼
EngineeringContextFacadeImpl.getEngineeringContext()          [backend app service]
  ├── projectService.getBySlug(projectSlug)                  → Project entity
  ├── projectContextProvider.build(projectId)                → ProjectContextSnapshot
  ├── repositoryContextAdapter.buildRepositoryContext(...)    → RepositoryContext
  │     ├── synthesizeAnalysisContext(...)                   → synthetic AnalysisContext (NOT persisted)
  │     ├── createIntentDefinition(intent)                   → IntentDefinition (key="engineering-story-preparation")
  │     ├── insightRepository.findByProjectIdAndStatusIn()   → validated insights
  │     └── repositoryContextService.build(4-arg overload)   → RepositoryContext
  │           ├── DeterministicContextIntelligence.plan()    → ContextPlan
  │           ├── 6 RepositoryContextCollectors.collect()    → candidates
  │           ├── DeterministicEvidenceRanker.rank()         → ranked candidates
  │           └── BudgetedDiverseEvidenceSelector.select()   → 60 selected items
  │
  └── mapper.toContract(...)                                 → EngineeringContext DTO
```

**Layers it DOES traverse:**
- RepositoryContextEngine (plan → collect → rank → select → enrich → diagnose)
- BudgetedDiverseEvidenceSelector (the composition mechanism under test)
- DeterministicEvidenceRanker
- EvidencePrecisionPolicy (ENGINEERING_STORY_PRECISION)

**Layers it DOES NOT traverse:**
- KnowledgeSelectionService (the Analysis pipeline's own selection)
- Analysis creation/persistence
- AiTask creation/persistence
- Prompt construction
- AI provider/LLM invocation
- Result callback/persistence
- ValidatableProposal creation
- Any human-visible output

---

# 3. IS_ENGINEERING_CONTEXT_EFFECTIVELY_MCP_PATH

**YES**

The MCP `get_engineering_context` tool (`EngineeringContextTool.java:16`) calls
`DevlogProjectContextClient.getEngineeringContext()` which proxies
`GET /api/v1/projects/{slug}/engineering-context?intent=`. The backend endpoint is the same.
The MCP tool is a thin STDIO-to-HTTP protocol adapter with zero business logic.

---

# 4. MCP_PATH_TRACE

```
MCP Client (stdio JSON-RPC)
  │
  ▼
EngineeringContextTool.getEngineeringContext()     [mcp-server, @McpTool]
  │  serializes to JSON string
  ▼
DevlogProjectContextClient.getEngineeringContext() [mcp-server, HTTP proxy]
  │  GET /api/v1/projects/{slug}/engineering-context?intent=
  ▼
[Same backend path as §2]
  │
  ▼
HTTP 200 EngineeringContext (JSON)
  │
  ▼
objectMapper.writeValueAsString() → MCP Response
```

Divergence from real Analysis workflow: **IMMEDIATE**. The MCP path returns raw selected
evidence. The Analysis path creates entities, collects knowledge, selects knowledge, builds
prompts, invokes LLM, persists results. They share only the `RepositoryContextEngine`
composition layer.

---

# 5. REAL_ANALYSIS_ENTRYPOINT

```
POST /api/v1/analyses
Body: {
  projectId: UUID,
  type: "ARCHITECTURE_REVIEW" | "PROJECT_EVOLUTION",
  intentId: "architecture-overview-v1" | "describe-project-v1" | ...,
  targetRevision?: String,
  userGuidance?: { focus, audience, levelOfDetail, writingStyle, outputContext, priorities }
}
→ Returns: AnalysisDetail (id, status=PENDING)
```

---

# 6. REAL_ANALYSIS_EXECUTION_PATH

```
Step 1: POST /api/v1/analyses
  → AnalysisController.create()
  → AnalysisServiceImpl.create()
  → Persists Analysis entity (status=PENDING)
  → Returns AnalysisDetail

Step 2: POST /api/v1/analyses/{id}/workflow
  → AnalysisController.startWorkflow()
  → AnalysisWorkflowServiceImpl.start(analysisId)    ← THE ORCHESTRATOR
      │
      ├── analysisService.start()                    → Analysis: PENDING → IN_PROGRESS [PERSISTS]
      ├── intentCatalog.resolve()                    → IntentDefinition
      ├── taskTypeResolver.resolve()                 → AiTaskType
      ├── knowledgeCollectionService.collect()       → Facts + Observations [PERSISTS]
      ├── deterministicAnalysisService.analyze()     → counts (read-only)
      ├── projectProfileService.build()              → ProjectProfile [PERSISTS]
      ├── analysisContextService.build()             → AnalysisContext (in-memory)
      ├── aiTaskService.create(request, context)     → AiTask (CREATED) [PERSISTS contextSnapshot]
      ├── knowledgeSelectionService.select()         → SelectedKnowledge (in-memory)
      ├── aiTaskService.attachSelectedKnowledge()    → [PERSISTS selectedKnowledgeSnapshot as JSONB]
      ├── aiEngineClient.submit(PromptRequest)       → POST /api/v1/ai/tasks → Python AI Engine
      │     │                                        → Returns 202 + externalJobId
      │     │
      │     │  [Python AI Engine background]
      │     │  ├── InsightPromptBuilder.build()      → Prompt (system + user message)
      │     │  ├── LlmProvider.generate_structured() → LLM invocation
      │     │  ├── Validate output                   → retry once on failure
      │     │  └── CoreCallbackClient.send_result()  → POST /api/v1/ai/tasks/{correlationId}/result
      │     │
      │     ▼
      └── aiTaskService.submit()                     → AiTask: CREATED → SUBMITTED [PERSISTS]
  → Returns AnalysisWorkflowResult

Step 3: [Callback arrives asynchronously]
  POST /api/v1/ai/tasks/{correlationId}/result
  → AiTaskResultController.receiveResult()
  → AiTaskResultServiceImpl.handle()
      ├── Validates contract + pessimistic lock
      ├── Persists PromptExecutionMetadata on AiTask (provider, model, digests)
      ├── Persists ValidatableProposal entities [PERSISTS]
      ├── AiTask: → COMPLETED [PERSISTS]
      └── Analysis: → COMPLETED [PERSISTS]
```

---

# 7. REAL_ANALYSIS_RESULT_RETRIEVAL_PATH

```
Frontend polls (timer(0, 5000ms)):
  GET /api/v1/analyses/{id}/diagnostics       → AnalysisDiagnostics (stops on COMPLETED/FAILED)
  GET /api/v1/ai-tasks/analysis/{id}          → AiTaskDetail[] (stops on terminal status)

Once complete:
  GET /api/v1/analyses/{id}                   → AnalysisDetail (status, timestamps)
  GET /api/v1/analyses/{id}/selected-evidence  → AiTaskSelectedEvidenceResponse (knowledge snapshot)
  GET /api/v1/proposals/analysis/{id}          → InsightProposalSummary[] (validatable proposals)
  GET /api/v1/insights/analysis/{id}           → InsightSummary[] (validated insights)
  GET /api/v1/analyses/{id}/warnings           → CollectionWarningDto[]
  GET /api/v1/analyses/{id}/context            → Raw AnalysisContext JSON
  GET /api/v1/analyses/{id}/profile            → ProjectProfile
```

---

# 8. ANALYSIS_BENCHMARK_CLASSIFICATION

**MULTI_STEP_API_WORKFLOW**

The real Analysis workflow requires:
1. `POST /api/v1/analyses` (creates PENDING Analysis)
2. `POST /api/v1/analyses/{id}/workflow` (synchronous orchestrator: collection → selection → AI engine submission)
3. Wait for async callback (Python AI Engine background processing + callback)
4. Poll `GET /api/v1/ai-tasks/analysis/{id}` until terminal status
5. Read `GET /api/v1/analyses/{id}/selected-evidence` for knowledge snapshot
6. Read `GET /api/v1/proposals/analysis/{id}` for generated proposals

This can be reproduced from CLI via HTTP calls, but requires:
- A running AI Engine (Python FastAPI) with valid LLM provider credentials
- A valid project with imported Git history
- Polling logic to detect completion
- All steps are deterministic except the LLM invocation itself

---

# 9. DIAGNOSTIC_BENCHMARK_PATH

```
GET /api/v1/projects/devlog-ai/engineering-context?intent={intent}
```

This exercises RepositoryContextEngine + BudgetedDiverseEvidenceSelector directly. It is the
correct diagnostic path for measuring CATEGORY_SELECTION mechanics. It does NOT require the
AI Engine to be running.

---

# 10. PRODUCT_BENCHMARK_PATH

```
1. POST /api/v1/analyses
   Body: {
     projectId: devlog-ai-project-id,
     type: "ARCHITECTURE_REVIEW",
     intentId: "architecture-overview-v1",
     userGuidance: null
   }
   → analysisId

2. POST /api/v1/analyses/{analysisId}/workflow
   → aiTaskId, correlationId

3. Poll GET /api/v1/ai-tasks/analysis/{analysisId}
   → wait until status = COMPLETED or FAILED

4. GET /api/v1/analyses/{analysisId}/selected-evidence
   → knowledge snapshot (evidence that reached the LLM)

5. GET /api/v1/proposals/analysis/{analysisId}
   → generated proposals (LLM output)

6. GET /api/v1/insights/analysis/{analysisId}
   → validated insights (post-acceptance)
```

**BLOCKER for automation:** The5 benchmark intents (history, architecture, recent-sync,
persistence, decision-governance) do NOT map to registered IntentDefinition IDs. The 5
registered intents are: `describe-project-v1`, `generate-readme-v1`,
`architecture-overview-v1`, `analyze-engineering-event-v1`,
`analyze-engineering-decision-v1`.

---

# 11. FIVE_INTENT_INPUT_MAPPING

| Benchmark Intent | Registered Intent Match | Mapping |
|---|---|---|
| `history` | None exact. `describe-project-v1` has contextProfiles `["project-state-v1","history-v1"]` | `describe-project-v1` (closest) |
| `architecture` | `architecture-overview-v1` | Direct match (id differs: "architecture" vs "architecture-overview") |
| `recent-sync` | None | **No mapping exists.** |
| `persistence` | None | **No mapping exists.** |
| `decision-governance` | `analyze-engineering-decision-v1` | Partial match |

The benchmark `?intent=` parameter is a free-form string passed to
`RepositoryContextAdapter.createIntentDefinition()` which uses it as the `objective` text for
`IntentTerms.extract()`. It is NOT resolved against `IntentCatalog`. The Analysis workflow
DOES resolve against `IntentCatalog` and requires a valid registered intent ID.

**This means the5 benchmark intents are only valid for the engineering-context diagnostic
endpoint, not for the real Analysis workflow.**

---

# 12. CURRENT_OBSERVABILITY

| Data | Persisted? | Where | Observable via |
|---|---|---|---|
| Facts | YES | `facts` table | `GET /api/v1/analyses/{id}/context` |
| Observations | YES | `observations` table | Same as above |
| Collection Warnings | YES | `collection_warnings` table | `GET /api/v1/analyses/{id}/warnings` |
| AnalysisExecutionDiagnostic | YES | `analysis_execution_diagnostics` table | `GET /api/v1/analyses/{id}/diagnostics` |
| Analysis entity | YES | `analyses` table | `GET /api/v1/analyses/{id}` |
| AiTask entity | YES | `ai_tasks` table | `GET /api/v1/ai-tasks/analysis/{id}` |
| selectedKnowledgeSnapshot | YES | `ai_tasks.selected_knowledge_snapshot` (JSONB) | `GET /api/v1/analyses/{id}/selected-evidence` |
| ValidatableProposal entities | YES | `validatable_proposals` table | `GET /api/v1/proposals/analysis/{id}` |
| Prompt text | **NO** | Only SHA-256 digest on AiTask | Digest only |
| Raw LLM response | **NO** | Not stored anywhere | Not observable |
| PromptRequest | **NO** | Transient DTO | Not observable |

**What CAN support a benchmark without adding logging:**
- Knowledge snapshot (selected evidence) → `GET /api/v1/analyses/{id}/selected-evidence`
- Generated proposals → `GET /api/v1/proposals/analysis/{id}`
- Collection diagnostics → `GET /api/v1/analyses/{id}/diagnostics`
- Analysis status/timing → `GET /api/v1/analyses/{id}`

**What CANNOT be observed:**
- The actual prompt sent to the LLM (only a digest)
- The raw LLM response text
- The prompt construction process in the Python AI Engine

---

# 13. STORY_0098_BENCHMARK_REVIEW

## Current Benchmark Issues

1. **Engineering-context endpoint called "Analysis benchmark":** Story 0098's verification
   section uses `curl localhost:18080/api/v1/projects/devlog-ai/engineering-context?intent=<intent>`
   and labels it as "benchmark confirms improvement." This endpoint does NOT exercise the Analysis
   product workflow. It exercises RepositoryContextEngine only. It is a diagnostic composition
   benchmark, not a product Analysis benchmark.

2. **Five benchmark intents invalid for Analysis workflow:** The5 intents (history, architecture,
   recent-sync, persistence, decision-governance) are free-form strings that work with the
   engineering-context endpoint's `createIntentDefinition()` but do NOT resolve against
   `IntentCatalog`. They cannot be used to create a real Analysis via `POST /api/v1/analyses`.

3. **Acceptance criterion validates only intermediate context:** "COMMIT_DIFF ≤ 12 items across
   all five intents" measures composition at the RepositoryContextEngine layer. It says nothing
   about whether the Analysis product output improves.

4. **No product-level acceptance criterion:** There is no criterion that validates the final
   human-visible Analysis output, the generated proposals, the knowledge snapshot reaching the LLM,
   or the grounding quality of the output.

## Required Corrections

1. **Rename benchmark sections:** Distinguish clearly between "Diagnostic Composition Benchmark"
   (engineering-context endpoint) and "Product Analysis Benchmark" (real workflow).

2. **Fix acceptance criterion 2:** "COMMIT_DIFF ≤ 12 items" should be explicitly labeled as a
   diagnostic composition measurement, not a product Analysis benchmark.

3. **Add product benchmark criterion:** An acceptance criterion should require evidence that the
   Analysis product workflow produces output with improved category diversity.

4. **Fix verification section:** The engineering-context curl should be labeled as the diagnostic
   benchmark, not the sole verification path.

5. **Document intent mapping limitation:** The5 diagnostic intents are not valid for the real
   Analysis workflow.

---

# 14. STORY_0098_BENCHMARK_READINESS

**BLOCKED**

The real Analysis product cannot currently be benchmarked end-to-end from CLI because:
1. The5 benchmark intents don't map to registered IntentDefinitions
2. The workflow requires a running AI Engine with valid LLM credentials
3. The workflow is async (callback-based) requiring polling logic
4. No automated benchmark tooling exists for the multi-step workflow

The diagnostic benchmark (engineering-context endpoint) IS exercisable and IS valid for
measuring CATEGORY_SELECTION mechanics. But it is insufficient to declare product success.

---

# 15. PREREQUISITE_STORY_REQUIRED

**YES**

## PREREQUISITE_STORY_PURPOSE

**Make the real Analysis workflow benchmarkable from CLI.**

The smallest prerequisite story would:
1. Create a simple CLI/script that exercises the complete multi-step Analysis workflow
   (create → launch → poll → read results)
2. Map the5 diagnostic intents to valid registered IntentDefinition IDs for the Analysis workflow
3. Document which Analysis types and intents are benchmarkable
4. Verify the selected-evidence and proposals endpoints return actionable data

This is NOT about solving CATEGORY_SELECTION. It is about establishing a reproducible product
benchmark path so that Story 0098 (and future stories) can be validated against the real product
output, not just an intermediate context endpoint.

---

# 16. NEW_ADR_REQUIRED

**NO**

ADR-063 §5 and §23 already authorize category ceilings. The issue is benchmark methodology,
not architecture.

---

# 17. GIT_STATUS

Current HEAD: `d6ea713` (Story 0097 merge commit on main). No uncommitted changes.

---

ANALYSIS_PRODUCT_BENCHMARK_INVESTIGATION_AWAITING_HUMAN_REVIEW
