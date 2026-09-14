# Repository Analysis

## Scope

Story 0123 preserves existing explicit `KnowledgeRelation` edges in the
analysis prompt projection. It does not introduce a graph model, infer new
relationships, or change global evidence ranking.

## Current Path

`ProjectContextProviderImpl` loads up to the existing bounded set of
`KnowledgeRelation` records and maps them to
`ProjectContextSnapshot.KnowledgeRelationSnapshot`. `AnalysisContextServiceImpl`
copies those snapshots into `AnalysisContext`. `KnowledgeSelectionServiceImpl`
retains the relation list in `SelectedKnowledge` while selecting facts,
observations, insights, engineering events, and repository evidence.

`SelectedKnowledgePromptProjectionService` currently emits
`relationshipHighlights`, but `isSelectedProjectedEndpoint` accepts only
`INSIGHT` and `ENGINEERING_EVENT`. Decision and Challenge endpoints are
therefore discarded even when their corresponding repository evidence is
selected. A relation is also discarded when either endpoint is not represented
in the projected selected context.

Relevant implementation:

- `backend/src/main/java/com/hopeful117/devlogai/knowledge/relation/entity/KnowledgeRelation.java`
- `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextSnapshot.java`
- `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContext.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`

## Description Trust Finding

`KnowledgeRelation.description` is nullable and is accepted directly by
`CreateKnowledgeRelationRequest`. `KnowledgeRelationServiceImpl` validates
that endpoints differ, but does not validate, classify, or provenance the
description. `KnowledgeRelationMapper` copies the request description to the
entity. No relation creation path inspected here attaches proposal,
validation, author, or trust metadata to the description.

The description may therefore be human-supplied through the REST API, but its
authority is not represented independently in the domain model. It may also be
created in workflows that are not distinguishable from direct client creation.
Under ADR-006, it is not safe to expose as authoritative semantic context in
this Story. The projection will preserve source identity, target identity,
direction, and relation type only. The description remains available
internally and is intentionally omitted from the AI-facing relation.

## Projection Model

The existing `PromptRelationshipHighlight` is reused as the bounded explicit
relationship contract. It contains:

- relation type;
- directed source entity type and ID;
- directed target entity type and ID.

It remains capped by `MAX_RELATIONSHIP_HIGHLIGHTS`. No relation expands the
selected evidence budget. A relation is projected only when both endpoints are
already represented in the selected prompt context.

Endpoint availability is determined as follows:

- `INSIGHT`: selected insight or selected repository evidence with
  `kind=INSIGHT` and `reference=insight:<id>`;
- `ENGINEERING_EVENT`: selected engineering event or selected repository
  evidence with `kind=ENGINEERING_EVENT` and `reference=event:<id>`;
- `DECISION`: selected repository evidence with `kind=DECISION` and
  `reference=decision:<id>`;
- `CHALLENGE`: selected repository evidence with `kind=CHALLENGE` and
  `reference=challenge:<id>`.

An unavailable endpoint causes the relation to be omitted. No partial relation
is sent to the model. Omitted relations produce deterministic projection
diagnostics with the relation ID and missing endpoint reason.

## Budget and Trust Boundary

Existing knowledge and repository evidence budgets remain authoritative.
Relationship projection does not select endpoints, bypass token limits, or
expand repository evidence. The existing relationship highlight cap remains
the relationship budget. Relations beyond the cap are omitted with a
deterministic diagnostic.

No textual similarity, co-occurrence, same-commit, package, or Story matching
creates a relation. No AI output is persisted as a trusted relation.

## Governing Decisions

- ADR-006: AI output is not trusted knowledge and requires human validation.
- ADR-055: deterministic Core owns explicit relationships and bounded context
  composition; enrichment must be incremental and consumer-driven.
