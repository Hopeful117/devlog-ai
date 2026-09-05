# Story 0111 — Structured Engineering Context for Agent Consumption

## Status

**ACCEPTED**

Implemented, reviewed, scope-corrected, acceptance-checked, and human-accepted.

## Baseline

- Baseline SHA: `fb79c0f44e5d2520b78886ac001ceaa6548d1a7f`
- Baseline branch: `main`

## Problem

The current `EngineeringContext` contract (exposed via `GET /api/v1/projects/{slug}/engineering-context` and MCP tool `get_engineering_context`) provides a flat list of `EngineeringEvidence` items with `kind`, `layer`, `sourceType`, `occurredAt`, and provenance metadata.

While the underlying context engine (`RepositoryContextEngine`, `ProjectContextProvider`, `ProjectContextSnapshot`) assembles rich, trust-classified, temporally-ordered, relationship-aware project information, the external contract does not surface this structure. Specifically:

1. **No explicit trust classification** — evidence mixes `TRUSTED` (validated Insights/Decisions/EngineeringEvents), `HUMAN_AUTHORED` (project notes), `TECHNICAL_EVIDENCE` (commits, diffs, facts), and `SYSTEM_METADATA` without distinction. ADR-063 §4 defines these trust tiers but they are not in the contract.

2. **No deterministic temporal ordering guarantee** — `occurredAt` exists on each evidence item but the list order is not guaranteed to be chronological, and no stable tie-breaker is specified.

3. **No targeted scope hints** — the request accepts only `projectSlug` and free-text `intent`. Kiko cannot narrow context to specific files, an Engineering Story, or other scope dimensions even when the underlying data supports it.

4. **No structured context sections** — the flat `evidence[]` array forces consumers to re-classify items by `kind`/`layer`/`sourceType` to answer questions like "what trusted decisions govern this task?" or "what repository evidence supports this?"

These gaps prevent Kiko (the first reference consumer per ADR-055 §6) from efficiently preparing Engineering Stories, and prevent the future DevLog Agent from consuming context through a clean, structured boundary.

## Context

The relationship between preceding work and this Story:

```text
ADR-006              -> AI proposal governance, trust boundary
ADR-055 (Proposed)   -> Engineering Context Engine, ContextRequest -> ContextPackage
ADR-063 (Accepted)   -> Retrieval/Composition/Projection/Grounding/Expansion separation
                       trust tiers (TRUSTED, HUMAN_AUTHORED, TECHNICAL_EVIDENCE, SYSTEM_METADATA, UNVALIDATED, TRANSIENT_AI)
                       consumer-owned composition, shared retrieval primitives
                       explicitly defers universal ContextPack (§14, §35 amendment)
ADR-064 (Accepted)   -> Hybrid Analysis Context Composition (Analysis-specific, not general)
ADR-065 (Accepted)   -> Analysis Synthesis vs ValidatableProposal separation
Story 0110           -> Replayable AI Intent evaluation harness
Current architecture -> EngineeringContextFacade, RepositoryContextEngine, ProjectContextProvider, AgentContextProjectionService all implemented
```

Story 0111 does not reopen ADR-063's decision to defer ContextPack. It evolves the **existing** `EngineeringContext` contract — the MCP/REST consumer composition per ADR-063 §2/§5 — to surface the structure already present in the shared retrieval/composition pipeline.

## Goal

Extend the `EngineeringContext` contract and its assembly pipeline to provide:

1. **Explicit trust classification** per evidence item using ADR-063 §4 trust tier vocabulary
2. **Deterministic temporal ordering** of evidence within sections (occurredAt DESC + stable tie-breaker)
3. **Targeted scope hints** in the request (`files[]`, `storyId`)
4. **Structured context sections** in the response (replacing/enhancing the flat `evidence[]`)

while preserving:
- Full backward compatibility for existing MCP/REST consumers
- ADR-006 trust boundary (no unvalidated proposals in context)
- ADR-063 consumer-owned composition boundary
- Deterministic assembly (no new AI calls)
- Existing context engine reuse (no new retrieval infrastructure)

## Architecture Constraints

### Trust boundary (ADR-006, ADR-063 §4, §29, §792-793)

- `UNVALIDATED` (pending ValidatableProposals) and `TRANSIENT_AI` (raw LLM output) are **excluded** from context candidates entirely
- `AnalysisSynthesis` (ADR-065) is `TRANSIENT_AI` — never in retrieval
- Only `TRUSTED`, `HUMAN_AUTHORED`, `TECHNICAL_EVIDENCE`, `SYSTEM_METADATA` appear in context
- `HUMAN_AUTHORED` ≠ authoritative (ADR-063 §179-180)

### Composition ownership (ADR-063 §2, §5)

- Shared retrieval primitives: `ProjectContextProvider`, `RepositoryContextEngine` (candidates with metadata)
- Consumer-owned composition: `EngineeringContextFacade` + `EngineeringContextContractMapper` (this Story's scope)
- `EngineeringContext` is the MCP consumer's composition output

### Deterministic core (ADR-060, ADR-063 §2)

- No LLM in context assembly
- Trust tier assignment: deterministic mapping from entity type + source metadata
- Temporal ordering: occurredAt DESC + reference string tie-breaker
- Scope filtering: deterministic predicate on existing fields

### No new persistence / retrieval / AI

```text
NEW DATABASE TABLES = NONE
NEW MIGRATIONS = NONE
NEW AI ENGINE CALL = NO
RAG / EMBEDDINGS / VECTOR SEARCH = NO
```

### Backward compatibility

- Existing `evidence[]` field remains in `EngineeringContext` (deprecated but present)
- Existing `intent` parameter unchanged
- Existing MCP tool `get_engineering_context` continues working
- Existing REST endpoint unchanged

## Selected Design Direction

**Extend `EngineeringContext` contract** (Option A from discovery analysis).

Rationale:
- ADR-063 §14/§35 explicitly defers/rejects universal ContextPack
- ADR-063 §2/§5: `EngineeringContext` IS the MCP consumer's composition output
- Minimal duplication: single contract serves MCP, REST, future DevLog Agent
- Backward compatible: additive fields, no breaking changes

### Contract Changes

**Request** (extend `GET /api/v1/projects/{slug}/engineering-context` and MCP tool):

```java
// Existing
projectSlug (path, required)
intent       (query, required)

// New (optional)
files        (query, repeatable)  -- file paths to narrow evidence
storyId      (query, optional)    -- Engineering Story UUID
```

**Response** (`EngineeringContext` record, additive fields):

```java
// Existing fields preserved
ProjectContext project
String intent
List<EngineeringEvidence> evidence          // DEPRECATED: kept for compatibility
EngineeringContextMetadata metadata

// New fields
List<ContextSection> sections               // Structured sections (primary)
ContextRequestEcho requestEcho              // Echo of scope hints for debugging
```

`ContextRequestEcho` is placed at the `EngineeringContext` record level (sibling to `metadata`), not inside `metadata`. It represents the normalized/effective request scope for debugging and audit:

```text
projectSlug, intent, files (effective paths after normalization), storyId (resolved UUID or null)
```

It does not leak internal implementation state and does not duplicate semantic information inconsistently with `metadata`.

**New supporting types** (in `devlog-contracts`):

```java
// Trust tier vocabulary from ADR-063 §4
enum TrustTier {
    TRUSTED,           // human-promoted: Insight, Decision, EngineeringEvent from accepted proposal
    HUMAN_AUTHORED,    // human context inputs, repository documents
    TECHNICAL_EVIDENCE,// commits, diffs, Facts, Observations, structure, profiles
    SYSTEM_METADATA    // freshness, diagnostics, selection metadata
}

// Context section
record ContextSection(
    String name,                    // "trusted_knowledge" | "human_context" | "technical_evidence" | "system_metadata"
    TrustTier trustTier,
    List<EngineeringEvidence> evidence,
    String rationale                // human-readable section purpose
)

// Request echo for debugging
record ContextRequestEcho(
    String projectSlug,
    String intent,
    List<String> files,
    UUID storyId
)

// Enhanced EngineeringEvidence with explicit trust tier
// (additive field to existing record)
record EngineeringEvidence(
    // ... existing 18 fields ...
    TrustTier trustTier             // NEW: explicit trust classification
)
```

**Java record compatibility note:** Adding a record component (`trustTier`) changes the canonical constructor signature. Internal construction sites in `EngineeringContextContractMapper.mapEvidence` must be updated mechanically. The external JSON/API/MCP contract remains additive and compatible — new field is optional in deserialization (Jackson ignores unknown properties by default) and present in serialization. No builder pattern is introduced unless the existing repository already uses it for `EngineeringEvidence` construction (current code uses positional record construction).

### Assembly Pipeline Changes

**`EngineeringContextFacadeImpl` / `EngineeringContextContractMapper`**:

1. Receive scope hints (`files[]`, `storyId`) alongside `projectSlug`, `intent`
2. Build `ProjectContextSnapshot` via `ProjectContextProvider` (unchanged)
3. Build `RepositoryContext` via `RepositoryContextAdapter` with scope-aware filtering:
   - `files[]` → filter `RepositoryEvidence` by `provenance.originatingFile`
   - `storyId` → apply commit-window filtering per storyId rules above
4. Classify each evidence item to `TrustTier` using deterministic rules
5. Partition evidence into four sections by trust tier
6. Sort each section by temporal ordering rules (non-null occurredAt DESC, then reference ASC; null-occurredAt last)
7. Build `EngineeringContext` with both `sections[]` and deprecated `evidence[]` (flattened sections)

**Canonical evidence invariant:**

```text
one canonical evidence selection (from RepositoryContext + ProjectContextSnapshot)
        ↓
trust classification (deterministic)
        ↓
deterministic temporal ordering
        ↓
sections[] (primary structured output)
        ↓
compatibility evidence[] derived by flattening sections[] in trust-tier order
```

`sections[]` and deprecated `evidence[]` are never assembled independently. The `evidence[]` compatibility array is a deterministic projection of the same selected evidence that populates `sections[]`.

### Trust Tier Classification Rules (Deterministic)

The classifier covers every evidence `kind` produced by the current collector set. For each `(kind, sourceType)` pair, exactly one `TrustTier` or explicit exclusion is assigned.

```text
evidence.sourceType == "CORE_KNOWLEDGE" && evidence.kind in {INSIGHT, DECISION, ENGINEERING_EVENT}
    -> TRUSTED
    (validated knowledge from accepted ValidatableProposal)

evidence.kind in {PROJECT_NOTE}
    -> HUMAN_AUTHORED
    (human-authored project context inputs)

evidence.kind in {MILESTONE, ARTIFACT, ENGINEERING_STORY, CHALLENGE}
    -> HUMAN_AUTHORED
    (repository documents / registry entries authored or validated by humans; ADR-063 §11, §173-180:
     "repository documents (ADRs, stories markdown, roadmap) become first-class retrieval candidates as HUMAN_AUTHORED items...
      HUMAN_AUTHORED describes provenance/origin only. It must not be read as currently authoritative...")

evidence.kind in {ANALYSIS}
    -> SYSTEM_METADATA
    (current analysis execution metadata)

evidence.sourceType in {"GIT", "DETERMINISTIC_EXTRACTION", "CORE_ANALYSIS"}
    -> TECHNICAL_EVIDENCE
    (repository-derived: commits, diffs, facts, observations, structure)

evidence.kind in {"FRESHNESS", "DIAGNOSTIC", "SELECTION_METADATA"}
    -> SYSTEM_METADATA
    (system metadata)

evidence.kind == "VALIDATABLE_PROPOSAL" || evidence.sourceType == "AI_ENGINE"
    -> EXCLUDED (UNVALIDATED/TRANSIENT_AI per ADR-063 §4)
```

No implicit fallback to `TRUSTED`. Ambiguous or unsupported kinds are excluded and logged as warnings.

### Temporal Ordering Rules

```text
Within each section:
  primary:   non-null occurredAt DESC (most recent first)
  secondary: reference ASC (string comparison, deterministic, unique)
  null ordering: evidence with non-null occurredAt sorts before evidence with null occurredAt;
                 among null-occurredAt items, reference ASC determines order
```

`Instant.now()` is never synthesized as a semantic timestamp for evidence that lacks an occurrence time. Evidence using `Instant.now()` during collection (e.g., `MODULE_SUMMARY`, `SOURCE_DIRECTORIES`, current `ANALYSIS`) retains that collection timestamp as its `occurredAt`; this is a collection-time fact, not a historical occurrence time.

### Scope Hint Filtering Rules

```text
files[]:
  For each evidence item: if originatingFile matches any path in files[] (prefix or exact), retain
  If files[] provided and no evidence matches, section is empty (not omitted)

storyId:
  **Invariant:** Providing storyId must never broaden technical_evidence relative to the same request without storyId. If the requested Story scope cannot be resolved deterministically, the story-scoped technical_evidence is empty rather than silently broadening to project-wide evidence.

  Resolve EngineeringStorySnapshot from ProjectContextSnapshot.engineeringStories
  If storyId not found:
    -> technical_evidence = [] for story-scoped technical evidence (no error; scope explicitly unresolvable)

  If story found:
    baseCommit = story.baseCommit
    targetCommit = story.targetCommit

    If baseCommit absent AND targetCommit absent:
      -> technical_evidence = [] (no deterministic commit boundary; Story exists but scope undefined)

    If baseCommit present AND targetCommit absent (story in progress):
      -> If RepositoryContext assembly already provides a deterministic repository snapshot boundary (ingested revision from freshness checkpoint or current understanding baseline):
            filter RepositoryEvidence (GIT_HISTORY, COMMIT_DIFF) to commits in [baseCommit, snapshotRevision]
            filter SOURCE_FILE/TEST_FILE to files changed in those commits
         else:
            -> technical_evidence = [] (no deterministic upper boundary available; do not use implicit moving source)

    If baseCommit absent AND targetCommit present:
      -> technical_evidence = [] (Story implementation start unknown; no deterministic window)
      (Do not assume all commits <= targetCommit; Story may not encompass the entire repository history up to targetCommit.)

    If baseCommit present AND targetCommit present:
      -> If targetCommit reachable from baseCommit (ancestor check via repository):
            filter to commits in window (baseCommit, targetCommit] — exclusive of baseCommit, inclusive of targetCommit
            (baseCommit represents the repository state before implementation began; implementation changes are commits after base through target.)
         else:
            -> technical_evidence = [] (unresolvable window; no silent broadening)

  Empty commit window (no commits in range):
    -> technical_evidence section present with evidence: [] (not omitted)
```

No commit proximity heuristics. Relationship inclusion respects `known A + known B does not prove A → B`. Only explicit `KnowledgeRelation` evidence establishes relationships. The AC5 "trusted_knowledge section includes Insights/Decisions/Events related to the story" is satisfied via explicit `KnowledgeRelation` edges from `ProjectContextSnapshot.knowledgeRelations` where `sourceEntityType`/`targetEntityType` references the story entity.

## Acceptance Criteria

### AC1 — Trust Classification Present

Given an `EngineeringContext` response, when inspecting `sections[]`, then every section has a `trustTier` value from `{TRUSTED, HUMAN_AUTHORED, TECHNICAL_EVIDENCE, SYSTEM_METADATA}` and every `EngineeringEvidence` item within has the same `trustTier` as its section.

### AC2 — Trust Tier Assignment Correct

Given context assembled for a project with known trusted Insights, Decisions, EngineeringEvents, ProjectHumanContextInputs, and repository evidence, when the response is inspected, then:
- Insights/Decisions/EngineeringEvents from accepted proposals → `TRUSTED`
- ProjectHumanContextInputs → `HUMAN_AUTHORED`
- Commits, diffs, Facts, Observations, source files, symbols → `TECHNICAL_EVIDENCE`
- Freshness/diagnostics → `SYSTEM_METADATA`
- No ValidatableProposal (PROPOSED) or AI synthesis appears in any section

### AC3 — Temporal Ordering Guaranteed

Given a `ContextSection` with multiple evidence items, when the section is inspected, then:
- Evidence with non-null `occurredAt` appears before evidence with null `occurredAt`
- Among non-null items: ordered by `occurredAt DESC`; identical `occurredAt` ordered by `reference ASC`
- Among null items: ordered by `reference ASC`

### AC4 — Scope Hint: Files

Given a request with `files=["src/main/java/.../RepositoryContextEngine.java"]`, when the response is inspected, then:
- `technical_evidence` section contains only evidence with `originatingFile` matching the path
- Other sections unaffected (trusted/human context not filtered by file)
- `requestEcho.files` echoes the provided paths

### AC5 — Scope Hint: StoryId

Given a request with `storyId=<valid-story-uuid>`, when the response is inspected, then:
- `technical_evidence` section contains only repository evidence per the storyId filtering rules:
  - story not found: technical_evidence = [] (scope explicitly unresolvable)
  - both commits absent: technical_evidence = [] (no deterministic boundary)
  - baseCommit present, targetCommit absent: commits in [baseCommit, snapshotRevision] if deterministic snapshot available; else technical_evidence = []
  - baseCommit absent, targetCommit present: technical_evidence = [] (implementation start unknown)
  - both present, reachable: commits in (baseCommit, targetCommit] (exclusive base, inclusive target)
  - both present, unreachable: technical_evidence = [] (no silent broadening)
  - empty commit window: technical_evidence = [] (not omitted)
- `trusted_knowledge` section includes Insights/Decisions/Events related to the story via explicit `KnowledgeRelation` edges (no commit proximity heuristic)
- `requestEcho.storyId` echoes the provided UUID

### AC6 — Structured Sections Present

Given an `EngineeringContext` response, when `sections[]` is inspected, then:
- All four sections present: `trusted_knowledge`, `human_context`, `technical_evidence`, `system_metadata`
- Empty sections included with `evidence: []` (not omitted)
- Each section has non-empty `rationale`
- Total evidence across sections equals `evidence[]` length (backward compatibility)

### AC7 — ADR-006 Trust Boundary Preserved

Given a project with pending ValidatableProposals and completed AI syntheses, when context is assembled, then no `UNVALIDATED` or `TRANSIENT_AI` evidence appears in any section.

### AC8 — Relationship Non-Inference

Given context assembly, when `knowledgeRelations` exist in `ProjectContextSnapshot`, then they are available for expansion via MCP resources (canonical references) but no inferred relationships (commit→ADR, ADR→Story, etc.) are manufactured in the context.

### AC9 — Backward Compatibility

Given existing MCP tool `get_engineering_context` and REST endpoint consumers, when the new version is deployed, then:
- Existing clients without `files[]`/`storyId` receive valid responses
- `evidence[]` field present and contains all evidence (flattened sections)
- `metadata` field unchanged in structure

### AC10 — MCP Tool Compatibility

Given the MCP tool `get_engineering_context`, when invoked with new optional parameters, then:
- Tool accepts `files` (array) and `storyId` (string) parameters
- Tool returns enhanced `EngineeringContext` JSON with `sections[]`
- Tool works without parameters (backward compatible)

### AC11 — Deterministic Assembly

Given identical project state and identical request (including scope hints), when context is assembled twice, then:
- `sections[]` content identical (same evidence, same order)
- `contextDigest` identical
- No non-deterministic behavior (no UUID generation, no random ordering)

### AC12 — Empty Context Behavior

Given a project with no trusted knowledge, no human inputs, and no repository evidence matching scope, when context is assembled, then:
- All four sections present with `evidence: []`
- `sections[]` not omitted
- `evidence: []` (backward compatible)
- No errors

## Explicit Non-Goals

- DevLog Agent implementation
- RAG, embeddings, vector database, semantic retrieval
- OpenClaw or agent runtime integration
- Autonomous Story generation
- New trusted-knowledge mutation path
- Full temporal reconstruction (before/after, supersession chains)
- New relationship inference (commit→ADR, ADR→Story, etc.)
- Multi-project context
- Universal ContextPack (per ADR-063 §14/§35)
- Repository document body retrieval (ADR-063 §28 deferred)
- Human authorization model (ADR-063 §20 deferred)
- Frontend rendering changes
- New AI Engine integration
- New database tables or migrations

## Planned Scope

### Production (devlog-contracts)

- `TrustTier` enum
- `ContextSection` record
- `ContextRequestEcho` record
- Enhanced `EngineeringEvidence` with `trustTier` field

### Production (backend)

- `EngineeringContextFacade` / `Impl`: accept scope hints, pass to mapper
- `EngineeringContextContractMapper`: trust tier classification, section partitioning, temporal ordering, defensive scope guard
- `EngineeringContextController`: bind new query parameters
- `RepositoryContextAdapter`: **authoritative** commit-window filtering for `storyId` (BFS graph traversal on persisted commit-parent relationships; deterministic snapshot revision from persisted freshness state)

### Production (MCP)

- `EngineeringContextTool`: accept `files[]`, `storyId` parameters
- `DevlogProjectContextClient`: updated interface

### Tests

**Unit (deterministic):**
- `TrustTierClassifierTest`: classification rules for all evidence kinds/sources
- `TemporalOrderingTest`: occurredAt DESC + reference ASC tie-breaker
- `ScopeHintFilterTest`: files[] and storyId narrowing
- `SectionPartitioningTest`: evidence partitioned into exactly four sections
- `EmptySectionTest`: empty sections included, not omitted
- `BackwardCompatibilityTest`: evidence[] = flattened sections

**Integration:**
- `EngineeringContextControllerTest`: full request/response with scope hints
- `EngineeringContextFacadeTest`: end-to-end assembly with real providers (testcontainers)
- `McpToolTest`: tool invocation with parameters

**Architectural:**
- `TrustBoundaryTest`: no UNVALIDATED/TRANSIENT_AI in output
- `RelationshipNonInferenceTest`: no manufactured relationships

## Dependencies

- ADR-006 (trust boundary) — already accepted
- ADR-063 (retrieval/composition architecture) — already accepted
- ADR-064 (hybrid composition) — accepted, Analysis-specific, not directly used
- ADR-065 (synthesis/proposal separation) — accepted, synthesis excluded via trust tier
- Existing: `ProjectContextProvider`, `RepositoryContextEngine`, `RepositoryContextAdapter`, `EngineeringContextFacade`, `AgentContextProjectionService`, `KnowledgeSelectionService`

## Lifecycle State

- Story materialization: completed
- Repository analysis: completed
- Implementation plan: completed
- Human design review: COMPLETED
- Human implementation authorization: GRANTED
- Implementation: COMPLETE
- Final review: COMPLETE
- Story scope correction: COMPLETE (authoritative commit-window filtering moved to RepositoryContextAdapter)
- Final acceptance check: COMPLETE
- Human acceptance: GRANTED
- Commit: COMPLETE
- Push: COMPLETE

Terminal state:

`STORY_0111_ACCEPTED`

## ADR References

- `docs/decisions/ADR-006.md` — AI Proposal and Knowledge Promotion Workflow
- `docs/decisions/ADR-055.md` — Engineering Context Enrichment and Projection (Proposed)
- `docs/decisions/ADR-063.md` — Engineering Context Retrieval and Composition Architecture (Accepted)
- `docs/decisions/ADR-064.md` — Hybrid Analysis Context Composition Architecture (Accepted)
- `docs/decisions/ADR-065.md` — Analysis Synthesis and Knowledge Proposal Separation (Accepted)

## Design Clarifications Resolved

The following open questions from initial definition were resolved during Human Design Review:

1. **HUMAN_AUTHORED scope**: Repository documents (MILESTONE, ARTIFACT, ENGINEERING_STORY, CHALLENGE) classified as HUMAN_AUTHORED per ADR-063 §11, §173-180. They describe provenance/origin only and are not automatically authoritative.

2. **storyId trusted_knowledge filtering**: Only explicit `KnowledgeRelation` edges establish relationships. No commit proximity heuristic. AC5 updated accordingly.

3. **requestEcho placement**: Confirmed at `EngineeringContext` record level (sibling to `metadata`). Contains normalized effective scope: `projectSlug`, `intent`, `files`, `storyId`.

4. **Temporal null semantics**: Non-null occurredAt before null; within each group, DESC/ASC ordering as specified.

5. **storyId edge cases**: All cases defined with strict no-broadening invariant:
   - story not found → technical_evidence = []
   - both commits absent → technical_evidence = []
   - baseCommit only + no deterministic snapshot → technical_evidence = []
   - baseCommit only + deterministic snapshot → (baseCommit, snapshotRevision]
   - targetCommit only → technical_evidence = []
   - reachable window → (baseCommit, targetCommit]
   - unreachable window → technical_evidence = []
   - empty window → technical_evidence = []
   No silent broadening to project-wide evidence in any failure mode.
   Authoritative filtering in `RepositoryContextAdapter.filterByStoryScope` using BFS graph traversal.
   Mapper (`EngineeringContextContractMapper.applyStoryIdFilter`) retains only defensive non-broadening guard.

6. **Deterministic snapshot provenance**: Base-only upper bounds come exclusively from persisted `ProjectFreshnessSummary.checkedSources()` (`baseline.analyzedRevision()` or `source.ingestedRevision()`). No implicit `HEAD` read, no live repository state, no fallback broadening.

6. **EngineeringEvidence compatibility**: Java record canonical constructor requires mechanical update at internal construction sites. External JSON/API contract remains additive and compatible.