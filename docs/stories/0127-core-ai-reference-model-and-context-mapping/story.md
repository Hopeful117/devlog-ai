# Story 0127: Core AI Reference Model and Context Mapping

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Baseline

- Branch: `main`
- Baseline HEAD: `c4facb6`
- Governing ADR: ADR-068 (Accepted in the current worktree)
- Architectural authority file: `docs/decisions/ADR-068.md`
- ADR-068 commit status: committed at the baseline HEAD
- Implementation authorization: **AUTHORIZED FOR STORY IMPLEMENTATION REVIEW**

The existing generated `devlog-contracts/target/*` changes and untracked
`docs/discoveries/` content are pre-existing and are not part of this Story.

## Goal

Establish the first Core-side implementation foundation of ADR-068 without
changing current provider behavior.

```text
SelectedKnowledge
        |
        +--------------------------+
        |                          |
        v                          v
Core AI Reference Registry    Existing provider projection
        |                          |
        v                          v
AiTask execution metadata      Python / provider
```

The Story introduces:

- Core AI reference value semantics;
- explicit namespace/type;
- explicit identity scope;
- deterministic reference mapping;
- an execution-scoped mapping snapshot;
- mapping contract version and digest.

The new identity contract is **not activated at the provider boundary** in this
Story.

## Current Production Boundary

### Standard Analysis path

`backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java`

```text
AnalysisContext
  -> KnowledgeSelectionService.select(...)
  -> SelectedKnowledge
  -> AiTaskService.attachSelectedKnowledge(...)
  -> SelectedKnowledgePromptProjectionService.toMap(...)
  -> PromptRequest
  -> AI Engine
```

### Story Context Analysis path

`backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`

```text
SelectedKnowledge
  -> SelectedKnowledgePromptProjectionService.toMap(...)
  -> selectedKnowledgeSnapshot
  -> AiTaskService.createForStoryContextAnalysisEntity(...)
  -> PromptRequest
```

### Existing persistence boundary

`AiTask` already stores `selectedKnowledgeSnapshot` and `contextSnapshot` as
JSONB. The selected-knowledge snapshot is provider input. The new mapping must
not be embedded in that snapshot before the provider contract migration.

The preferred first-slice persistence direction is one internal,
execution-scoped mapping snapshot on `AiTask`. The exact field name and schema
representation remain implementation decisions, but a new mapping table is not
required by this Story.

## Scope

### In Scope

- Core AI reference value model;
- namespace/type model;
- scope model;
- deterministic reference assignment;
- Core-owned reference binding and mapping;
- registry creation from already-authorized `SelectedKnowledge`;
- coverage of all currently relevant projected entity families;
- reuse of bindings for semantic-section entities;
- reuse of bindings for relationship endpoints;
- grounding-capability metadata for a future provider migration;
- mapping contract version;
- deterministic mapping digest;
- execution-scoped persistence on `AiTask`;
- standard Analysis preparation path;
- Story Context Analysis preparation path;
- deterministic Core tests;
- persistence and reload tests;
- regression proving the provider-facing selected knowledge remains unchanged;
- compatibility for legacy tasks without a mapping snapshot.

### Explicit Non-Goals

- No Python changes.
- No provider prompt changes.
- No `PromptRequest` contract migration.
- No Pydantic output-schema migration.
- No migration of `supportingFactIds` or `supportingObservationIds`.
- No migration of `evidenceReferences`.
- No callback reference resolution.
- No changes to `AiProposalContractValidator` behavior.
- No changes to `AiTaskResultServiceImpl` domain lookup behavior.
- No retry wording or retry-policy redesign.
- No Retrieval, vector search, embeddings or ContextPack.
- No MCP or REST contract changes.
- No proposal persistence changes.
- No Story 0128 implementation.
- No Story 0126 trace schema changes.
- No provider-specific behavior changes.
- No selection, ranking, budget or semantic-classification changes.
- No new trusted domain entity.

## Design

### 1. AI reference semantics

The Core MUST introduce an immutable value representation conceptually
equivalent to:

```text
AiReference
    type
    ref
    scope
```

The exact Java record/class names remain implementation decisions.

The following distinction is mandatory:

```text
AiReference != DomainIdentity
```

An AI reference is a projection identity. It must not become an authoritative
persistence identity or trusted project knowledge.

### 2. Reference namespaces

The reference type represents an explicit namespace. The registry must cover the
entity families actually projected by the current Core paths:

```text
FACT
OBSERVATION
INSIGHT
ANALYSIS
PROJECT
PROJECT_PROFILE
HUMAN_CONTEXT
ENGINEERING_EVENT
ARCHITECTURE_KNOWLEDGE
REPOSITORY_EVIDENCE
ENGINEERING_STORY
DECISION
CHALLENGE
```

Speculative namespaces must not be added merely for future possibilities.
However, semantic sections and relationship endpoints must not be left on a
parallel identity convention.

Bare UUIDs are not the target AI-facing identity. Existing raw UUIDs remain in
the provider projection during this preparatory Story solely for compatibility.

### 3. Reference scopes

The model must distinguish identity scope explicitly. Relevant scopes include:

```text
ANALYSIS_CONTEXT
PROJECT
SOURCE_REVISION
REPOSITORY
```

Exact enum names remain implementation details. Scope communicates reference
lifetime and validity boundaries; it is not a container for authoritative domain
IDs.

### 4. Analysis-local handles

Analysis-local entities should use opaque context-local references:

```text
FACT            -> F001, F002, ...
OBSERVATION     -> O001, O002, ...
ANALYSIS        -> A001
PROJECT_PROFILE -> PP001
```

The exact syntax is not frozen. Handles must be:

- deterministic;
- namespace-safe;
- scope-explicit;
- collision-free within the context;
- resolvable by Core;
- independent of project-controlled text.

Raw Analysis-local UUIDs must not become the target AI-facing identity.

### 5. Stable references

Project- or repository-scoped entities may reuse existing canonical identities
where appropriate:

```text
INSIGHT           -> typed stable Insight reference
ENGINEERING_EVENT -> typed stable event reference
REPOSITORY_EVIDENCE -> canonical repository evidence reference
```

The implementation must inspect and reuse existing canonical-reference
semantics instead of introducing duplicate stable identity systems. Bare UUIDs
are not sufficient as stable AI-facing references.

### 6. Registry and binding

Introduce a Core-owned execution mapping conceptually equivalent to:

```text
AiReferenceRegistry
```

with bindings conceptually equivalent to:

```text
AiReferenceBinding
    reference
    domainEntityType
    domainIdentity or canonicalIdentity
    groundingCapabilities
```

The registry:

- consumes already-authorized `SelectedKnowledge`;
- does not query repositories;
- does not select or rank knowledge;
- does not retrieve additional context;
- does not classify semantic sections;
- does not infer relationships;
- does not call the AI Engine.

It projects identity. It does not select knowledge.

### 7. Binding uniqueness

A single domain/context entity receives one binding within a registry.

```text
Insight X
    appears in semantic section A
    appears in semantic section B
    appears as relationship endpoint
        |
        v
ONE AiReference binding
```

Semantic-section membership must not create another identity. Relationship
projection must not create a separate identity convention.

The Story must establish a shared binding lookup seam even though serialized
semantic-section and relationship JSON remains legacy for now.

### 8. Grounding capability metadata

The registry prepares future grounding authorization without changing current
validation behavior:

```text
FACT               -> SUPPORTING_FACT
OBSERVATION        -> SUPPORTING_OBSERVATION
REPOSITORY_EVIDENCE -> EVIDENCE_REFERENCE
other context      -> not authorized for those fields
```

This metadata is Core-owned execution metadata. It does not replace the current
Python or Java allow-list validation in this Story.

### 9. Deterministic assignment

Reference assignment must use authoritative deterministic ordering for each
projected collection. It must not depend on:

- `HashSet` iteration;
- database incidental ordering;
- JVM iteration behavior;
- accidental serialization order.

If selected ordering is intentionally semantically meaningful, it must be
preserved rather than replaced by arbitrary UUID sorting.

Required invariant:

```text
identical authorized SelectedKnowledge
    + identical deterministic projection ordering
    -> identical references and mapping digest
```

### 10. Execution snapshot

The mapping must be retained at the `AiTask` execution boundary with:

- mapping contract version;
- deterministic mapping digest;
- all bindings required for future resolution;
- grounding capability metadata.

The mapping snapshot must be stored separately from
`selectedKnowledgeSnapshot`, because the latter is currently sent to the AI
Engine. It should remain an internal Core task representation and must not be
added to `AiTaskResponse` without a separate requirement.

Legacy tasks with no mapping snapshot remain valid and readable.

## Architectural Invariants

This Story inherits ADR-068 and must preserve:

```text
AI_REF_NAMESPACE_EXPLICIT
AI_REF_SCOPE_EXPLICIT
DOMAIN_IDENTITY_AUTHORITY_REMAINS_IN_CORE
GROUNDING_REFERENCES_MUST_BELONG_TO_ALLOWED_CONTEXT_SET
CONTEXTUAL_ENTITIES_CANNOT_BECOME_GROUNDING_BY_IDENTIFIER_REUSE
AI_REFERENCES_MAP_DETERMINISTICALLY
AI_REFERENCES_ARE_VALIDATED_BEFORE_DOMAIN_USE
SEMANTIC_CONTEXT_REMAINS_AVAILABLE
RELATIONSHIP_IDENTITY_IS_SHARED
RETRIEVAL_COMPATIBILITY_PRESERVED
TRUST_IS_NOT_GRANTED_BY_REFERENCE_VALIDITY
RETRY_DOES_NOT_BROADEN_AUTHORITY
```

Not every invariant becomes active provider behavior in this Story. In
particular, provider-side typed references and callback resolution are deferred
to the later contract migration. Story 0127 must establish their Core
foundation without claiming that migration is complete.

## Acceptance Criteria

### AC-1 - Immutable Core reference model

Given a Core-projected entity, when an AI reference is created, then it
contains an explicit namespace/type, opaque reference token and explicit scope.

The model is immutable and distinct from the domain identity.

### AC-2 - Namespace coverage

Given the current `SelectedKnowledge` projection, when the registry is built,
then all currently projected relevant entity families have explicit reference
bindings, including Facts, Observations, Insights, Analysis, Project,
Project Profile, Human Context, Engineering Events, architecture knowledge,
repository evidence and relationship endpoint entities that are actually
projected.

Speculative future entities are not required unless they are already projected
by the path under test.

### AC-3 - Analysis-local handles

Facts, Observations, Analysis and Analysis-associated Project Profile identities
receive opaque context-local references with explicit `ANALYSIS_CONTEXT`-type
scope semantics. Raw Analysis-local UUIDs are not used as the target reference
token.

### AC-4 - Stable reference reuse

Project- or repository-scoped entities use existing canonical identity semantics
where available, wrapped in the typed Core reference model. No duplicate stable
identity system is introduced.

### AC-5 - Deterministic assignment

Given identical authorized `SelectedKnowledge` and identical projection order,
registry generation produces identical references, bindings and mapping digest.

Tests must prove that this result does not depend on hash iteration or incidental
database ordering.

### AC-6 - Binding uniqueness

When one entity appears in multiple semantic sections or as a relationship
endpoint, the registry contains one binding for that entity and all projections
resolve to it.

### AC-7 - Grounding capability metadata

The registry records future grounding capability for Facts, Observations and
repository evidence separately from contextual entities. An Insight binding
cannot be classified as a supporting Fact or supporting Observation candidate.

Current provider and Core validators retain their existing behavior.

### AC-8 - No selection or retrieval

Registry creation performs no repository access, retrieval, selection, ranking,
budget changes, semantic reclassification, relationship inference or AI call.

### AC-9 - Standard Analysis wiring

The standard Analysis preparation path creates and persists the mapping snapshot
for a task with selected knowledge.

### AC-10 - Story Context Analysis wiring

The Story Context Analysis preparation path creates and persists the same mapping
semantics for its selected knowledge without changing its provider request.

### AC-11 - Execution-scoped persistence

The mapping snapshot is persisted on `AiTask` with a contract version and digest.
It is separate from `selectedKnowledgeSnapshot` and does not appear in the
provider-facing selected-knowledge payload.

### AC-12 - Reloadability

After persistence and reload, the registry can reconstruct the same bindings,
scopes, capabilities and digest.

### AC-13 - Legacy compatibility

Tasks created before the mapping snapshot exists, or tasks whose mapping is
absent, remain readable and retain current behavior.

### AC-14 - Provider regression

Existing provider-facing selected knowledge remains logically unchanged. Current
prompt projection tests continue to pass without adding the new registry to the
serialized prompt.

### AC-15 - Trace boundary

Story 0126 trace persistence and callback contracts remain unchanged. The Story
does not inject the mapping into trace payloads or provider diagnostics.

### AC-16 - Focused verification

Focused Core tests and the relevant broader backend suite pass. No Python test
or provider test is required because the provider contract is explicitly
unchanged.

## Test Intent

Tests must cover at least:

- Fact reference creation and deterministic assignment;
- Observation reference creation and deterministic assignment;
- Insight reference creation and stable canonical identity reuse;
- Project, Analysis and Project Profile scope assignment;
- Human Context and Engineering Event bindings;
- repository evidence canonical reference binding;
- architecture-knowledge binding reuse;
- one binding reused across multiple semantic sections;
- one binding reused by relationship endpoints;
- grounding capability separation;
- unknown/duplicate binding detection inside the registry;
- deterministic digest equality for equivalent inputs;
- digest difference when authorized identity-bearing content changes;
- persistence and reload;
- legacy null mapping compatibility;
- unchanged `selectedKnowledgeSnapshot` provider payload;
- standard Analysis wiring;
- Story Context Analysis wiring.

Recommended existing test seams:

```text
backend/src/test/java/.../knowledge/selection/SelectedKnowledgePromptProjectionServiceTest.java
backend/src/test/java/.../knowledge/selection/SemanticSectionComposerTest.java
backend/src/test/java/.../ai/engine/service/AiProposalContractValidatorTest.java
```

The Story should add a focused reference-registry test suite rather than
overloading existing projection tests with all mapping behavior.

## Likely File Forecast

The following is a forecast, not a mandatory class list.

### Potentially new

```text
backend/src/main/java/.../ai/reference/AiReference.java
backend/src/main/java/.../ai/reference/AiReferenceType.java
backend/src/main/java/.../ai/reference/AiReferenceScope.java
backend/src/main/java/.../ai/reference/AiReferenceBinding.java
backend/src/main/java/.../ai/reference/AiReferenceRegistry.java
backend/src/main/java/.../ai/reference/AiReferenceRegistryFactory.java
```

### Potentially modified

```text
backend/src/main/java/.../ai/task/entity/AiTask.java
backend/src/main/java/.../ai/task/service/AiTaskServiceImpl.java
backend/src/main/java/.../analysis/workflow/AnalysisWorkflowServiceImpl.java
backend/src/main/java/.../storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java
backend/src/main/java/.../knowledge/selection/SelectedKnowledgePromptProjectionService.java
backend/src/main/resources/db/migration/V49__add_ai_reference_mapping_snapshot.sql
```

Exact package names, field names and migration shape remain implementation
decisions. A new mapping table is not part of the intended first slice.

## Implementation Subtasks

### Subtask 1 - Define reference value semantics

**Classification: LEARN**

Understand domain identity versus projection identity, namespace, scope and
immutable value modeling before implementation.

Relevant authority:

- ADR-068;
- ADR-063;
- ADR-060.

### Subtask 2 - Build the deterministic registry factory

**Classification: PAIR**

Create the registry from already-authorized `SelectedKnowledge` without
repository access, selection duplication or relationship inference.

### Subtask 3 - Establish shared binding lookup

**Classification: PAIR**

Ensure semantic-section entities and relationship endpoints resolve through one
registry binding without changing their serialized provider representation.

This is the most architecturally sensitive part of the Story.

### Subtask 4 - Persist and reload the mapping

**Classification: DELEGATE after the model is approved**

Add the smallest execution-scoped persistence representation, migration and
reload coverage after the reference model is fixed.

### Subtask 5 - Wire both Core preparation paths

**Classification: PAIR**

Wire standard Analysis and Story Context Analysis without changing the provider
request or Python contract.

### Subtask 6 - Add deterministic regression tests

**Classification: DELEGATE after the model is approved**

Implement the specified mapping, digest, persistence and unchanged-payload test
matrix.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Registry becomes a second context-selection pipeline | Factory consumes only `SelectedKnowledge`; no repository access or ranking |
| Mapping enters the provider prompt accidentally | Persist separately from `selectedKnowledgeSnapshot`; add prompt regression assertions |
| Standard and Story Context Analysis paths diverge | Require both paths to create the same registry semantics |
| Stable-reference semantics are prematurely frozen | Keep exact token syntax and class names implementation-level |
| Raw UUIDs are mistaken for the new contract | Document legacy provider representation and keep contract version explicit |
| Relationship endpoints receive a second identity system | Resolve endpoints through shared binding lookup |
| Mapping snapshot exposes internal IDs broadly | Keep it internal to `AiTask`; do not extend `AiTaskResponse` without authorization |
| Historical tasks lack a registry | Treat null mapping as legacy-compatible; never reinterpret historical IDs |
| Story expands into provider migration | Enforce explicit non-goals and defer contract changes to Story 0128 |

## Open Decisions for Implementation

These decisions must be resolved during implementation without reopening ADR-068:

1. Exact package, class and record names.
2. Exact stable-reference syntax for project and repository entities.
3. Exact authoritative ordering for every selected collection.
4. Exact JSONB field name and migration details.
5. Whether the mapping digest is derived from canonical binding data only.
6. Whether the mapping digest contributes to the existing `contextDigest`.
   The default recommendation is **no** for this Story.
7. Whether internal mapping diagnostics are needed. The default recommendation
   is **no endpoint and no trace-schema change**.
8. Whether `AiTaskResponse` exposes mapping metadata. The default recommendation
   is **no**.

## Governing Decisions and Evidence

- ADR-006 - AI Proposal and Knowledge Promotion Workflow.
- ADR-055 - Engineering Context Enrichment and Projection.
- ADR-060 - Deterministic Core, Probabilistic Intelligence, and Governed Effects.
- ADR-063 - Engineering Context Retrieval and Composition Architecture.
- ADR-064 - Hybrid Analysis Context Composition Architecture.
- ADR-065 - Analysis Synthesis and Knowledge Proposal Separation.
- ADR-066 - Persist a Replayable AI Intent Evaluation Harness.
- ADR-067 - DevLog Engineering Story Context Agent.
- ADR-068 - Typed AI-Facing References and Grounding Namespace Isolation.
- Story 0126 - AI Interaction Traceability for DevLog Analyses.

Relevant implementation paths:

- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SemanticSectionComposer.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/task/service/AiTaskServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java`
- `ai-engine/app/prompts/insight.py`
- `ai-engine/app/services/insight_generation_service.py`

## Story Lifecycle

- Story definition/design: **READY_FOR_HUMAN_REVIEW**
- ADR-068 dependency: **PRESENT IN WORKTREE, NOT COMMITTED AT BASELINE HEAD**
- Implementation authorization: **NOT_AUTHORIZED**
- Branch creation: **NOT_AUTHORIZED**
- Production code changes in this refinement: **NONE**
- Story 0128: **NOT_CREATED**

```text
STORY_0127_READY_FOR_HUMAN_REVIEW
```
