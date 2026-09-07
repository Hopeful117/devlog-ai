# DevLog AI — First Useful DevLog Agent Discovery Report

## 1. Repository Truth

**Branch/HEAD:** `main` @ `3550c78` (Merge PR #94: structured engineering context)
**Worktree:** Clean (only untracked `data/` and build artifacts)
**Key ADRs Accepted:** ADR-006 (AI Proposal Workflow), ADR-063 (Context Retrieval/Composition), ADR-064 (Hybrid Analysis Composition), ADR-065 (Synthesis/Proposal Separation), ADR-066 (Replayable Evaluation Harness)
**Engineering Stories:** 21 stories persisted (per ADR-063), workflow: REGISTERED → IN_PROGRESS → COMPLETED with base/target commits
**MCP Server:** Exposes `get_engineering_context` tool + resources (stories, decisions, insights, events, commits, history search)
**AI Engine:** Python FastAPI service with Intent-driven generation (Insight, Engineering Event, Decision), corrective retry, structured output validation
**EngineeringContext Contract:** `EngineeringContext(project, intent, evidence[], metadata, sections[], requestEcho)` with TrustTier classification (TRUSTED/HUMAN_AUTHORED/TECHNICAL_EVIDENCE/SYSTEM_METADATA), ContextSection partitioning, files[]/storyId scoping
**AI Boundary:** `PromptRequest` (with SelectedKnowledge) → AI Engine → `AiTaskResultRequest` (proposals[] + optional synthesis) → Core callback → `ValidatableProposal` lifecycle (PROPOSED → ACCEPTED/REJECTED) → atomic promotion to trusted knowledge

---

## 2. Existing Capability Map

### Repository Understanding
- **Git History:** Full commit/diff import with parent relationships, changed files, diff statistics (Story 0093 freshness checkpoints)
- **Deterministic Extraction:** RepositoryContextEngine with 6 collectors (GitHistory, CommitDiff, ProjectKnowledge, DeterministicKnowledge, CurrentAnalysis, RepositoryStructure), intent-aware ranking, diversity-aware selection, token budget (ADR-038/039)
- **Symbol Enrichment:** Bounded Java declaration extraction (ADR-045) with location metadata
- **Content Enrichment:** Bounded file content with revision traceability (ADR-044)
- **Context Digest:** SHA-256 over deterministic context for reproducibility
- **Historical Reconstruction:** ProjectCommitRepository with BFS traversal for story commit-window filtering (Story 0111)

### Engineering Knowledge
- **Trusted Knowledge:** 18 ACTIVE Insights, 21 Engineering Stories, 1 Decision, 44 KnowledgeRelations, 44,235 Facts, 345 Observations (per ADR-063)
- **ADRs:** Persisted as repository documents (HUMAN_AUTHORED), not yet first-class retrieval candidates (ADR-063 §11)
- **Engineering Events:** Single-commit, first-parent boundary, validated vertical slice (Story 0100)
- **Challenges:** Open challenges tracked with impact/resolution
- **Human Context:** ProjectHumanContextInput (ACTIVE only) with markdown content
- **ValidatableProposals:** ACCEPTED proposals tracked as lineage, excluded from context (UNVALIDATED tier)

### Context Composition (Accepted EngineeringContext Architecture)
- **Trust Tiers:** TRUSTED (promoted Insights/Decisions/Events), HUMAN_AUTHORED (stories, notes, milestones, challenges), TECHNICAL_EVIDENCE (git, facts, observations, structure), SYSTEM_METADATA (analysis, freshness, diagnostics), UNVALIDATED/TRANSIENT_AI excluded
- **Sections:** `trusted_knowledge`, `human_context`, `technical_evidence`, `system_metadata` — ordered by trust tier
- **Temporal Ordering:** Within sections, by `occurredAt` desc, then identifier
- **Files[] Scope:** Filters TECHNICAL_EVIDENCE by originatingFile prefix match
- **StoryId Scope:** Filters TECHNICAL_EVIDENCE to story commit window via BFS commit graph traversal (authoritative history)
- **Request Echo:** Returns normalized `projectSlug, intent, files[], storyId`
- **Compatibility Evidence:** Flattened sections[] in trust-tier order for backward compatibility
- **REST Access:** `GET /api/v1/projects/{slug}/engineering-context?intent=...&files=...&storyId=...`
- **MCP Access:** `get_engineering_context` tool with identical parameters

### AI Boundary (Current)
```
PromptRequest
  ├── correlationId, analysisId, aiTaskId, taskType
  ├── intent (id, version, objective, supportedInsightTypes, outputSchema)
  ├── userGuidance (priorities, focus, outputContext)
  ├── selectedKnowledge (Map<String,Object> from KnowledgeSelectionService)
  │   ├── observations[], facts[], insights[], existingArchitectureKnowledge[]
  │   ├── engineeringEvents[], humanContextInputs[], knowledgeRelations[]
  │   ├── repositoryContext (EngineeringEvidence with content/symbols/resources)
  │   ├── evolutionContext (for event proposals)
  │   └── selectionMetadata (version, features, counts, budget, digest)
  └── expectedOutputContract + metadata

→ AI Engine (InsightGenerationService, etc.)
  ├── PromptBuilder builds structured prompt with SHARED_STRUCTURED_CONTEXT_CONTRACT
  ├── LLM Provider (OpenAI/Mock) → structured output (InsightGenerationOutput)
  ├── Validation: grounding subset checks, delta/target consistency, synthesis requirements
  ├── Corrective Retry: relationship-aware retry with ArchitectureKnowledgeRetryCandidate
  └── Callback: AiTaskResultRequest
       ├── proposals[] (type, payload, confidence, supportingFactIds, supportingObservationIds, evidenceReferences)
       ├── synthesis (title, sections[], deltaConclusion, groundingReferences) — architecture-overview-v2 only
       ├── promptExecution (promptVersion, provider, model, promptContentDigest, contextDigest)
       └── error

→ Core Validation & Promotion (ADR-006)
     ├── ValidatableProposal persisted (PROPOSED)
     ├── Human reviews in UI
     ├── ACCEPTED → atomic transaction: validate + create domain knowledge + mark ACCEPTED
     └── REJECTED → end of lifecycle
```

**AI Currently Allowed:**
- Interpret structured SelectedKnowledge
- Generate Insight/EngineeringEvent/Decision proposals with grounding references
- Produce Analysis Synthesis (architecture-overview-v2 only)
- Corrective retry on validation failure

**AI Currently Forbidden:**
- Directly persist trusted knowledge (ADR-006)
- Modify domain entities
- Decide proposal acceptance
- Bypass human validation
- Invent unsupported relationships (grounding validation enforces subset checks)

---

## 3. Engineering Story Workflow (Actual from Repository Evidence)

### Lifecycle
```
REGISTERED (created with title, storyNumber)
    │
    ▼ startImplementation(baseCommit)
IN_PROGRESS (baseCommit set)
    │
    ▼ complete(targetCommit)
COMPLETED (targetCommit set, completedAt)
```

### Context Preparation for Story
1. **Kiko/Human creates story** via `POST /api/v1/projects/{id}/stories` (CreateEngineeringStoryRequest)
2. **Story started** with `baseCommit` — `POST /api/v1/stories/{id}/start` (StartStoryRequest)
3. **Context consumed** via MCP `get_engineering_context(projectSlug, intent="story-preparation", files=[], storyId=story.id)` or REST equivalent
4. **RepositoryContextAdapter** builds synthetic AnalysisContext, filters by story commit window (BFS on ProjectCommit graph)
5. **EngineeringContext** returned with TECHNICAL_EVIDENCE scoped to story commits
6. **Human discusses** — currently no formal discussion tracking; happens outside DevLog (GitHub issues, Slack, etc.)
7. **Implementation** — developer works in IDE; DevLog passively observes via freshness (ADR-062)
8. **Testing** — no integrated test workflow; manual
9. **Review** — no formal review workflow in DevLog; PR review happens in Git hosting
10. **Completion** — `POST /api/v1/stories/{id}/complete` (CompleteStoryRequest with targetCommit)
11. **Acceptance** — story marked COMPLETED; no automated acceptance gate

### Conceptual vs. Actual Workflow Gap
| Conceptual Phase | Actual Representation |
|-----------------|----------------------|
| Understand | Project bootstrap (`describe-project-v1`), ProjectContextSnapshot |
| Discuss | **Not represented** — no discussion thread, comment, or collaboration entity |
| Plan | Engineering Story created (REGISTERED) with title only |
| Implement | Story IN_PROGRESS with baseCommit; **no planning artifacts** |
| Test | **Not represented** |
| Review | **Not represented** — no review queue, approval, or integration with PR |
| Improve | Story COMPLETED with targetCommit; **no retrospective** |

**Verified Gap:** Only `Understand` (bootstrap) and partial `Plan` (story registration) are represented. Discussion, implementation tracking, testing, review, and improvement are external to DevLog.

---

## 4. Highest-Value First Agent Capability Evaluation

### Candidates Investigated

| Candidate | Immediate Usefulness | Context Availability | Architectural Fit | Autonomy Risk | Deterministic Verification | Usefulness to Kiko | Demonstrates EngineeringContext Value |
|-----------|---------------------|---------------------|-------------------|---------------|---------------------------|-------------------|--------------------------------------|
| **Engineering Story Context Analyst** | HIGH — fills discussion/planning gap | HIGH — EngineeringContext already scoped to storyId/files | HIGH — reuses existing context boundary | LOW — read-only analysis | HIGH — grounding validation, replayable eval | HIGH — Kiko's primary workflow | HIGH — proves context value for story prep |
| Story Preparation Agent | MEDIUM — overlaps with Analyst | HIGH | MEDIUM — would need write access to story fields | MEDIUM — proposes story edits | MEDIUM | MEDIUM | MEDIUM |
| Architecture Impact Analyst | MEDIUM — useful but narrower | HIGH — architecture knowledge in context | HIGH | LOW | HIGH | MEDIUM | HIGH |
| Historical Decision Investigator | LOW — niche use case | MEDIUM — ADRs not yet first-class retrieval | MEDIUM | LOW | MEDIUM | LOW | MEDIUM |
| Implementation Context Assistant | HIGH — but requires code modification | HIGH | MEDIUM — crosses into implementation | HIGH — writes code | LOW | HIGH | MEDIUM |
| Review Context Agent | LOW — no review workflow exists | LOW — no review entities | LOW | LOW | LOW | LOW | LOW |

### Selected: **DevLog Engineering Story Context Agent**

**Why this wins:**
1. **Immediate usefulness:** Fills the largest workflow gap (Discuss/Plan phases) where Kiko currently has no tooling
2. **Maximum context reuse:** Consumes existing `EngineeringContext` with `storyId` scoping — zero new context infrastructure
3. **Perfect architectural fit:** Read-only analysis consuming deterministic context, producing structured output — aligns with ADR-060 (Deterministic Core, Probabilistic Intelligence)
4. **Lowest autonomy risk:** Cannot modify anything; only produces analysis for human consumption
5. **Deterministic verification:** Output grounded in EngineeringContext evidence; replayable evaluation (ADR-066) directly applicable
6. **Kiko's primary workflow:** Engineering Stories are Kiko's unit of work; this agent directly assists the human at the point of highest leverage
7. **Proves EngineeringContext value:** If the agent can produce useful story analysis from scoped context, the entire context architecture is validated

### Why Others Deferred
- **Story Preparation Agent:** Requires write access to story entities (title, description, acceptance criteria) — crosses trust boundary prematurely
- **Architecture Impact Analyst:** Valuable but narrower; should build on Story Context Analyst's foundation
- **Historical Decision Investigator:** Blocked by ADR-063 §11 — ADRs not yet retrievable as first-class evidence
- **Implementation Context Assistant:** Highest autonomy risk; requires code modification capability — violates ADR-006/ADR-060 authority boundaries
- **Review Context Agent:** No review workflow exists in DevLog yet (see Section 3)

---

## 5. Preferred Direction: DevLog Engineering Story Context Agent

**Responsibility:** Given an Engineering Story or engineering question, consume a scoped `EngineeringContext` and produce a structured analysis helping Kiko/human understand:
- Relevant architecture (from TRUSTED insights, HUMAN_AUTHORED ADRs via expansion)
- Relevant historical decisions (Decisions, EngineeringEvents, KnowledgeRelations)
- Related repository evidence (scoped commits, diffs, symbols, file content)
- Constraints (human context inputs, challenges, milestones)
- Previous attempts (validated/pending proposals, previous analyses)
- Potentially impacted components (from repository structure, symbols, commit diffs)
- Uncertainties (missing evidence categories, stale freshness, grounding gaps)
- Missing information (what's not in context but should be investigated)
- Questions to answer before implementation

**Must NOT:**
- Authorize implementation (human decision)
- Modify trusted knowledge directly (ADR-006)
- Modify code (implementation is human/IDE responsibility)
- Invent relationships (only project explicit KnowledgeRelations + deterministic temporal proximity)
- Silently turn AI conclusions into project facts (output is transient analysis or ValidatableProposal)

---

## 6. EngineeringContext Consumption

### How the Agent Consumes Context
**Through existing Java application boundary → MCP → AI Engine orchestration**

**Flow:**
```
Human/Kiko requests story context analysis
       ↓
MCP tool: get_engineering_context(projectSlug, intent="story-analysis", files=[], storyId=storyId)
       ↓
EngineeringContextFacadeImpl → RepositoryContextAdapter → RepositoryContextEngine
       ↓
EngineeringContext (with sections[], evidence[], metadata, requestEcho)
       ↓
AI Engine receives via PromptRequest.selectedKnowledge (via SelectedKnowledgePromptProjectionService)
       ↓
Agent-specific Intent: "engineering-story-context-analysis-v1"
       ↓
Structured output → returned to human via MCP/resource
```

**Why this path:**
- Reuses existing `EngineeringContext` contract (no new context model)
- Reuses `RepositoryContextAdapter.filterByStoryScope` (authoritative commit-window filtering)
- Reuses MCP tool contract (already exposed, tested, documented)
- Reuses AI Engine Intent/validation/callback pipeline
- Reuses replayable evaluation harness (ADR-066) for agent output validation

### Intent Semantics for This Agent
```
intent = "engineering-story-context-analysis"
version = "v1"
objective = "Analyze engineering context for story {storyId} to support planning and discussion"
supportedInsightTypes = []  // No new knowledge proposals
outputSchema = StoryContextAnalysisSchema  // New structured output contract
```

### Scoping Sufficiency
- **files[]**: Sufficient for file-level scoping (already implemented in EngineeringContextContractMapper)
- **storyId**: Sufficient for commit-window scoping (already implemented via RepositoryContextAdapter BFS traversal)
- **Missing Capability:** None genuinely required for V1. The existing `EngineeringContext` with `storyId` + `files[]` + trust-tier sections provides all needed scoping.

**No new context infrastructure required for V1.**

---

## 7. Agent Output Contract (Candidate)

```json
{
  "analysisId": "uuid",
  "storyId": "uuid",
  "projectSlug": "string",
  "intent": "engineering-story-context-analysis-v1",
  "timestamp": "ISO8601",
  "contextDigest": "sha256",
  "objectiveUnderstanding": {
    "summary": "string",
    "keyGoals": ["string"],
    "successCriteria": ["string"],
    "confidence": "HIGH|MEDIUM|LOW"
  },
  "relevantArchitecture": [
    {
      "component": "string",
      "description": "string",
      "evidenceReferences": ["devlog://..."],
      "trustTier": "TRUSTED|HUMAN_AUTHORED",
      "provenance": "INSIGHT|DECISION|ENGINEERING_EVENT|REPOSITORY_DOCUMENT"
    }
  ],
  "relevantDecisions": [
    {
      "decisionId": "uuid",
      "title": "string",
      "choice": "string",
      "rationale": "string",
      "consequences": "string",
      "evidenceReferences": ["devlog://..."],
      "status": "ACTIVE|SUPERSEDED",
      "trustTier": "TRUSTED|HUMAN_AUTHORED"
    }
  ],
  "relevantEvidence": [
    {
      "kind": "COMMIT|CHANGED_FILE|INSIGHT|DECISION|ENGINEERING_EVENT|FACT|OBSERVATION",
      "summary": "string",
      "resource": "devlog://...",
      "trustTier": "TRUSTED|HUMAN_AUTHORED|TECHNICAL_EVIDENCE|SYSTEM_METADATA",
      "relevanceReason": "string"
    }
  ],
  "historicalContext": [
    {
      "event": "string",
      "timestamp": "ISO8601",
      "type": "COMMIT|ANALYSIS|PROPOSAL|ENGINEERING_EVENT|DECISION",
      "resource": "devlog://...",
      "relationToStory": "PREDECESSOR|RELATED|CONTEXTUAL"
    }
  ],
  "constraints": [
    {
      "type": "HUMAN_CONTEXT|CHALLENGE|MILESTONE|TECHNICAL_DEBT|ARCHITECTURAL_RULE",
      "description": "string",
      "source": "string",
      "severity": "BLOCKER|CONSTRAINT|CONSIDERATION",
      "evidenceReferences": ["devlog://..."]
    }
  ],
  "potentiallyImpactedComponents": [
    {
      "component": "string",
      "impactType": "MODIFICATION|EXTENSION|REPLACEMENT|INTEGRATION",
      "evidenceReferences": ["devlog://..."],
      "confidence": "HIGH|MEDIUM|LOW"
    }
  ],
  "uncertainties": [
    {
      "area": "string",
      "description": "string",
      "reason": "MISSING_EVIDENCE|STALE_CONTEXT|CONFLICTING_EVIDENCE|AMBIGUOUS_REQUIREMENTS",
      "suggestedInvestigation": "string"
    }
  ],
  "missingInformation": [
    {
      "category": "ARCHITECTURE|DECISIONS|REPOSITORY_EVIDENCE|HUMAN_CONTEXT|TESTS",
      "description": "string",
      "suggestedAction": "string"
    }
  ],
  "questionsBeforeImplementation": [
    {
      "question": "string",
      "category": "CLARIFICATION|DECISION|INVESTIGATION|DESIGN",
      "priority": "HIGH|MEDIUM|LOW",
      "context": "string"
    }
  ],
  "confidence": "HIGH|MEDIUM|LOW",
  "provenance": {
    "contextDigest": "sha256",
    "evidenceCount": 42,
    "trustedEvidenceCount": 12,
    "technicalEvidenceCount": 25,
    "humanAuthoredCount": 5
  },
  "outputClassification": {
    "factualExtraction": [...],      // Direct evidence from context
    "aiInterpretation": [...],       // AI reasoning over evidence
    "proposal": [...],               // ValidatableProposal candidates (optional)
    "recommendation": [...]          // Non-binding suggestions
  }
}
```

### Category Distinctions (Enforced by Schema)
| Category | Source | Validation | Persistence |
|----------|--------|------------|-------------|
| **factualExtraction** | Direct quotes/references from EngineeringContext evidence | Deterministic subset check against context | Transient (not persisted) |
| **aiInterpretation** | AI synthesis, correlation, explanation | Grounding references must resolve to context | Transient (or ValidatableProposal if promoted) |
| **proposal** | Structured ValidatableProposal payload | ADR-006 validation + human acceptance | Persisted as ValidatableProposal (PROPOSED) |
| **recommendation** | AI suggestions not proposing knowledge | None (advisory only) | Transient |

---

## 8. Trust and Governance

### ADR-006 Application
The agent output **must not** automatically become trusted knowledge. It follows the existing proposal lifecycle:

```
Agent Output (Structured Analysis)
       │
       ├── Transient Analysis (default) → displayed to human, not persisted
       │
       └── Optional: ValidatableProposal(s) embedded in output
               │
               ▼ Human reviews in UI
         PROPOSED
               │
               ├── ACCEPTED → atomic promotion to trusted knowledge (Insight/Decision/Event)
               │
               └── REJECTED → end of lifecycle
```

### Explicit Boundaries

| Boundary | Value | Rationale |
|----------|-------|-----------|
| **AGENT_CAN_READ** | `EngineeringContext` (all trust tiers except UNVALIDATED/TRANSIENT_AI) | Context is read-only deterministic composition |
| **AGENT_CAN_INTERPRET** | Yes — correlation, explanation, gap identification, hypothesis generation | Core AI capability per ADR-060 §3 |
| **AGENT_CAN_PROPOSE** | Yes — via embedded `ValidatableProposal` in output | Follows ADR-006; proposals require human validation |
| **AGENT_CAN_PERSIST_DIRECTLY** | **NO** — never | ADR-006: AI never directly creates trusted knowledge |
| **AGENT_CAN_PROMOTE_TRUSTED_KNOWLEDGE** | **NO** — never | ADR-006: only human validation + Core atomic transaction |
| **HUMAN_VALIDATION_REQUIRED_FOR** | Any `ValidatableProposal` embedded in agent output | ADR-006 invariant; transient analysis exempt |

**Agent output classification:**
- **Default:** Transient analysis (ephemeral, displayed via MCP resource, not persisted)
- **Optional:** Structured analysis + zero or more `ValidatableProposal` objects
- **Never:** Direct trusted knowledge creation

---

## 9. Relationship Safety

### Required Semantic Distinctions (Enforced in Output Contract)

| Relationship Type | Definition | Evidence Required | Contract Field |
|-------------------|------------|-------------------|----------------|
| **Explicit Relation** | `KnowledgeRelation` edge (RESOLVES, CAUSED_BY, RELATES_TO, DERIVED_FROM, ADDRESSES, INFORMED_BY) | Persisted `KnowledgeRelation` entity | `relevantDecisions[].evidenceReferences`, `relevantEvidence[].resource` |
| **Temporal Proximity** | Commits/events in same story window or adjacent time | Commit timestamps, story base/target commits | `historicalContext[].relationToStory: "PREDECESSOR\|CONTEXTUAL"` |
| **Possible Relevance** | Shared symbols, file paths, or architectural keywords | Symbol overlap, file path prefix, IntentTerms match | `potentiallyImpactedComponents[].confidence: "MEDIUM\|LOW"` |
| **Inferred Hypothesis** | AI-generated correlation not explicitly persisted | Grounding references + AI reasoning trace | `aiInterpretation[]` with explicit `confidence: "LOW"` |

### Safeguards Against Hallucinated Relationships
1. **Grounding Validation:** Every claim in `aiInterpretation` must have `groundingReferences` resolving to `EngineeringContext` evidence (subset check, same as InsightGenerationService)
2. **Explicit Relation Flag:** Only `KnowledgeRelation` edges + commit-parent + proposal-lineage marked as `EXPLICIT`; all else `INFERRED`
3. **Confidence Calibration:** `HIGH` only for explicit relations; `MEDIUM` for temporal proximity; `LOW` for inferred hypotheses
4. **No Causal Language Without Evidence:** Output validator rejects "caused by", "led to", "resulted in" unless supported by `CAUSED_BY` KnowledgeRelation or explicit commit-message evidence
5. **Provenance Trail:** Every output section includes `evidenceReferences` array; human can expand via MCP resources to verify

---

## 10. Evaluation Strategy

### Reusable Infrastructure (ADR-066)
- **Harness:** `ai-engine/evaluations/` — replay-first, deterministic, CI-compatible
- **Scenario Contract:** JSON with `PromptRequest` fixture, expected delta/target, grounding allow-lists, qualitative grounding assessment
- **Dimensions:** STRUCTURAL_VALIDITY, PROPOSAL_CORRECTNESS, DELTA_CORRECTNESS, TARGET_CORRECTNESS, GROUNDING_QUALITY, TRUST_SAFETY, OVERALL_QUALITY
- **Gates:** Scenario-owned thresholds (min STRONG runs, max incorrect deltas/targets, max trust violations)

### Evaluation Corpus Design (Based on Existing Engineering Stories)

| Scenario | Story | Intent | Expected Behavior |
|----------|-------|--------|-------------------|
| `story-0002-context-exposure` | #2 | story-analysis | Identifies RepositoryContextEngine, MCP tool, contract mapper; surfaces ADR-038/039/044/045 |
| `story-0012-evidence-precision` | #12 | story-analysis | Finds EvidenceRanker, EvidenceSelector, precision policy; notes budget pressure |
| `story-0037-knowledge-evolution` | #37 | story-analysis | Connects Insight promotion, KnowledgeRelation, temporal assessment |
| `story-0100-canonical-analysis` | #100 | story-analysis | Maps EngineeringEvent vertical slice, first-parent boundary, proposal lineage |
| `story-0111-structured-context` | #111 | story-analysis | Identifies TrustTier sections, storyId scoping, ContextSection rationale |

### Evaluation Dimensions

| Dimension | Deterministic Check | Human Qualitative |
|-----------|---------------------|-------------------|
| Evidence Grounding | All references resolve to context evidence | Claim accuracy vs. source |
| Architecture Relevance | TRUSTED/HUMAN_AUTHORED architecture items present | Completeness of architectural picture |
| Historical Relevance | Story-window commits/events included | Causal chain coherence |
| Unsupported Claims | Zero references outside grounding allow-list | Plausible-but-unproven detection |
| Relationship Hallucination | No CAUSED_BY without KnowledgeRelation | Inferred vs. explicit distinction |
| Missing Constraints | Human context inputs, challenges surfaced | Blocker identification accuracy |
| Story Prep Usefulness | QuestionsBeforeImplementation non-empty | Human rating: "would help me plan" |
| Stability/Reproducibility | Same contextDigest → same output structure | Consistent emphasis across runs |
| Token/Context Efficiency | Output size vs. context size ratio | Information density |

### Separation: Deterministic vs. Human Evaluation
- **Deterministic (CI):** Schema validation, grounding subset checks, trust-tier compliance, relationship-type labeling, contextDigest stability, token bounds
- **Human (Periodic):** Qualitative grounding assessment, usefulness for story preparation, missing constraint detection, question relevance

---

## 11. Developer OS Future Compatibility

### Boundary Definition for Future External Runtime
```
DevLog AI (Current)
├── EngineeringContext (REST/MCP) — stable contract
├── SelectedKnowledge (internal) — versioned projection
├── AiTaskResultRequest (callback) — versioned contract
├── ValidatableProposal lifecycle — governed by Core
└── Replayable Evaluation — scenario JSON + replay artifacts

Future Developer OS Agent Orchestrator
    │
    ├── Invokes DevLog via: REST /api/v1/projects/{slug}/engineering-context
    │                        MCP get_engineering_context tool
    │                        AI Engine /api/v1/ai/tasks (PromptRequest)
    │
    ├── Consumes: EngineeringContext, AiTaskResultRequest, ValidatableProposal
    │
    └── Does NOT require: DevLog internal classes, database schema, AI Engine internals
```

**Key Boundary Principles:**
1. **Contract Stability:** `EngineeringContext`, `PromptRequest`, `AiTaskResultRequest` are versioned contracts — external runtime depends only on these
2. **No OpenClaw Coupling:** DevLog does not import or depend on OpenClaw; OpenClaw (if used) calls DevLog APIs
3. **Workspace Isolation:** DevLog projects are independent; Developer OS Workspace aggregates multiple DevLog projects
4. **Agent Orchestration:** External orchestrator sequences DevLog capabilities (context → analysis → proposal → validation) without DevLog knowing about orchestrator
5. **Kiko Integration:** Kiko (human developer) uses DevLog via UI/MCP; Developer OS Agent uses same APIs programmatically

---

## 12. Developer Trader Compatibility

### Future Engineering Investigation Flow
```
Trading OS Observation
       ↓
Engineering Investigation (Developer OS)
       ↓
DevLog EngineeringContext (scoped to files[]/storyId from trading observation)
       ↓
DevLog Agent: Story Context Analysis
       ↓
Structured Analysis → Human Engineer
       ↓
Engineering Story Created (with baseCommit from investigation)
       ↓
Implementation → Testing → Review → Completion
       ↓
Trading OS Feedback Loop
```

### Compatibility Assessment
- **Natural Support:** The proposed agent consumes `EngineeringContext` scoped by `files[]` and `storyId` — exactly what a trading-originated investigation would provide (affected files, related commits)
- **No Trading Logic in DevLog:** Agent knows nothing about trades, positions, risk; only engineering context
- **Evidence Traceability:** `requestEcho` preserves original query; `contextDigest` enables audit trail from trading observation to engineering story
- **Future Extensibility:** When ADR-063 §11 (repository document retrieval) lands, ADRs/architecture docs become retrievable — critical for trading-system architecture investigations

**No DevLog changes needed for Trading OS compatibility.** The agent's input contract (`files[]`, `storyId`, `intent`) already supports the required scoping.

---

## 13. Gap Analysis

### Required Before First Agent (Genuine Blockers)
1. **New Intent Registration:** `engineering-story-context-analysis-v1` in IntentCatalog with output schema
2. **Output Schema Definition:** `StoryContextAnalysisSchema` (Pydantic + JSON Schema) for AI Engine validation
3. **Prompt Template:** Structured prompt for story context analysis (extends SHARED_STRUCTURED_CONTEXT_CONTRACT)
4. **AI Engine Service:** `StoryContextAnalysisGenerationService` (similar to InsightGenerationService but no proposals required)
5. **Callback Handling:** Core must accept synthesis-only output (no proposals) for this Intent
6. **MCP Exposure:** New resource `devlog://projects/{slug}/story-analysis/{storyId}` or tool `analyze_story_context`
7. **Evaluation Scenario:** One canonical scenario in `ai-engine/evaluations/scenarios/` with reviewed replay

### Useful Soon After First Agent (Non-Blocking)
1. **Human Discussion Thread:** Attach analysis to story as discussion context (new entity)
2. **ADR Retrieval:** ADR-063 §11 implementation — repository documents as HUMAN_AUTHORED evidence
3. **Proposal Embedding:** Allow agent output to include ValidatableProposal for human review
4. **Frontend Integration:** Story detail view shows agent analysis alongside story metadata
5. **Cross-Story Relations:** Agent identifies related stories via KnowledgeRelation/shared commits

### Explicitly Deferred
- RAG / vector database / embeddings
- Autonomous coding / code modification
- OpenClaw coupling / generic multi-agent orchestration
- Agent Presence Window / real-time collaboration
- Automatic trusted-knowledge mutation
- Generic ContextPack (ADR-063 §14)
- Full temporal knowledge graph (ADR-059/060)
- Review workflow entities (no review workflow exists yet)

---

## 14. Proposed First Implementation Slice

### Responsibility
**DevLog Engineering Story Context Agent** — read-only analysis of story-scoped EngineeringContext producing structured transient analysis for human planning/discussion.

### Entry Point
- **MCP Tool:** `analyze_story_context(projectSlug, storyId, intent="story-analysis", files=[])`
- **REST:** `POST /api/v1/projects/{slug}/stories/{storyId}/analyze-context`

### Input Contract
```json
{
  "projectSlug": "string",
  "storyId": "uuid",
  "intent": "engineering-story-context-analysis-v1",
  "files": ["string"]  // optional additional file scoping
}
```

### Context Acquisition
- Reuses `EngineeringContextFacadeImpl.getEngineeringContext()` with `storyId` + `files[]`
- No new context retrieval; existing trust-tier sections, scoping, resource URIs

### AI Invocation
- New `PromptRequest` with `taskType: STORY_CONTEXT_ANALYSIS`
- `selectedKnowledge` from `SelectedKnowledgePromptProjectionService.toMap()` (includes repositoryContext evidence)
- `intent.outputSchema` = `StoryContextAnalysisSchema`
- AI Engine: new `StoryContextAnalysisGenerationService` (no corrective retry needed for V1 — synthesis only)

### Output Contract
As defined in Section 7 (StoryContextAnalysisResult) — transient analysis, no proposals in V1

### Trust Classification
- **Output:** Transient analysis (not persisted, not a ValidatableProposal)
- **Provenance:** `contextDigest`, `promptExecution` metadata for auditability
- **Governance:** ADR-006 not triggered (no proposals); ADR-065 synthesis rules apply if synthesis produced

### Persistence
- **V1:** Non-persistent — returned via MCP tool/resource, displayed in UI
- **Future:** Optional persistence as `StoryAnalysis` entity linked to EngineeringStory

### Validation
- AI Engine: Pydantic validation against `StoryContextAnalysisSchema`
- Grounding: Deterministic subset check against `selectedKnowledge` evidence references
- Trust: No UNVALIDATED/TRANSIENT_AI references allowed

### Error Handling
- Context acquisition failure → MCP error response with `CONTEXT_UNAVAILABLE`
- AI Engine failure → `AiTaskResultRequest` with error → surfaced via MCP
- Validation failure → corrective retry (max 1) → fallback to error response

### Tests
- **Unit:** PromptBuilder, output schema validation, grounding validator
- **Integration:** MCP tool → EngineeringContext → AI Engine → output (mock provider)
- **Replayable Evaluation:** One scenario in `ai-engine/evaluations/scenarios/story-context-analysis-v1/`

### Evaluation
- Reuses ADR-066 harness (replay-first)
- Scenario: `story-0111-structured-context` (known story, rich context)
- Gates: STRONG ≥ 1/1 (replay), zero trust violations, grounding PASSED

### REST/MCP Exposure
- **MCP Tool:** `analyze_story_context` (new tool in EngineeringContextTool or separate)
- **MCP Resource:** `devlog://projects/{slug}/story-analysis/{storyId}` (reads cached/generated analysis)
- **REST:** Optional — MCP is primary agent interface

### Estimated Modified Files
| File | Change |
|------|--------|
| `ai-engine/app/models/intent.py` | Add `STORY_CONTEXT_ANALYSIS` task type |
| `ai-engine/app/schemas/insight.py` | Add `StoryContextAnalysisResult` schema |
| `ai-engine/app/prompts/structured_context.py` | Add story-analysis prompt contract |
| `ai-engine/app/prompts/story_analysis.py` | **New** — prompt template |
| `ai-engine/app/services/story_context_analysis_service.py` | **New** — generation service |
| `ai-engine/app/services/task_processing_service.py` | Route new task type |
| `ai-engine/app/api/ai_tasks.py` | Add task type to supported |
| `backend/.../intent/service/IntentCatalog.java` | Register new Intent |
| `backend/.../ai/engine/dto/PromptRequest.java` | No change (generic Map) |
| `backend/.../knowledge/selection/SelectedKnowledgePromptProjectionService.java` | Ensure repositoryContext projected |
| `mcp-server/.../tool/EngineeringContextTool.java` | Add `analyze_story_context` tool |
| `mcp-server/.../resource/StoryAnalysisResource.java` | **New** — resource for analysis result |
| `devlog-contracts/.../engineeringcontext/StoryContextAnalysisResult.java` | **New** — output contract |

### New Components (Justified)
1. `StoryContextAnalysisResult` contract — required for structured output
2. `StoryContextAnalysisGenerationService` — AI Engine service (single responsibility)
3. `StoryAnalysisResource` — MCP resource for human consumption
4. Evaluation scenario — required by ADR-066

---

## 15. Architecture Decision Needs

### New ADR Required: **YES**

**ADR Title:** *DevLog Engineering Story Context Agent — Transient Analysis Capability*

**Decision Question:** Should DevLog introduce a read-only AI agent capability that consumes scoped EngineeringContext to produce structured transient analysis for Engineering Story planning, without modifying trusted knowledge or requiring new context infrastructure?

**Alternatives:**
1. **Accept (Recommended):** Implement as described — minimal slice, reuses all existing architecture, proves EngineeringContext value
2. **Defer Until RAG:** Wait for vector retrieval (ADR-063 §13) — rejected: blocks value demonstration, over-engineers V1
3. **Embed in Existing Analysis:** Extend `architecture-overview-v2` synthesis — rejected: different intent, different output contract, conflates concerns
4. **External Agent Only:** Build entirely outside DevLog (OpenClaw) — rejected: duplicates context retrieval, loses DevLog trust/grounding guarantees

**Recommended Direction:** Accept — implement as minimal slice per Section 14

**Consequences:**
- **Positive:** First useful agent demonstrating EngineeringContext value; reusable evaluation; no new context infra; respects all trust boundaries
- **Negative:** New Intent, output schema, AI service to maintain; transient output not yet queryable historically
- **Risk:** Output usefulness depends on context quality (mitigated by existing context maturity)

---

## 16. Recommended Engineering Story

**Title:** *DevLog Engineering Story Context Agent — Transient Analysis for Story Planning*

**Objective:** Implement the first useful DevLog Agent that consumes scoped EngineeringContext and produces structured transient analysis to help Kiko/human understand story context before implementation.

**Problem Statement:** Engineering Stories lack a "Discuss/Plan" phase tool. Kiko creates stories but has no automated way to gather relevant architecture, decisions, repository evidence, constraints, and open questions from DevLog's existing knowledge. This agent fills that gap using the now-accepted EngineeringContext architecture.

**In Scope:**
- New Intent `engineering-story-context-analysis-v1` with output schema
- AI Engine service generating structured story context analysis
- MCP tool `analyze_story_context` and resource `devlog://projects/{slug}/story-analysis/{storyId}`
- Reusable evaluation scenario with replay artifact (ADR-066)
- Grounding validation, trust-tier compliance, deterministic verification
- Transient output (no persistence, no ValidatableProposal in V1)

**Out of Scope:**
- Persistence of analysis results
- ValidatableProposal embedding in agent output
- Human discussion thread attachment
- ADR/repository document retrieval (ADR-063 §11)
- Frontend UI integration (MCP resource sufficient for V1)
- Cross-story relation analysis

**Acceptance Criteria:**
1. Given a storyId, `analyze_story_context` returns valid `StoryContextAnalysisResult` within 30s
2. All `evidenceReferences` in output resolve to EngineeringContext evidence (deterministic grounding check)
3. Zero TRUST_SAFETY violations (no UNVALIDATED/TRANSIENT_AI references)
4. Replayable evaluation scenario passes gate (STRONG, zero trust violations)
5. Output includes all contract sections (objectiveUnderstanding, relevantArchitecture, relevantDecisions, relevantEvidence, historicalContext, constraints, potentiallyImpactedComponents, uncertainties, missingInformation, questionsBeforeImplementation, confidence, provenance, outputClassification)
6. `requestEcho` matches input; `contextDigest` matches EngineeringContext digest
7. MCP tool and resource both functional and documented

**Trust Constraints:**
- Agent output is TRANSIENT_ANALYSIS — never becomes trusted knowledge
- No ValidatableProposal generated in V1
- Grounding validation identical to InsightGenerationService subset checks
- Human validation NOT required for transient analysis (only for proposals)

**Testing Strategy:**
- Unit: PromptBuilder, schema validation, grounding validator (deterministic)
- Integration: Mock AI provider → full flow MCP → EngineeringContext → AI Engine → output
- Replayable Evaluation: One canonical scenario (Story 0111 context) with reviewed replay artifact
- CI: Deterministic replay in PR pipeline; live execution manual/scheduled

---

## Final Report Summary

```
RECOMMENDED_FIRST_AGENT = DevLog Engineering Story Context Agent
PRIMARY_USER = Kiko (human developer) via MCP/client
PRIMARY_USE_CASE = Engineering Story planning/discussion — understanding relevant architecture, decisions, evidence, constraints, and open questions before implementation
ENGINEERING_CONTEXT_SUFFICIENT_FOR_V1 = YES
NEW_CONTEXT_INFRASTRUCTURE_REQUIRED = NO
RAG_REQUIRED_FOR_V1 = NO
OPENCLAW_REQUIRED_FOR_V1 = NO
NEW_AI_TRUST_MODEL_REQUIRED = NO
VALIDATABLE_PROPOSAL_REUSED = NO (V1 is transient analysis; proposals optional future enhancement)
REPLAYABLE_EVALUATION_REUSABLE = YES (ADR-066 harness directly applicable)
NEW_ADR_REQUIRED = YES
PROPOSED_ADR_TITLE = DevLog Engineering Story Context Agent — Transient Analysis Capability
PROPOSED_NEXT_STORY = DevLog Engineering Story Context Agent — Transient Analysis for Story Planning
ESTIMATED_IMPLEMENTATION_SURFACE = ~12 files modified, 4 new components (contract, service, prompt, resource, evaluation scenario)
BLOCKERS = None (all context infrastructure exists)
RISKS = Low — read-only, reuses existing boundaries, deterministic verification via ADR-066
FILES_MODIFIED = See Section 14 (estimated 12 files)
COMMITS_CREATED = 0
PUSH_PERFORMED = NO
```

**DEVLOG_FIRST_AGENT_DISCOVERY_COMPLETE**