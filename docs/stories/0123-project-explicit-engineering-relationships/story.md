# Story 0123: Project Explicit Engineering Relationships

## Goal

Preserve existing explicit, directional `KnowledgeRelation` edges in the
bounded AI-facing analysis context together with both projected endpoints.

## Scope

- Reuse the existing `relationshipHighlights` projection as the bounded edge
  contract.
- Permit explicitly modeled Decision and Challenge endpoints when their
  selected repository evidence is present.
- Omit relations whose source or target endpoint is not projected.
- Emit deterministic diagnostics for omitted relations.
- Preserve existing fact/observation and fact/evidence grounding behavior.

## Non-Goals

- No universal graph model or graph database.
- No relationship inference from text, co-occurrence, commits, packages, or
  Story identity.
- No prompt redesign, RAG, embeddings, vector search, or ranking redesign.
- No promotion of AI output into trusted relationships.

## Acceptance

1. Explicit Insight, EngineeringEvent, Decision, and Challenge relations can
   be projected when both endpoints are present.
2. Relation type, direction, and endpoint identity are preserved.
3. Relations with unavailable endpoints are excluded from authoritative prompt
   context and diagnosed deterministically.
4. Relation descriptions are not projected because their origin and trust are
   not independently represented.
5. Existing grounding closure and repository evidence contracts remain intact.
6. Focused and broader backend tests pass.
