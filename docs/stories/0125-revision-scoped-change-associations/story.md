# Story 0125: Revision-Scoped Change Association Relationships

## Goal

Expose the first reconstructible repository-derived engineering relationship:
`COMMIT --CHANGES--> FILE`, through the shared bounded context pipeline.

## Scope

- Keep `KnowledgeRelation` as durable validated knowledge.
- Add a consumer-neutral immutable relationship projection.
- Derive change relationships from `ProjectCommit.changedFiles`.
- Preserve project/source, commit hash, revision, repository-relative path, trust,
  provenance, and evidence references.
- Reuse Story 0124 bounded admission and endpoint closure.
- Project admitted `CHANGES` edges explicitly to AI context.

## Non-Goals

- No `STORY --CHANGES--> FILE`.
- No changes to durable `KnowledgeRelation` or `EntityType`.
- No budget or relational-capacity increase.
- No graph infrastructure, persistence store, RAG, embeddings, or prompt redesign.
- No support, governance, or dependency relationships.
- No frontend work.

## Acceptance

1. Durable and repository-derived relationships have distinct origins.
2. Repository Commit/File endpoints are not UUID-faked knowledge endpoints.
3. `COMMIT --CHANGES--> FILE` is reconstructed from existing changed-file data.
4. Repository provenance and technical-evidence trust are preserved.
5. Story 0124 bounded admission is reused without a second pipeline.
6. Strict endpoint closure is preserved.
7. The same explicit relationship is available to consumer-neutral and AI projections.
8. Existing durable relationship behavior remains compatible.
9. Focused and full backend verification pass.
