# Story 0137: Source-Aware Canonical Evidence Reference Semantics

## Status

`IMPLEMENTATION SLICE COMPLETE - READY FOR HUMAN REVIEW`

## Architectural Basis

- ADR-038 - Repository Context Engine
- ADR-063 - Engineering Context Retrieval and Composition Architecture,
  including the 2026-09-21 canonical evidence amendment
- ADR-068 - Typed AI-Facing References and Grounding Namespace Isolation

## Objective

Establish the smallest repository-consistent Core representation and validation
boundary for canonical evidence reference semantics without introducing a
universal `Evidence` entity or requiring one concrete syntax for every evidence
family.

The Story must make repository source ownership unambiguous, especially for
changed-file and structure evidence, while preserving category-specific
revision semantics and existing legacy representations.

## Scope

- Inventory and classify current `RepositoryEvidence.reference` producers.
- Define the implementation-level semantic contract for canonical evidence
  references and their evidence-family type dispatch.
- Preserve source identity for repository-scoped evidence, including changed
  file/diff and repository structure evidence.
- Identify which existing references are canonical, legacy/display-only or
  incomplete for canonical resolution.
- Add deterministic validation and conformance tests for source ambiguity,
  category identity and revision metadata.
- Preserve existing `DocumentReference` source/path/revision semantics.
- Preserve `AiReference` as a separate task-scoped provider reference.

## Explicit Non-Goals

- No universal `Evidence` aggregate or entity.
- No shared resolver implementation; that is Story 0138.
- No retrieval ranking, search, vector search, RAG or ContextPack.
- No authorization model.
- No MCP resource redesign.
- No migration of historical persisted references.
- No reinterpretation of existing analysis or AI task snapshots.
- No broad AI contract migration.

## Invariants

1. A canonical reference identifies one evidence item within a defined scope.
2. Repository-scoped canonical evidence is associated with one unambiguous
   `sourceId`.
3. Reference identity is independent of authorization, trust, relevance,
   confidence, ranking and composition.
4. Revision is included in identity only where intrinsic to the evidence
   category.
5. Legacy/display references are not silently promoted to canonical identity.
6. Ambiguous source ownership fails deterministically and never selects the
   first active source.
7. Existing persisted tasks and analyses remain readable without rewriting.

## Acceptance Criteria

1. Every current Repository Context collector has a documented reference
   classification and owning evidence family.
2. Changed-file/diff evidence carries or resolves through unambiguous source
   identity without requiring source inference from active-source ordering.
3. Structure evidence records source and revision semantics sufficient for its
   declared resolution capability, or is explicitly classified as a bounded
   non-expandable projection.
4. Document evidence continues to use deterministic source/path/revision
   semantics.
5. Tests reject ambiguous repository source ownership.
6. Tests prove reference identity does not grant authorization or trust.
7. Tests prove ranking/composition changes do not change evidence identity.
8. No historical reference or persisted task is silently rewritten.
9. No universal Evidence entity or transport-specific identity is introduced.

## Expected Tests

- Collector reference-family matrix tests.
- Multi-source changed-file identity tests.
- Structure evidence source/revision tests.
- Document reference round-trip tests.
- Legacy reference compatibility tests.
- Identity versus ranking/composition tests.

## Implementation Modes

- **LEARN:** establish the distinction between evidence identity, provenance,
  source identity and revision using the existing collectors and ADR-063.
- **PAIR:** choose the smallest Core representation for source-aware reference
  semantics without creating a second identity system.
- **DELEGATE:** add repetitive collector fixtures and compatibility assertions
  after the semantic boundary is agreed.

## Dependencies

None for the documentation and analysis phase. Story 0138 depends on the
reference-family classification produced here.

## Compatibility

This Story does not authorize production implementation, migration, commit or
push. Human implementation authorization remains required.
