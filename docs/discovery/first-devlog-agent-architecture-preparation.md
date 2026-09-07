# DevLog AI — First DevLog Agent Architecture Preparation Report

## 1. Repository Truth

**Branch/HEAD:** `main` @ `3550c78` (Merge PR #94: structured engineering context)
**Worktree:** Clean (only untracked `data/` and build artifacts)
**Accepted ADRs:** ADR-006 (AI Proposal Workflow), ADR-063 (Context Retrieval/Composition), ADR-064 (Hybrid Analysis Composition), ADR-065 (Synthesis/Proposal Separation), ADR-066 (Replayable Evaluation Harness)
**Story 0111:** ACCEPTED — Structured Engineering Context for Agent Consumption
**EngineeringContext Contract:** `EngineeringContext(project, intent, evidence[], metadata, sections[], requestEcho)` with TrustTier classification (TRUSTED/HUMAN_AUTHORED/TECHNICAL_EVIDENCE/SYSTEM_METADATA), ContextSection partitioning, files[]/storyId scoping
**MCP Server:** Exposes `get_engineering_context` tool with `projectSlug, intent, files[], storyId` parameters
**AI Engine:** Python FastAPI with Intent-driven generation (Insight, Engineering Event, Decision), corrective retry, structured output validation, callback to Core
**IntentCatalog:** 5 registered intents (describe-project-v1, generate-readme-v1, architecture-overview-v1, architecture-overview-v2, analyze-engineering-event-v1, analyze-engineering-decision-v1)
**PromptRequest → AI Engine → AiTaskResultRequest:** Full cycle with synthesis (ADR-065) and proposals (ADR-006)
**ValidatableProposal Workflow:** PROPOSED → ACCEPTED/REJECTED → atomic promotion to trusted knowledge
**Replayable Evaluation Harness:** ADR-066, Intent-level, replay-first, deterministic CI
**Engineering Story Lifecycle:** REGISTERED → IN_PROGRESS(baseCommit) → COMPLETED(targetCommit)
**Kiko Workflow:** Creates stories, consumes context via MCP, implements externally, no Discuss/Plan/Review tooling in DevLog

---

## 2. Accepted Baseline (Constraints)

| Constraint | Status |
|------------|--------|
| `EngineeringContext` is canonical agent-facing context | ✅ Enforced by ADR-063, Story 0111 |
| No new ContextPack | ✅ ADR-063 §14/§35 explicitly deferred |
| First agent: DevLog Engineering Story Context Agent | ✅ Candidate from discovery |
| V1 exclusions: RAG, vector DB, embeddings, OpenClaw, autonomous coding, code modification, persistent agent memory, generic multi-agent orchestration, automatic trusted-knowledge mutation | ✅ Mandated |
| Trust: Agent must not directly create/modify trusted knowledge | ✅ ADR-006 |
| Human authority mandatory | ✅ ADR-006, ADR-063 Human Context Supremacy |

---

## 3. Core Design Question

> How should DevLog introduce its first read-only AI agent capability for Engineering Story analysis while preserving EngineeringContext as the canonical context boundary, human authority, trust semantics, grounding, and future compatibility with Developer OS?

**Must be broken into explicit sub-decisions for human discussion.**

---

## 4. Architectural Decisions Requiring Human Input

### Decision A — Agent Boundary

**Where does the agent conceptually live?**

| Candidate | Description | Ownership |
|-----------|-------------|-----------|
| **DevLog Backend/Core** | Application use case `AnalyzeStoryContext`; orchestrates context acquisition + AI invocation + validation | Core owns agent logic; AI Engine is a service |
| **AI Engine** | New Intent `engineering-story-context-analysis-v1` with dedicated service; AI Engine owns full generation | AI Engine owns agent logic; Core is transport |
| **MCP Layer** | MCP tool `analyze_story_context` that calls context + AI Engine; MCP as orchestration boundary | MCP owns agent contract; backend/AI Engine are implementations |
| **Orchestration Across** | Thin coordinator in backend; delegates to EngineeringContextFacade + AI Engine client; no new service | Shared ownership; no single component "owns" agent |

**Key Tension:** Implementation spans components (context acquisition in backend, AI generation in AI Engine, transport via MCP/REST), but **architectural ownership** must be assigned to one bounded responsibility.

**Repository Evidence:** ADR-063 §22: "RepositoryContextEngine remains a bounded composer" — backend owns composition. ADR-065: AI Engine owns synthesis generation. ADR-066: AI Engine owns evaluation. Current pattern: backend orchestrates, AI Engine generates.

---

### Decision B — Invocation Path

**How should the human invoke the first agent?**

| Candidate | Flow | Developer Experience | Coupling |
|-----------|------|---------------------|----------|
| **MCP Tool** | `analyze_story_context(projectSlug, storyId, intent, files[])` → returns structured analysis | Native for Kiko (MCP client); matches existing `get_engineering_context` pattern | Ties agent to MCP transport |
| **REST Endpoint** | `POST /api/v1/projects/{slug}/stories/{storyId}/analyze-context` → returns analysis | Works for any HTTP client; testable via curl | Ties agent to REST transport |
| **Both (Transport Adapters)** | Backend use case `AnalyzeStoryContext`; MCP tool + REST controller both delegate to it | Best of both; future-proof | Requires backend use case layer |
| **EngineeringContext Tool + Separate AI Task** | 1. `get_engineering_context` 2. Human copies context 3. Submits AI task manually | Flexible but high friction; not "agent" UX | Decoupled but manual |
| **Dedicated Agent Command** | New CLI/opencode subcommand `devlog agent analyze-story --story-id=...` | Developer-native; explicit | New transport |

**Repository Evidence:** Story 0111 MCP tool `get_engineering_context` already accepts `files[]` and `storyId`. Current pattern: single MCP tool per capability. ADR-063 §25: "MCP remains a transport and consumer boundary" — not the architecture itself.

---

### Decision C — Context Acquisition

**Should the agent consume an already-built `EngineeringContext`, request it internally, or reconstruct SelectedKnowledge separately?**

| Approach | Flow | Duplication Risk | Canonical Context Preserved? |
|----------|------|------------------|------------------------------|
| **1. Consume Pre-built EngineeringContext** | Caller (MCP/REST) builds `EngineeringContext` via existing facade → passes to agent | Low — single context construction | ✅ Yes — one canonical context |
| **2. Agent Requests EngineeringContext Internally** | Agent receives `projectSlug, storyId, intent, files[]` → calls `EngineeringContextFacade` internally | Medium — context construction logic duplicated if multiple entry points | ✅ Yes — but internal call |
| **3. Reconstruct SelectedKnowledge** | Agent uses `KnowledgeSelectionService` directly (like Analysis workflow) | High — diverges from EngineeringContext composition; different budgets/selection | ❌ No — SelectedKnowledge ≠ EngineeringContext |

**Repository Evidence:** Story 0111 explicitly builds `EngineeringContext` as the MCP consumer composition (ADR-063 §2/§5). `EngineeringContextFacade` + `EngineeringContextContractMapper` is the composition pipeline. `KnowledgeSelectionService` builds `SelectedKnowledge` for Analysis workflow — different consumer, different budgets (ADR-063 §5). **EngineeringContext is the canonical agent-facing context.**

---

### Decision D — V1 Output Size and Structure

The discovery proposed a large `StoryContextAnalysisResult`. Evaluate three contracts:

#### Minimal Contract
```json
{
  "objectiveUnderstanding": { "summary", "keyGoals[]", "successCriteria[]", "confidence" },
  "relevantContext": [{ "section": "trusted_knowledge|human_context|technical_evidence|system_metadata", "items": [...] }],
  "constraints": [{ "type", "description", "severity", "evidenceReferences[]" }],
  "impactedComponents": [{ "component", "impactType", "confidence", "evidenceReferences[]" }],
  "uncertainties": [{ "area", "description", "reason", "suggestedInvestigation" }],
  "questionsBeforeImplementation": [{ "question", "category", "priority", "context" }],
  "provenance": { "contextDigest", "evidenceCounts", "timestamp" }
}
```

#### Rich Contract (Discovery Proposal)
Full separation: `relevantArchitecture[]`, `relevantDecisions[]`, `relevantEvidence[]`, `historicalContext[]`, `missingInformation[]`, `outputClassification { factualExtraction, aiInterpretation, proposal, recommendation }`

#### Hybrid Contract
Small stable core (minimal) + optional structured sections that can evolve independently.

| Dimension | Minimal | Rich | Hybrid |
|-----------|---------|------|--------|
| **Complexity** | Low | High | Medium |
| **Prompt Stability** | High (stable schema) | Low (many fields) | Medium |
| **Evaluation Complexity** | Low (few checks) | High (many dimensions) | Medium |
| **Frontend/MCP Usability** | Simple display | Rich structured views | Progressive disclosure |
| **Future Evolution** | Add sections additively | Hard to change | Add optional sections |
| **Usefulness for Kiko** | Core value | Comprehensive | Balanced |

**Key Question:** Does V1 need separate `relevantArchitecture` and `relevantDecisions` sections, or can they be derived from `relevantContext` with `section=trusted_knowledge`?

---

### Decision E — Transient vs Persisted Output

| Option | Description | Auditability | Stale Analysis Risk | DB Complexity | UX |
|--------|-------------|--------------|---------------------|---------------|-----|
| **Transient Only** | Generated per request; returned via MCP/REST; not stored | Low — no history | None | None | Simple; re-run for refresh |
| **Persisted StoryAnalysis** | New entity `StoryAnalysis` linked to EngineeringStory; stored on generation | High — full history | High — may become stale after repo changes | New table, migrations | Rich history; diff across runs |
| **Hybrid (V1 Transient)** | Transient V1; explicit persistence capability designed for later | Medium — replay via evaluation | Low — always fresh | None initially | Flexible; can persist later |

**Repository Evidence:** ADR-065: "Analysis Synthesis is persisted as an immutable, execution-scoped, non-trusted snapshot on the existing `AiTask` entity... scoped to the Analysis execution, not the project; no cross-run reuse without explicit future ADR authorization." This suggests **transient-by-default** with scoped persistence only when needed.

---

### Decision F — Proposal Support in V1

| Option | Description | ADR-006 Compliance | Human Review Burden | Usefulness |
|--------|-------------|-------------------|---------------------|------------|
| **ANALYSIS_ONLY** | No proposals generated; pure synthesis/analysis | ✅ Trivial — no proposals to validate | None | Focused on understanding |
| **Optional ValidatableProposal** | Agent may embed proposals in output; human reviews if present | ✅ Compliant — proposals follow ADR-006 | Additional review step | Can surface actionable deltas |
| **Mandatory Proposals** | Agent must propose at least one ValidatableProposal | ✅ Compliant | Always requires review | Forces actionability |

**Discovery Recommendation:** `ANALYSIS_ONLY` for V1 — agent's purpose is context analysis for planning, not knowledge proposal. Proposals are separate Intents (architecture-overview-v2, analyze-engineering-decision-v1, etc.).

**Challenge:** If agent identifies a clear architecture delta, should it stay silent? Or should it surface as a `recommendation` (non-proposal) that human can manually promote via existing Intents?

---

### Decision G — Grounding Model

**What does it mean for an agent claim to be grounded?**

| Policy | Every Section? | Factual Claims? | Interpretations? | Recommendations? | Confidence Sufficient? |
|--------|----------------|-----------------|------------------|------------------|------------------------|
| **Strict** | ✅ All claims | ✅ Required | ✅ Required | ✅ Required (or labeled ungrounded) | ❌ No — must reference evidence |
| **Section-Aware** | `relevantContext` items must have refs | ✅ Required | ✅ Required | ⚠️ Labeled "ungrounded recommendation" | ❌ No |
| **Confidence-Based** | Optional | ✅ Required | ✅ With refs | ✅ Allowed ungrounded with LOW confidence | ⚠️ Partial |

**Invariant to Preserve:** `known A + known B != A → B` — explicit `KnowledgeRelation` edges required for causal claims.

**Repository Evidence:** ADR-065 §6: "Every substantive architecture claim in the synthesis must be grounded in the selected point-in-time context." ADR-063 §134: "visible-but-non-citable elements must be deterministically distinguishable from citable ones." ADR-066: deterministic grounding constraints (allow-lists) + qualitative grounding assessment.

---

### Decision H — Relationship Semantics

**How should the agent represent relationships?**

| Relationship Type | Definition | Required Evidence | Contract Representation |
|-------------------|------------|-------------------|-------------------------|
| **Explicit Relation** | `KnowledgeRelation` edge (RESOLVES, CAUSED_BY, RELATES_TO, DERIVED_FROM, ADDRESSES, INFORMED_BY) | Persisted `KnowledgeRelation` entity | `relationType: "EXPLICIT", relationEdgeId, sourceRef, targetRef` |
| **Temporal Proximity** | Commits/events in same story window or adjacent time | Commit timestamps, story base/target commits | `relationType: "TEMPORAL_PROXIMITY", windowStart, windowEnd` |
| **Possible Relevance** | Shared symbols, file paths, architectural keywords | Symbol overlap, file path prefix, IntentTerms match | `relationType: "POSSIBLE_RELEVANCE", confidence: MEDIUM|LOW` |
| **Inferred Hypothesis** | AI-generated correlation not explicitly persisted | Grounding references + AI reasoning trace | `relationType: "INFERRED_HYPOTHESIS", confidence: LOW, reasoningTrace` |

**Question:** Is `confidence` field sufficient, or are explicit `relationType` fields necessary to prevent hallucination?

---

### Decision I — Human/Kiko Workflow Integration

**Where does the agent fit into: `Understand → Discuss → Plan → Implement → Test → Review → Improve`?**

| Aspect | Options |
|--------|---------|
| **Phase** | Before `Implement` — during `Discuss/Plan` (fills current gap) |
| **Invocation** | Explicit (Kiko requests) vs Automatic (on story start) |
| **Refresh** | Re-runnable with same/new scope; contextDigest enables change detection |
| **User Guidance** | Accept `focus`, `priorities`, `outputContext` via `UserGuidance` (already in PromptRequest) |
| **Comparison** | Store previous analysis digests? Diff across runs? |

**Repository Evidence:** Current Kiko workflow has no `Discuss/Plan` tooling. Story 0111 enables story-scoped context. ADR-065: synthesis for architecture-overview-v2 is "current-state answer" — similar purpose.

---

### Decision J — Evaluation

**Minimum evaluation required before V1 success:**

| Check | Type | Reuse ADR-066? |
|-------|------|----------------|
| Schema validity | Deterministic | ✅ Pydantic validation |
| Grounding (deterministic) | Deterministic | ✅ Allow-list subset check |
| Trust safety (no UNVALIDATED/TRANSIENT_AI refs) | Deterministic | ✅ Trust tier check |
| Unsupported claims | Human qualitative | ⚠️ ADR-066 qualitative grounding assessment |
| Usefulness for story prep | Human qualitative | ❌ New dimension |
| Reproducibility (same contextDigest → same structure) | Deterministic | ✅ Context digest stability |
| Context efficiency (output tokens / input tokens) | Deterministic | ⚠️ New metric |

**Question:** Should agent evaluation be a new scenario type in ADR-066 harness, or a separate evaluation?

---

### Decision K — Future Developer OS Runtime Boundary

**Ensure design can be invoked by Workspace, Agent Orchestrator, OpenClaw without coupling DevLog to any runtime.**

| Principle | Current Architecture Support |
|-----------|------------------------------|
| **Contract Stability** | `EngineeringContext`, `PromptRequest`, `AiTaskResultRequest` are versioned contracts |
| **No OpenClaw Coupling** | DevLog doesn't import OpenClaw; external runtime calls DevLog APIs |
| **Workspace Isolation** | DevLog projects independent; Workspace aggregates |
| **Agent Orchestration** | External orchestrator sequences: context → analysis → proposal → validation |
| **MCP as Main Boundary?** | ADR-063 §25: "MCP remains a transport and consumer boundary" — not the architecture |

**Key Decision:** Should MCP be the **primary** long-term agent capability boundary, or just **one adapter** alongside REST?

---

## 5. MCP Design Practices Analysis

### MCP_DESIGN_PRACTICES_TO_REUSE
Based on repository evidence from Story 0111 and MCP architecture:

1. **Explicit Capability Boundaries** — Single MCP tool per capability (`get_engineering_context`, `search_project_history`, resources per entity)
2. **Small Contracts** — `EngineeringContext` record with additive evolution; no breaking changes
3. **Transport Separated from Domain** — MCP tool is thin adapter; domain logic in `EngineeringContextFacade` + `RepositoryContextEngine`
4. **Human Approval Before Implementation** — Story 0111: "Human design review: COMPLETED", "Human implementation authorization: GRANTED"
5. **Incremental Implementation** — Story 0111 built on existing `EngineeringContextFacade`/`RepositoryContextEngine`; no new retrieval infra
6. **Testing Contracts First** — Unit tests for classification, ordering, filtering; integration tests for full flow
7. **Backward Compatibility** — `evidence[]` deprecated but preserved; additive fields only
8. **Deterministic Assembly** — No LLM in context construction; SHA-256 contextDigest for reproducibility

### MCP_DESIGN_PRACTICES_NOT_APPLICABLE
1. **Resource-Oriented for Read-Only** — Agent produces analysis (write-like), not just reading existing resources
2. **Single-Tool Simplicity** — Agent may need multi-step: context → analysis → validation → return
3. **No AI in Transport** — MCP tool currently has no AI; agent introduces AI generation in the path

---

## 6. Premature Implementation Design — Avoided

Per instructions, I do NOT decide:
- Exact endpoint names (beyond pattern)
- Exact Pydantic/Java field names (beyond contract shape)
- Exact prompt wording
- Exact class/file list (kept provisional)

---

## 7. Architecture Alternatives

### Option A — MCP-First Transient Analysis (RECOMMENDATION_FOR_HUMAN_DISCUSSION)

**Flow:**
```
Kiko → MCP tool `analyze_story_context(projectSlug, storyId, intent, files[], userGuidance?)
       ↓
MCP Server → EngineeringContextFacade.getEngineeringContext(projectSlug, intent, files, storyId)
       ↓
EngineeringContext (with sections[], trustTier, scopeEcho)
       ↓
MCP Server → builds PromptRequest(selectedKnowledge=EngineeringContextProjection.toMap())
       ↓
AI Engine /api/v1/ai/tasks (taskType=STORY_CONTEXT_ANALYSIS, intent=engineering-story-context-analysis-v1)
       ↓
StoryContextAnalysisGenerationService → structured output (AnalysisSynthesisResult-like)
       ↓
Callback → MCP returns analysis to Kiko
```

**Ownership:** Backend owns `AnalyzeStoryContext` use case; MCP tool is transport adapter; AI Engine owns generation service.

**Advantages:**
- Reuses Story 0111 `EngineeringContext` fully (canonical context)
- MCP tool pattern consistent with `get_engineering_context`
- Transient output by default (ADR-065 aligned)
- Human-in-the-loop: Kiko explicitly invokes
- ADR-066 evaluation reusable (new scenario type)
- No new persistence, no new retrieval infra

**Disadvantages:**
- MCP server must build `PromptRequest` (currently backend does this for Analysis workflow)
- New Intent in AI Engine Catalog
- New AI Engine service (`StoryContextAnalysisGenerationService`)

**Coupling:** Low — MCP → backend HTTP → AI Engine HTTP; all via contracts.

**Testing:** Unit (classifier, grounding), Integration (MCP → context → AI Engine mock), Evaluation (ADR-066 scenario).

**Future Developer OS:** External runtime calls same MCP tool or REST endpoint; no DevLog dependency on runtime.

**Migration Cost:** Low — extends existing Story 0111 pipeline.

---

### Option B — Backend Application Capability with MCP/REST Adapters

**Flow:**
```
Kiko → REST `POST /api/v1/projects/{slug}/stories/{storyId}/analyze-context` OR MCP tool
       ↓
Backend Controller → AnalyzeStoryContextUseCase
       ↓
Use Case: EngineeringContextFacade → AIEngineClient.submit(PromptRequest) → await callback/poll
       ↓
Returns StoryContextAnalysisResult
```

**Ownership:** Backend owns full use case; MCP/REST are pure transport adapters.

**Advantages:**
- Clean separation: use case in backend, transports thin
- REST + MCP both first-class
- Backend controls polling/callback complexity (currently async)
- Easier to add persistence later (use case owns it)

**Disadvantages:**
- More backend code (use case, controller, async handling)
- MCP tool becomes thinner (just delegator)
- Callback polling adds complexity vs synchronous MCP call

**Coupling:** Medium — backend orchestrates async AI Engine callback.

---

### Option C — External Orchestration (OpenClaw/Developer OS Runtime)

**Flow:**
```
External Runtime → get_engineering_context(projectSlug, storyId, intent, files[])
       ↓
EngineeringContext
       ↓
External Runtime → builds PromptRequest → AI Engine /api/v1/ai/tasks
       ↓
AI Engine → callback to external runtime (or polling)
       ↓
External Runtime → returns analysis to Kiko
```

**Ownership:** DevLog provides primitives (context, AI tasks); external runtime composes.

**Advantages:**
- DevLog stays primitive-focused
- Maximum flexibility for external runtimes
- No "agent" logic in DevLog

**Disadvantages:**
- Kiko (human) cannot use directly without external runtime
- Duplicates composition logic in external runtime
- Loses DevLog's trust/grounding guarantees at composition layer
- ADR-063 §25 violated: "human application must not consume backend capabilities through MCP"

**Coupling:** Low for DevLog, high for external runtime.

---

## 8. Agent Identity in DevLog V1

| Meaning | Fits V1? | Rationale |
|---------|----------|-----------|
| **Agent as Use Case** | ✅ YES | Deterministic orchestration: context acquisition → LLM generation → validation → return. No autonomy. |
| **Agent as Autonomous Runtime** | ❌ NO | Long-lived process with tools, memory, planning, loops — not V1 scope |
| **Agent as External Runtime Personality** | ❌ NO | OpenClaw invoking DevLog — external concern |

**Conclusion for V1:** "Agent" = **a named, versioned use case** that consumes `EngineeringContext`, invokes AI Engine with a specific Intent, validates output, returns structured transient analysis. No memory, no tools, no loops, no autonomy beyond single invocation.

---

## 9. State and Memory

| Memory Type | Exists in DevLog? | Agent Needs Own? |
|-------------|-------------------|------------------|
| Project Memory (trusted knowledge) | ✅ Insights, Decisions, Events, Stories, Relations | ❌ No — via EngineeringContext |
| Repository Memory (commits, diffs, symbols) | ✅ RepositoryContextEngine | ❌ No — via EngineeringContext |
| Task Scope (Engineering Story) | ✅ EngineeringStory entity with base/target commits | ❌ No — via storyId scoping |
| Conversation History | ❌ Not in DevLog | ❌ No — transient invocation |
| Cross-Run Learning | ❌ Not in DevLog | ❌ No — ADR-066 replay is for regression, not learning |

**Likely Outcome:** No dedicated agent memory for V1. Single stateless invocation. If conversation history needed, it's a future Developer OS concern.

---

## 10. Security and Permissions

| Capability | V1 Required? | Representation |
|------------|--------------|----------------|
| `READ_ENGINEERING_CONTEXT` | ✅ Yes | Existing MCP tool / REST endpoint auth |
| `RUN_AI_ANALYSIS` | ✅ Yes | AI Engine task submission (existing) |
| `CREATE_PROPOSAL` | ❌ No (V1 = ANALYSIS_ONLY) | Would require ADR-006 proposal creation |
| `WRITE_TRUSTED_KNOWLEDGE` | ❌ FORBIDDEN | ADR-006 |
| `MODIFY_CODE` | ❌ FORBIDDEN | Not in DevLog scope |
| `AUTHORIZE_IMPLEMENTATION` | ❌ FORBIDDEN | Human decision |
| `EXECUTE_SHELL` | ❌ FORBIDDEN | Not in DevLog scope |
| `MODIFY_REPOSITORY` | ❌ FORBIDDEN | Not in DevLog scope |

**Question:** Should capabilities be explicit in a permission model, or are existing service boundaries (MCP tool access, AI Engine task submission) sufficient for V1?

---

## 11. Failure Semantics

| Failure Scenario | Policy Options | Trade-offs |
|------------------|----------------|------------|
| **EngineeringContext empty** | Return empty analysis with `confidence=LOW`; include `uncertainties` explaining no context | Honest vs useful |
| **Context stale (freshness=STALE)** | Include `freshness` in provenance; add `uncertainty` with `reason=STALE_CONTEXT` | Transparent vs alarming |
| **Invalid storyId** | Return analysis with empty `technical_evidence`; `requestEcho.storyId` echoes input; `uncertainties` explains | Story 0111 AC5: no error, empty section |
| **AI output fails schema validation** | Corrective retry (max 1, like InsightGenerationService) → then error response | ADR-065 corrective retry pattern |
| **Grounding fails (deterministic)** | Reject output; retry; if persists → error with `TRUST_SAFETY=VIOLATION` | ADR-066 trust safety |
| **Low confidence** | Include in output `confidence=LOW`; human decides whether to trust | Transparent |
| **Partial analysis** | Return what's valid; mark incomplete sections; include `uncertainties` | Useful vs complete |

---

## 12. Human Guidance

**Should invocation allow optional human guidance?**

| Guidance Target | Options |
|-----------------|---------|
| **Influence Context Selection** | Modify `files[]`/`storyId` scoping; add `focus` terms to ranking (via `UserGuidance.priorities`) |
| **Influence AI Interpretation Only** | Pass `UserGuidance` to PromptRequest; affects LLM only, not context selection |
| **Echo in Output** | Return `userGuidanceEcho` in analysis for auditability |
| **Persist** | Store with analysis if persisted (Decision E) |

**Repository Evidence:** `PromptRequest` already has `UserGuidance` with `priorities`, `focus`, `outputContext`. `KnowledgeSelectionService` uses `guidanceScore()` for ranking boost. **Preserve deterministic context/trust boundaries** — guidance affects ranking/interpretation, not trust tier assignment or context composition rules.

---

## 13. Developer Trader Future Compatibility

**Future Loop:**
```
Trading Observation
    ↓
Engineering Investigation (Developer OS)
    ↓
Engineering Story (baseCommit from investigation)
    ↓
DevLog Agent: Story Context Analysis
    ↓
Structured Analysis → Human Engineer
    ↓
Implementation → Testing → Review → Completion
    ↓
Trading OS Feedback Loop
```

**Compatibility Requirements:**
- Agent remains **domain-agnostic** — no trading logic
- Input contract (`files[]`, `storyId`, `intent`) already supports trading-originated scoping
- `requestEcho` preserves original query for audit trail
- `contextDigest` enables traceability from trading observation → engineering story → analysis
- Future ADR-063 §11 (repository document retrieval) will make ADRs/architecture docs retrievable — critical for trading-system investigations

**No DevLog changes needed for Trading OS compatibility.**

---

## 14. ADR Discussion Draft

**Next ADR Number:** ADR-067 (after ADR-066)

**Proposed Title:** `DevLog Engineering Story Context Agent — First Agent Capability`

**Status:** Proposed (Draft for Human Discussion)

### ADR Structure (Draft)

```
# ADR-067 — DevLog Engineering Story Context Agent — First Agent Capability

## Status
Proposed

## Context
[Repository truth, accepted baseline, problem statement]

## Problem
[Engineering Stories lack Discuss/Plan tooling; Kiko has no automated context analysis]

## Goals
[Help Kiko understand story context before implementation; prove EngineeringContext value; preserve trust boundaries]

## Non-Goals
[RAG, autonomous coding, persistent agent memory, OpenClaw coupling, trusted-knowledge mutation]

## Existing Architecture
[EngineeringContext, IntentCatalog, AI Engine, ADR-006, ADR-063, ADR-065, ADR-066]

## Decision Questions (for human input)
[A through K as above]

## Alternatives
[Option A: MCP-First Transient Analysis (recommended for discussion)
 Option B: Backend Application Capability
 Option C: External Orchestration]

## Preliminary Recommendation
Option A — MCP-First Transient Analysis

## Trust Boundaries
[ADR-006 compliance, AGENT_CAN_READ/INTERPRET/PROPOSE/PERSIST/PROMOTE matrix]

## Interaction Model
[Invocation path, context acquisition, output contract, failure handling]

## Evaluation Model
[ADR-066 reuse, new scenario type, deterministic + qualitative checks]

## Consequences
[Positive/Negative/Risks]

## Unresolved Questions
[All decisions A-K requiring human input]
```

---

## 15. Human Discussion Packet

### Decision 1: Agent Boundary (Decision A)

**QUESTION:** Where does the agent conceptually live — Backend/Core, AI Engine, MCP Layer, or Orchestration Across?

**WHY_IT_MATTERS:** Determines ownership, testing boundaries, and where changes land when agent evolves.

**OPTION_A:** DevLog Backend/Core owns `AnalyzeStoryContext` use case; AI Engine is a service; MCP/REST are transports.

**OPTION_B:** AI Engine owns agent via new Intent `engineering-story-context-analysis-v1` with dedicated service.

**OPTION_C:** MCP Layer owns agent contract; backend/AI Engine are implementation details.

**REPOSITORY_EVIDENCE:** ADR-063 §22 (backend owns composition), ADR-065 (AI Engine owns synthesis), current pattern: backend orchestrates Analysis workflow.

**PRELIMINARY_RECOMMENDATION:** Option A — Backend owns use case; AI Engine generates; MCP/REST transport.

---

### Decision 2: Invocation Path (Decision B)

**QUESTION:** How should Kiko invoke the agent — MCP tool, REST endpoint, both, or dedicated command?

**WHY_IT_MATTERS:** Defines developer experience, transport coupling, and future orchestration compatibility.

**OPTION_A:** MCP tool `analyze_story_context` (consistent with `get_engineering_context`).

**OPTION_B:** REST endpoint `POST /api/v1/projects/{slug}/stories/{storyId}/analyze-context`.

**OPTION_C:** Both — backend use case with MCP/REST as transport adapters.

**REPOSITORY_EVIDENCE:** Story 0111 MCP tool pattern; ADR-063 §25 (MCP is transport, not architecture).

**PRELIMINARY_RECOMMENDATION:** Option C — Backend use case with both MCP and REST adapters.

---

### Decision 3: Context Acquisition (Decision C)

**QUESTION:** Should agent consume pre-built EngineeringContext, request it internally, or reconstruct SelectedKnowledge?

**WHY_IT_MATTERS:** Preserves single canonical context source; avoids duplication/divergence.

**OPTION_A:** Caller builds EngineeringContext via existing facade → passes to agent.

**OPTION_B:** Agent receives scope hints → calls EngineeringContextFacade internally.

**OPTION_C:** Agent uses KnowledgeSelectionService directly (diverges from EngineeringContext).

**REPOSITORY_EVIDENCE:** Story 0111: EngineeringContext IS the MCP consumer composition (ADR-063 §2/§5). KnowledgeSelectionService builds SelectedKnowledge for Analysis workflow — different consumer, different budgets.

**PRELIMINARY_RECOMMENDATION:** Option A — Consume pre-built EngineeringContext; single canonical context.

---

### Decision 4: V1 Output Contract (Decision D)

**QUESTION:** Minimal, Rich, or Hybrid contract for V1?

**WHY_IT_MATTERS:** Affects prompt stability, evaluation complexity, frontend usability, evolution.

**OPTION_A:** Minimal — core sections only (objectiveUnderstanding, relevantContext, constraints, impactedComponents, uncertainties, questionsBeforeImplementation, provenance).

**OPTION_B:** Rich — full separation (relevantArchitecture, relevantDecisions, relevantEvidence, historicalContext, missingInformation, outputClassification).

**OPTION_C:** Hybrid — minimal core + optional structured sections additively.

**REPOSITORY_EVIDENCE:** ADR-065 synthesis contract has structured sections; ADR-066 evaluation dimensions favor measurable outputs.

**PRELIMINARY_RECOMMENDATION:** Option C — Hybrid: stable minimal core with optional sections for architecture/decisions/evidence.

---

### Decision 5: Output Persistence (Decision E)

**QUESTION:** Transient only, persisted StoryAnalysis entity, or hybrid?

**WHY_IT_MATTERS:** Auditability, stale analysis risk, database complexity, UX.

**OPTION_A:** Transient only — generated per request, returned via MCP/REST.

**OPTION_B:** Persisted StoryAnalysis entity linked to EngineeringStory.

**OPTION_C:** Hybrid — V1 transient with explicit persistence capability designed for later.

**REPOSITORY_EVIDENCE:** ADR-065: synthesis is "execution-scoped, non-trusted snapshot on the existing AiTask entity... scoped to the Analysis execution, not the project; no cross-run reuse without explicit future ADR authorization."

**PRELIMINARY_RECOMMENDATION:** Option A — Transient only for V1 (aligned with ADR-065).

---

### Decision 6: Proposal Support (Decision F)

**QUESTION:** Should V1 be strictly ANALYSIS_ONLY or allow optional ValidatableProposal?

**WHY_IT_MATTERS:** Human review burden, ADR-006 compliance, agent purpose clarity.

**OPTION_A:** ANALYSIS_ONLY — no proposals; pure synthesis for planning.

**OPTION_B:** Optional ValidatableProposal — agent may embed proposals; human reviews if present.

**OPTION_C:** Mandatory Proposals — agent must propose at least one.

**REPOSITORY_EVIDENCE:** ADR-065 separates synthesis (describes existing) from proposals (proposes new). Current Intents: architecture-overview-v2 does synthesis + optional proposals; analyze-engineering-decision-v1 does proposals only.

**PRELIMINARY_RECOMMENDATION:** Option A — ANALYSIS_ONLY for V1. Agent's purpose is context understanding, not knowledge proposal. Surface actionable items as `recommendations` (non-proposal) that human can manually promote via existing Intents.

---

### Decision 7: Grounding Model (Decision G)

**QUESTION:** What does "grounded" mean for each output category?

**WHY_IT_MATTERS:** Prevents hallucination; preserves `known A + known B != A → B`.

**OPTION_A:** Strict — all claims require evidenceReferences; interpretations must reference context; recommendations labeled ungrounded.

**OPTION_B:** Section-aware — relevantContext items require refs; uncertainties/recommendations may be ungrounded but labeled.

**OPTION_C:** Confidence-based — LOW confidence allowed without refs for recommendations.

**REPOSITORY_EVIDENCE:** ADR-065 §6: "Every substantive architecture claim in the synthesis must be grounded." ADR-066: deterministic grounding constraints (allow-lists) + qualitative assessment.

**PRELIMINARY_RECOMMENDATION:** Option B — Section-aware: factual extractions require refs; aiInterpretation requires refs; recommendations allowed ungrounded with explicit `grounded: false` + `confidence: LOW`.

---

### Decision 8: Relationship Semantics (Decision H)

**QUESTION:** Explicit relationType fields or confidence-only for relationships?

**WHY_IT_MATTERS:** Prevents presenting inferred relationships as facts.

**OPTION_A:** Explicit `relationType` enum (EXPLICIT, TEMPORAL_PROXIMITY, POSSIBLE_RELEVANCE, INFERRED_HYPOTHESIS) required.

**OPTION_B:** Confidence field only (HIGH/MEDIUM/LOW) with reasoning trace.

**REPOSITORY_EVIDENCE:** ADR-063 §12: "Relations are expansion metadata." ADR-063 §15: "treating everything visible to a model as automatically citable" rejected. ADR-066: trust safety checks.

**PRELIMINARY_RECOMMENDATION:** Option A — Explicit relationType fields mandatory; confidence secondary. Prevents collapse of semantic categories.

---

## Final Summary

```
TASK = FIRST_DEVLOG_AGENT_ARCHITECTURE_PREPARATION
AGENT_CANDIDATE = DevLog Engineering Story Context Agent
NEXT_ADR_NUMBER = ADR-067
PROPOSED_ADR_TITLE = DevLog Engineering Story Context Agent — First Agent Capability
ARCHITECTURE_DECISIONS_REQUIRING_HUMAN_INPUT = 11 (A through K)
ARCHITECTURE_OPTIONS_COUNT = 3 (Option A/B/C)
RECOMMENDED_OPTION_FOR_DISCUSSION = Option A — MCP-First Transient Analysis
ENGINEERING_CONTEXT_REUSED = YES
NEW_CONTEXT_INFRASTRUCTURE_REQUIRED = NO
DEDICATED_AGENT_MEMORY_REQUIRED_FOR_V1 = NO
OUTPUT_PERSISTENCE = TRANSIENT
VALIDATABLE_PROPOSALS_IN_V1 = NO
PRIMARY_INVOCATION = MCP + REST (transport adapters over backend use case)
RAG_REQUIRED = NO
OPENCLAW_REQUIRED = NO
AUTONOMOUS_CODE_MODIFICATION = NO
HUMAN_AUTHORITY_PRESERVED = YES
ADR_DRAFT_CREATED = YES (this document serves as draft)
IMPLEMENTATION_AUTHORIZED = NO
PRODUCTION_CODE_MODIFIED = NO
COMMITS_CREATED = 0
PUSH_PERFORMED = NO
```

**FIRST_DEVLOG_AGENT_READY_FOR_HUMAN_ARCHITECTURE_DISCUSSION**