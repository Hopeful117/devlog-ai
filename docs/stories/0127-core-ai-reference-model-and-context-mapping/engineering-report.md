# Story 0127 - Engineering Report

## Status

**IMPLEMENTED - UNCOMMITTED, AWAITING HUMAN REVIEW**

## Delivered Architecture

```text
Authorized SelectedKnowledge
          ↓
Core AiReferenceRegistryFactory
          ↓
AiReferenceRegistry
          ├── deterministic bindings
          ├── shared semantic/relationship lookup
          └── grounding capability metadata
          ↓
AiReferenceMappingSnapshot
          ↓
AiTask JSONB execution metadata
```

The existing projection remains parallel and unchanged:

```text
SelectedKnowledge
          ↓
SelectedKnowledgePromptProjectionService
          ↓
PromptRequest
          ↓
Python provider
```

## Authority Assessment

```text
JAVA_CORE = reference semantics + deterministic mapping + persistence
SELECTED_KNOWLEDGE = already-authorized input; no duplicate retrieval pipeline
MAPPING_SNAPSHOT = internal execution metadata, not trusted knowledge
PYTHON_PROVIDER = unchanged probabilistic execution boundary
```

The registry creates explicit typed references, scopes and grounding capability
metadata. It does not grant trust, authorize grounding by itself, or replace
the existing validators.

## Identity And Determinism

- Analysis-local facts and observations use opaque context-local handles.
- Root Analysis uses `A001`; the associated project profile uses its analysis
  context binding.
- Project and repository entities reuse canonical stable identity semantics.
- Semantic sections and relationship endpoints resolve through the same binding.
- Equivalent authorized inputs produce the same bindings and digest.
- Digest canonicalization uses length-prefixed components.

## Durability And Compatibility

- `AiTask` stores a nullable JSONB mapping snapshot.
- The snapshot includes contract version, digest, bindings and capabilities.
- V49 adds the field without introducing a separate mapping table.
- Historical tasks with no mapping remain readable and preserve current
  behavior.
- The snapshot is not included in `AiTaskResponse` or provider payloads.

## Preparation Paths

Both standard Analysis and Story Context Analysis create mapping metadata from
the same selected knowledge used by their existing provider preparation. This
prevents divergence between the internal mapping and the authorized provider
selection while preserving the current request contract.

## Quality Evidence

- Focused reference tests: 18 passed.
- Persistence integration tests: 6 passed.
- Full backend suite: 1,316 passed.
- Flyway migrations through V49 passed.
- `git diff --check`: passed.

## Final Assessment

```text
CORE_REFERENCE_MODEL = PRESENT
DETERMINISTIC_MAPPING = PRESENT
SHARED_BINDING_IDENTITY = PRESENT
GROUNDING_NAMESPACE_ISOLATION = PRESERVED
EXECUTION_SNAPSHOT_PERSISTENCE = PRESENT
PROVIDER_CONTRACT = UNCHANGED
STORY_0126_TRACE_BOUNDARY = PRESERVED
QUALITY_GATES = PASSED
STORY_ACCEPTANCE_GATE = AWAITING_HUMAN_REVIEW
```
