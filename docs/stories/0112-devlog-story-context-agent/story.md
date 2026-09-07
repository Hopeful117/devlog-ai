# Story 0112 — DevLog Engineering Story Context Agent

## Status

**DESIGN_CORRECTED — READY FOR HUMAN RE-REVIEW**

Design consolidated from ADR-067 accepted decisions D1–D11 and interactive human design decisions D12–D21. Corrections applied to align consolidated documentation with approved decisions.

## Baseline

- Baseline SHA: `de4641e` (ADR-067 acceptance commit)
- Baseline branch: `main`
- ADR-067: **Accepted** (2026-09-05)
- Story 0111: **ACCEPTED** — Structured Engineering Context for Agent Consumption

## Problem

Engineering Stories are created and started, but Kiko (the human developer) lacks automated tooling for the **Discuss/Plan** phases. Kiko has no automated way to gather relevant architecture, decisions, repository evidence, constraints, and open questions from DevLog's existing knowledge before implementation.

This agent fills that gap using the now-accepted `EngineeringContext` architecture.

The agent must:

* consume deterministic, trust-classified `EngineeringContext`;
* produce structured, grounded analysis for human consumption;
* preserve all trust boundaries (ADR-006, ADR-065);
* reuse existing evaluation infrastructure (ADR-066);
* remain compatible with future Developer OS / OpenClaw runtimes without coupling DevLog to them.

---

## Context

The relationship between preceding work and this Story:

```text
ADR-006              -> AI proposal governance, trust boundary
ADR-063 (Accepted)   -> Engineering Context Retrieval and Composition Architecture
                        Human Context Supremacy amendment
                        EngineeringContext contract, trust tiers, scoping
ADR-064 (Accepted)   -> Hybrid Analysis Context Composition Architecture
ADR-065 (Accepted)   -> Analysis Synthesis and Knowledge Proposal Separation
ADR-066 (Accepted)   -> Replayable AI Intent evaluation harness
ADR-067 (Accepted)   -> DevLog Engineering Story Context Agent — First Agent Capability
Story 0111           -> Structured Engineering Context for Agent Consumption
Current architecture -> EngineeringContextFacade, RepositoryContextEngine, ProjectContextProvider,
                        AgentContextProjectionService, AiTask lifecycle, AiProposalContractValidator,
                        ADR-066 evaluation harness all implemented
```

Story 0112 does not reopen ADR-067 decisions D1–D11. It implements the first DevLog Agent capability per the accepted architecture.

---

## Goal

Implement the **DevLog Engineering Story Context Agent** — the first useful DevLog Agent capability.

The agent:

* is explicitly invoked by Kiko during Discuss/Plan;
* consumes a scoped `EngineeringContext` (via `storyId`, optional `files[]`);
* produces a rich, structured, grounded `StoryContextAnalysisResult`;
* persists an immutable, non-trusted `StoryContextAnalysis` snapshot for audit/history;
* reuses existing `EngineeringContext`, `AiTask`, ADR-066 evaluation, MCP/REST transports;
* does NOT create `ValidatableProposal`, modify trusted knowledge, or authorize implementation.

---

## Architecture Constraints

### Authority (ADR-067 D1, D3)

* **Java/Core owns**: capability semantics, authorization, `EngineeringContext` construction, trust boundaries, deterministic invariants, contractual acceptance, governance, exposure through transports.
* **Python AI Engine owns**: agent execution, probabilistic reasoning, prompt orchestration, structured generation, uncertainty-aware interpretation.
* **MCP and REST** are thin transport adapters over the same Java capability.
* `EngineeringContext` is **exclusively** constructed and authorized by Java/Core.

### Trust (ADR-006, ADR-063, ADR-067)

* `UNVALIDATED` (pending `ValidatableProposal`) and `TRANSIENT_AI` are **excluded** from context.
* Only `TRUSTED`, `HUMAN_AUTHORED`, `TECHNICAL_EVIDENCE`, `SYSTEM_METADATA` appear in context.
* `HUMAN_AUTHORED` ≠ authoritative (ADR-063 §179-180).
* Agent output is **non-trusted** by construction; `ValidatableProposal` is **forbidden in V1**.

### Deterministic Core (ADR-060, ADR-063, ADR-067)

* No LLM in context assembly or trust assignment.
* Java/Core constructs authoritative grounding contract from authorized `EngineeringContext`.
* Python receives explicit immutable grounding contract for defensive validation only.
* Java/Core remains sole authoritative acceptor.

### No New Infrastructure (ADR-067 Non-Goals)

```text
RAG / EMBEDDINGS / VECTOR SEARCH = NO
OPENCLAW / GENERIC AGENT RUNTIME = NO
AUTONOMOUS CODING / REPOSITORY MODIFICATION = NO
PERSISTENT CONVERSATIONAL MEMORY = NO
GENERIC AGENT FRAMEWORK = NO
KNOWLEDGE FRESHNESS AGENTS = NO
```

---

## Approved Design Decisions (D12–D21)

### D12 — Story Context Analysis Contract Shape

**Status**: `STORY_0112_D12_CONTRACT_SHAPE = APPROVED_AND_FROZEN`

The `StoryContextAnalysisResult` contract is **strongly typed** with explicit semantic areas:

```text
StoryContextAnalysisResult
├── ObjectiveUnderstanding
├── List<ArchitectureFinding>
├── List<DecisionFinding>
├── List<EvidenceFinding>
├── List<HistoricalContextItem>
├── List<ConstraintFinding>
├── List<ImpactedComponentFinding>
├── List<Uncertainty>
├── List<MissingInformation>
├── List<ImplementationQuestion>
├── Confidence
├── Provenance
└── OutputClassification
```

**Rules**:

* Semantic areas are explicit contract fields (not a generic `findings[]`).
* Shared grounding/classification metadata composed through common `GroundingMetadata`.
* Empty sections are valid (`[]`); fabrication to populate is forbidden.
* No arbitrary `extensions: Map` in V1.
* Finding content has one canonical representation.
* `outputClassification` references/classifies findings — MUST NOT duplicate substantive content.

**Markers**:

```
CONTRACT_MODEL = STRONGLY_TYPED
SEMANTIC_AREAS → EXPLICIT_CONTRACT_FIELDS
COMMON_GROUNDING_METADATA → SHARED_SEMANTICS
ARBITRARY_EXTENSION_MAP = NO
EMPTY_SECTION = VALID
FABRICATION_TO_FILL_SECTION = FORBIDDEN
FINDING_CONTENT → SINGLE_CANONICAL_REPRESENTATION
OUTPUT_CLASSIFICATION → REFERENCES_OR_CLASSIFIES_FINDINGS
```

---

### D13 — Evidence Reference Model

**Status**: `STORY_0112_D13_EVIDENCE_REFERENCE_MODEL = APPROVED`

Grounded findings reference evidence using a **typed dual reference**:

```text
EvidenceRef
├── reference (REQUIRED)
└── resource (OPTIONAL)
```

**Rules**:

* `reference` — canonical internal evidence identity; grounding validation key.
* `resource` — optional navigation metadata only (MCP URI when available).
* `resource` is **not** evidence identity, trust authority, or grounding requirement.
* MCP resources are NOT required for grounding; MCP does NOT own evidence identity.
* Trust remains exclusively in Java/Core `EngineeringContext`.
* AI `EvidenceRef` values never independently assert trust.
* Persisted references preserve `{ reference, resource? }`.
* `EvidenceRef` contains NO `trustTier` field. Trust is derived exclusively from the authoritative Java/Core `EngineeringContext` using the canonical `reference`.

**Markers**:

```
EVIDENCE_REF_MODEL = DUAL_TYPED_REFERENCE
CANONICAL_EVIDENCE_IDENTITY = reference
GROUNDING_VALIDATION_KEY = reference
RESOURCE → NAVIGATION_ONLY → OPTIONAL → NOT_IDENTITY → NOT_TRUST_AUTHORITY
MCP_RESOURCE_REQUIRED_FOR_GROUNDING = NO
MCP_OWNS_EVIDENCE_IDENTITY = NO
TRUST_AUTHORITY = JAVA_CORE_ENGINEERING_CONTEXT
AI_EVIDENCE_REF_CAN_ASSERT_TRUST = NO
```

**Typed EvidenceRef (D12-aligned)**:

```java
// Java record
public record EvidenceRef(
    String reference,          // Canonical identity — grounding validation
    String resource            // Optional MCP URI — navigation hint
) { }

// Python Pydantic
class EvidenceRef(BaseModel):
    reference: str
    resource: str | None = None
```

---

### D14 — Grounding Validation Responsibility

**Status**: `STORY_0112_D14_GROUNDING_RESPONSIBILITY = APPROVED`

**Java/Core is the sole grounding authority.**

* Java constructs authoritative grounding universe from authorized `EngineeringContext`.
* Java provides Python with explicit immutable-per-execution grounding contract.
* Python MAY use contract for defensive validation, generation-quality checking, corrective retry.
* Python SHALL NOT reconstruct grounding authority, expand allowed references, or redefine trust.
* Java SHALL independently and authoritatively revalidate before acceptance.
* `EvidenceRef.reference` is grounding identity; `EvidenceRef.resource` does not participate.
* Duplicated authority policy forbidden; defensive duplicate checks allowed for corrective retry.

**Markers**:

```
GROUNDING_AUTHORITY = JAVA_CORE
GROUNDING_UNIVERSE → CONSTRUCTED_BY_JAVA → FROM_AUTHORIZED_ENGINEERING_CONTEXT
GROUNDING_CONTRACT → JAVA_TO_PYTHON → EXPLICIT → IMMUTABLE_FOR_EXECUTION
PYTHON_VALID ≠ AUTHORITATIVE_ACCEPTANCE
DUPLICATED_AUTHORITY_POLICY = FORBIDDEN
DEFENSIVE_DUPLICATE_CHECKS = ALLOWED
```

---

### D15 — Snapshot Persistence

**Status**: `STORY_0112_D15_SNAPSHOT_PERSISTENCE = APPROVED`

Introduce a dedicated durable `StoryContextAnalysis` domain artifact:

* Durable, immutable, non-trusted.
* Associated with an Engineering Story for audit/history.
* Produced by an `AiTask` execution.
* `AiTask` remains the technical AI execution record (provenance, timing, attempts, correlation ID).
* `StoryContextAnalysis` remains the canonical durable domain analysis artifact.
* New invocation → fresh `EngineeringContext` → new `StoryContextAnalysis`.
* Previous analyses never overwritten/mutated.
* Persistence does NOT mean promotion or cross-run memory.
* `AiTask` does NOT mirror the full analysis result. It retains technical execution data required by existing infrastructure. The `StoryContextAnalysis` entity owns the canonical durable result. Linkage between them is explicit via `aiTaskId` foreign key.

**Markers**:

```
PERSISTENCE_MODEL → DEDICATED_STORY_CONTEXT_ANALYSIS
STORY_CONTEXT_ANALYSIS → DOMAIN_ARTIFACT → DURABLE → IMMUTABLE → NON_TRUSTED
AI_TASK → TECHNICAL_EXECUTION_RECORD
PREVIOUS_ANALYSIS_MUTATION = FORBIDDEN
ANALYSIS_OVERWRITE = FORBIDDEN
PERSISTED_ANALYSIS ≠ TRUSTED_KNOWLEDGE
AI_TASK_DOES_NOT_MIRROR_FULL_RESULT = YES
LINKAGE_VIA_AI_TASK_ID = EXPLICIT
```

---

### D16 — Execution Success / Durability Boundary

**Status**: `STORY_0112_D16_SUCCESS_DURABILITY = APPROVED`

**Fail-closed**. Successful completion requires:

1. Python generation
2. Authoritative Java validation
3. Durable `StoryContextAnalysis` persistence + commit
4. Successful response delivery

A validated but non-persisted result is **not successful**. Caller must not receive a result if persistence fails.

External Python/LLM execution must NOT occur inside a long-lived DB transaction.

`AiTask` terminal success SHALL NOT precede durable `StoryContextAnalysis` persistence.

No partial-success response in V1.

**Markers**:

```
SUCCESS_BOUNDARY → AUTHORITATIVE_JAVA_VALIDATION → STORY_CONTEXT_ANALYSIS_PERSISTED → COMMIT → SUCCESS
FAIL_CLOSED = YES
VALIDATED_BUT_NOT_PERSISTED → NOT_SUCCESSFUL
RESULT_DELIVERY_BEFORE_DURABILITY → FORBIDDEN
LONG_DB_TRANSACTION_AROUND_PYTHON_OR_LLM → FORBIDDEN
PARTIAL_SUCCESS_RESPONSE → NOT_V1
```

---

### D17 — Invocation Identity and Idempotency

**Status**: `STORY_0112_D17_INVOCATION_IDENTITY = APPROVED`

Each explicit human invocation = new logical analysis:

```
new explicit invocation
    → new AiTask
    → new StoryContextAnalysis on success
```

* `AiTask` correlation ID = technical execution identity.
* `contextDigest` = provenance only (not identity/deduplication key).
* Client idempotency keys NOT required in V1.
* Same `AiTask` callback retry → max one `StoryContextAnalysis`.
* `AiTask.attemptCount` = technical retry metadata, not analysis version.
* New explicit human invocation allowed even with identical context/guidance.

**Markers**:

```
EXPLICIT_HUMAN_INVOCATION → NEW_LOGICAL_ANALYSIS
EXECUTION_IDENTITY → JAVA_GENERATED_AI_TASK_CORRELATION_ID
CONTEXT_DIGEST → PROVENANCE_ONLY
CLIENT_IDEMPOTENCY_KEY → NOT_REQUIRED_V1
SAME_AI_TASK_CALLBACK_RETRY → MAX_ONE_STORY_CONTEXT_ANALYSIS
AI_TASK_ATTEMPT_COUNT → TECHNICAL_RETRY_METADATA
```

---

### D18 — Human Guidance Contract

**Status**: `STORY_0112_D18_HUMAN_GUIDANCE = APPROVED`

Guidance is optional, non-authoritative execution input.

**May**: focus analysis, prioritize authorized context areas, request perspective.
**Must not**: expand `EngineeringContext`, introduce evidence, define trust, weaken grounding, alter relationships, force conclusions, authorize implementation/code.

Guidance may be retained as execution provenance for auditability.

**Markers**:

```
GUIDANCE → OPTIONAL → HUMAN_OR_KIKO_PROVIDED → NON_AUTHORITATIVE
GUIDANCE_PROVENANCE → MAY_BE_PERSISTED_FOR_AUDIT
GUIDANCE_AS_TRUSTED_KNOWLEDGE → FORBIDDEN
```

---

### D19 — AI Intent and Pipeline Integration

**Status**: `STORY_0112_D19_PIPELINE_INTEGRATION = APPROVED`

Reuse existing `AiTask` execution pipeline.

* **New Intent**: `engineering-story-context-analysis-v1` (exact naming per repo convention).
* `AiTask` remains generic technical execution abstraction.
* Java/Core: owns use case, constructs fresh `EngineeringContext`, constructs grounding contract, creates `AiTask`.
* Python: executes intent, owns generation, defensive validation, corrective retry.
* Intent dispatch = thin routing.
* **No** generic agent runtime, session, memory, tool loop, capability registry, OpenClaw coupling.

**Markers**:

```
AI_TASK → REUSE_EXISTING_EXECUTION_PIPELINE
NEW_AI_INTENT → STORY_CONTEXT_ANALYSIS
INTENT_DISPATCH → THIN_ROUTING
GENERIC_AGENT_FRAMEWORK → NOT_STORY_0112
AGENT_SESSION → NOT_V1
AGENT_MEMORY → NOT_V1
TOOL_LOOP → NOT_V1
```

---

### D20 — Invocation Surface

**Status**: `STORY_0112_D20_INVOCATION_SURFACE = APPROVED`

Expose capability through **REST and MCP**, both delegating to same Java/Core use case.

**Caller input**:

* Required: Engineering Story identity (`storyId` + `projectSlug`)
* Optional: human guidance
* Optional: `files[]` — Core-governed scoping filter for `TECHNICAL_EVIDENCE` only (cannot expand authorized context)

**Caller must NOT provide/override**:

* `EngineeringContext`, grounding contract, allowed references, trust classifications
* system prompt, model config, Core-owned governance input
* **AI intent** — Java/Core internally maps the public capability to the `engineering-story-context-analysis-v1` intent

Java/Core constructs governed execution input. `AiTask` and its intent remain internal execution abstractions.

REST and MCP preserve equivalent semantics, governance, success criteria (D16).

**Markers**:

```
PUBLIC_CAPABILITY → DOMAIN_ORIENTED_STORY_CONTEXT_ANALYSIS
CALLER_INPUT → STORY_ID_REQUIRED → GUIDANCE_OPTIONAL → FILES_SCOPE_FILTER_OPTIONAL
JAVA_CORE → CONSTRUCTS_GOVERNED_EXECUTION_INPUT → CHOOSES_INTENT_INTERNALLY
REST_AND_MCP → SAME_JAVA_USE_CASE
AI_TASK → INTERNAL_EXECUTION_ABSTRACTION
MCP → NOT_EVIDENCE_IDENTITY_AUTHORITY
FILES_SCOPE_FILTER → CORE_GOVERNED → TECHNICAL_EVIDENCE_ONLY → CANNOT_EXPAND_CONTEXT
```

---

### D21 — Acceptance and Evaluation

**Status**: `STORY_0112_D21_ACCEPTANCE_EVALUATION = APPROVED`

**Dual acceptance gate**:

1. **Deterministic gates** (reuse ADR-066 harness):
   * schema validity, grounding validity, trust safety, relationship-semantic safety
   * context integrity/digest consistency, forbidden output/capability violations
   * Python corrective retry, authoritative Java rejection, persistence/durability failure
   * duplicate callback idempotency

2. **Human qualitative evaluation** (at least one real Engineering Story):
   * correctness, groundedness, relevance, usefulness
   * uncertainty honesty, clarity, noise control
   * quality of architecture/history reconstruction

Deterministic pass alone is **insufficient**. Structured human verdict required. Passing does not expand autonomy.

**Markers**:

```
ACCEPTANCE → DETERMINISTIC_GATE → HUMAN_USEFULNESS_GATE
DETERMINISTIC_PASS_ONLY → INSUFFICIENT
HUMAN_GATE → AT_LEAST_ONE_REAL_ENGINEERING_STORY
HUMAN_USEFULNESS → DOES_NOT_CREATE_TRUSTED_KNOWLEDGE
AUTONOMOUS_TRIGGERING → STILL_FORBIDDEN_V1
```

---

## Cross-Decision Invariants

| Invariant | Enforcement |
|-----------|-------------|
| **Authority** | Java/Core owns context, trust, grounding universe, validation, acceptance. Python owns generation, pre-validation, corrective retry. |
| **Trust** | Durable ≠ grounded ≠ trusted. `StoryContextAnalysis` is durable + grounded but non-trusted. Persistence ≠ trust. |
| **Relationship safety** | Four relation types: EXPLICIT, TEMPORAL_PROXIMITY, POSSIBLE_RELEVANCE, INFERRED_HYPOTHESIS. Confidence never promotes category. |
| **Human authority** | Agent analyzes/interprets/recommends/asks. Never authorizes implementation/code, mutates trusted knowledge, converts recommendations to facts. |
| **Runtime independence** | No OpenClaw/Developer OS dependency. MCP/REST are adapters. Capabilities first, protocols second. |

---

## Scope — Explicit Non-Goals (V1)

* RAG, vector database, embeddings
* OpenClaw coupling, generic multi-agent runtime
* Autonomous coding, code modification, repository modification
* Persistent conversational agent memory
* Automatic trusted-knowledge mutation
* Generic ContextPack
* Agent Presence Window / real-time collaboration
* Review workflow entities
* Knowledge freshness maintenance / passive monitoring
* Generic agent framework / autonomous triggers
* `ValidatableProposal` creation
* Autonomous Story creation

---

## Functional Requirements

### FR-01: Analyze Story Context Use Case

**Java Core** capability: `AnalyzeStoryContextUseCase`

* Input: `projectSlug`, `storyId`, optional `files[]` (Core-governed TECHNICAL_EVIDENCE scope filter), optional `guidance`
* Constructs fresh `EngineeringContext` via `EngineeringContextFacade` (scoped by `storyId`, `files[]`)
* Constructs authoritative grounding contract from authorized `EngineeringContext`
* Creates `AiTask` with intent `engineering-story-context-analysis-v1` (intent chosen internally by Java/Core)
* Submits to AI Engine, awaits callback
* Authoritative validation (grounding, schema, relationship semantics, trust safety)
* On success: persists `StoryContextAnalysis` + `AiTask` in same transaction
* Returns `StoryContextAnalysisResult`

### FR-02: Story Context Analysis Generation Service

**Python AI Engine** service: `StoryContextAnalysisGenerationService`

* New Intent: `engineering-story-context-analysis-v1`
* Prompt template with ADR-067 D7/D8 grounding/relationship instructions
* Receives `PromptRequest` with `selectedKnowledge` = `EngineeringContext` projection
* Generates `StoryContextAnalysisResult` (no proposals)
* Defensive validation: schema, grounding subset checks, classification rules
* Corrective retry (max 1) on validation failure
* Callback to Java Core with `AiTaskResultRequest`

### FR-03: StoryContextAnalysisResult Contract

**Strongly typed** (D12) with explicit semantic areas:

```text
StoryContextAnalysisResult
├── ObjectiveUnderstanding
├── List<ArchitectureFinding>
├── List<DecisionFinding>
├── List<EvidenceFinding>
├── List<HistoricalContextItem>
├── List<ConstraintFinding>
├── List<ImpactedComponentFinding>
├── List<Uncertainty>
├── List<MissingInformation>
├── List<ImplementationQuestion>
├── Confidence
├── Provenance
└── OutputClassification
```

Each finding includes `EvidenceRef` (D13): `{ reference, resource? }`. No `trustTier` — trust is derived exclusively from Java/Core `EngineeringContext` using the canonical `reference`.

### FR-04: Grounding Contract & Validation

* **Java**: constructs immutable grounding contract from `EngineeringContext` (allowed references, fact IDs, observation IDs, trust tiers, relationship edges).
* **Python**: receives contract, uses for defensive validation + corrective retry.
* **Java**: authoritative revalidation on callback (grounding, schema, relationships, trust safety).
* **No** duplicated authority; Python defensive, Java authoritative.

### FR-05: StoryContextAnalysis Persistence

**New entity**: `StoryContextAnalysis`

* Durable, immutable, non-trusted domain artifact
* Linked to `EngineeringStory` + `AiTask`
* Fields: `analysisSnapshot` (JSONB), `contextDigest`, `promptExecutionMetadata`, `createdAt`
* Persisted in same transaction as `AiTask` completion (D16)
* Queryable by `storyId` for audit/history

### FR-06: REST Endpoint

```
POST /api/v1/projects/{projectSlug}/stories/{storyId}/analyze-context
Content-Type: application/json

Request:
{
  "files": ["src/main/java/..."],
  "guidance": { "focus": "SECURITY", "priorities": ["auth"], "questions": ["..."] }
}

Response (200): StoryContextAnalysisResult
```

Java/Core internally maps this request to the `engineering-story-context-analysis-v1` intent. The caller does not provide the intent.

### FR-07: MCP Tool

```
analyze_story_context(
  projectSlug: string (required),
  storyId: uuid (required),
  files: string[] (optional) — Core-governed TECHNICAL_EVIDENCE scope filter,
  guidance: HumanGuidance (optional)
) → StoryContextAnalysisResult
```

Delegates to same Java use case as REST. The `files[]` parameter is a Core-governed scoping filter for `TECHNICAL_EVIDENCE` only; it cannot expand the authorized grounding universe.

### FR-08: Evaluation

* **Deterministic**: ADR-066 scenario `engineering-story-context-analysis-v1` with gates for schema, grounding, trust, relationships, context integrity, forbidden outputs.
* **Human**: At least one real Engineering Story; assess correctness, groundedness, relevance, usefulness, uncertainty honesty, clarity.

---

## Trust & Governance Requirements

| Requirement | Enforcement |
|-------------|-------------|
| No `ValidatableProposal` in V1 | Intent `outputProposalType` = none; validator rejects proposals |
| Grounding mandatory for facts/interpretations | Java + Python validators reject ungrounded claims |
| Recommendations explicitly classified | `OutputClassification` separates factual/AI/recommendation |
| Recommendations may be ungrounded but labeled | `grounded: boolean` + `classification: RECOMMENDATION` |
| Relationship semantics explicit | `relationType` ∈ {EXPLICIT, TEMPORAL_PROXIMITY, POSSIBLE_RELEVANCE, INFERRED_HYPOTHESIS} |
| No trust escalation | `EvidenceRef` contains no trust field; Java/Core `EngineeringContext` sole authority |
| No autonomous triggering | Explicit human invocation only (D9, D17) |
| No trusted knowledge mutation | `StoryContextAnalysis` non-trusted; no promotion path in V1 |

---

## Persistence Semantics

### AiTask (Technical Execution Record)

* Existing `AiTask` entity — no new fields required for Story 0112
* Retains technical execution provenance: correlation ID, timing, attempts, status, context digest, prompt metadata
* Linked to `StoryContextAnalysis` via `StoryContextAnalysis.aiTaskId` foreign key

### StoryContextAnalysis (Domain Analysis Artifact)

* New JPA entity `StoryContextAnalysis`
* Fields: `id`, `storyId` (FK to EngineeringStory), `aiTaskId` (FK to AiTask), `analysisSnapshot` (JSONB), `contextDigest`, `promptExecutionMetadata`, `createdAt`
* Immutable after creation; linked to `EngineeringStory` for audit trail
* Created in same transaction as `AiTask` completion (D16)
* Queryable by `storyId` for audit/history
* **Canonical durable domain artifact** — `AiTask` does not mirror the full result

---

## Invocation & Failure Semantics

| Scenario | Response |
|----------|----------|
| Empty `EngineeringContext` | `confidence=LOW`, `uncertainties` explaining no context |
| Stale context (`freshness=STALE`) | Include freshness in provenance; `uncertainty.reason=STALE_CONTEXT` |
| Invalid/non-resolving `storyId` | Deterministic Core behavior: `EngineeringContext` contains only non-TECHNICAL_EVIDENCE sections (per `RepositoryContextAdapter.filterToNonTechnical`); `requestEcho.storyId` echoed; `uncertainties` explains no technical evidence found for story |
| AI output validation failure | Corrective retry (max 1) → error response |
| Grounding validation failure | Reject → retry → `TRUST_SAFETY=VIOLATION` |
| Relationship semantic violation | Reject → retry → error |
| Trust safety violation | Reject immediately; no silent trust crossing |
| Low confidence | `confidence=LOW`; human decides |
| Partial output | Valid sections returned; incomplete marked; `uncertainties` included |
| AI Engine timeout/unavailable | Transport error; no trust relaxation |
| Snapshot persistence failure | **Fail-closed**: error response; no result delivered (D16) |

---

## Evaluation Requirements

### Deterministic Gates (ADR-066 Extension)

New scenario: `engineering-story-context-analysis-v1`

* Schema validity
* Grounding validity (all `reference` in allow-list)
* Trust safety (no UNVALIDATED/TRANSIENT_AI references)
* Relationship semantics (`relationType` ∈ approved enum; no confidence promotion)
* Context integrity (`contextDigest` matches EngineeringContext)
* Forbidden outputs (no proposals, no trusted knowledge claims)
* Context digest consistency (same context → same contract/schema structure; probabilistic content remains non-deterministic)

### Human Qualitative Evaluation

At least **one real Engineering Story**:

* Correctness of architecture/decisions/history
* Groundedness (claims match evidence)
* Relevance to Discuss/Plan preparation
* Usefulness (would human use this?)
* Uncertainty honesty (gaps acknowledged)
* Clarity, noise control, quality of reconstruction

**Deterministic pass alone = insufficient** for Story 0112 acceptance.

---

## Implementation Considerations (Deferred Decisions)

The following remain implementation-level choices (per ADR-067 §484):

* Exact Java class names (`AnalyzeStoryContextUseCase`, validator, persistence)
* Exact Python service/class names (`StoryContextAnalysisGenerationService`)
* Exact MCP tool schema / REST endpoint paths
* Exact Pydantic/Java DTO field names for `StoryContextAnalysisResult`
* Database schema for `StoryContextAnalysis` persistence
* Prompt template text for `engineering-story-context-analysis-v1`
* LLM model selection
* Exact retry implementation details
* Specific grounding validation algorithm alignment (Python/Java)
* Frontend/UI integration

---

## Repository Consistency Review

| Check | Result |
|-------|--------|
| D12–D21 consistent with ADR-067 D1–D11 | ✅ Verified |
| Compatible with `AiTask` lifecycle | ✅ Reuses `AiTask` + callback |
| Grounding contract integrable without Python authority | ✅ Java constructs, Python defensive |
| D13 compatible with `EngineeringContext` evidence refs | ✅ `reference` is canonical key; `EvidenceRef` has no `trustTier` |
| D15/D16 compatible with callback/transaction model | ✅ Same transaction as `AiTask` completion; `StoryContextAnalysis` is canonical artifact |
| D17 guarantees ≤1 analysis per successful `AiTask` | ✅ `AiTask` lifecycle + callback deduplication |
| D18 guidance cannot bypass context/grounding authority | ✅ Guidance only affects ranking/interpretation |
| REST + MCP converge on one Java use case | ✅ `AnalyzeStoryContextUseCase` |
| ADR-066 harness extensible for new scenario | ✅ New scenario type `engineering-story-context-analysis-v1` |
| No generic agent framework introduced | ✅ One-shot use case, no sessions/memory |
| Caller cannot provide AI intent | ✅ Intent chosen internally by Java/Core |
| `files[]` is Core-governed scope filter | ✅ Filters TECHNICAL_EVIDENCE only; cannot expand context |

---

## Blockers

**None identified.** All D12–D21 decisions are approved and consistent with ADR-067 and repository reality. Previous design drift in consolidated documentation has been corrected.

---

## Implementation Readiness

```
STORY_0112_DESIGN_CORRECTION = READY_FOR_HUMAN_REVIEW
```

## Documentation Changes

**Created**: `docs/stories/0112-devlog-story-context-agent/story.md` — This consolidated design document.

No production code modified. No Story 0112 implementation started. No database migrations created. No MCP tools/REST endpoints/AI intents created.

---

## Next Human Gate

The design is consolidated and ready for **human review and implementation authorization**.

If approved, the next step is **Story 0112 implementation authorization** (separate task).

**NOT authorized**: production implementation of the agent, MCP tool, REST endpoint, persistence schema, or any Story 0112 code.

---

**STORY_0112_DESIGN_CORRECTION = READY_FOR_HUMAN_REVIEW**