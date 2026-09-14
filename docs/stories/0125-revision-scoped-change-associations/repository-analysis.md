# Story 0125 - Repository Analysis

## Status

`IMPLEMENTATION_AUTHORIZED`

## Repository State Before Implementation

- Base: local `main` at `59f99822600db826740c42e4097b545ee30f7a82`
- Story branch: `story/0125-revision-scoped-change-associations`
- Local `main` contains the Story 0123 and Story 0124 merges.
- The pre-existing `docs/discoveries/` change and generated
  `devlog-contracts/target/*` changes were preserved.

## Authoritative Repository Facts

### Commit identity

`ProjectCommit` is the authoritative persisted commit model at
`backend/src/main/java/com/hopeful117/devlogai/history/entity/ProjectCommit.java`.
Its canonical repository identity is the tuple represented by `source` and
`commitHash`; its database `UUID` is the persistence identity.

### File identity and changed-file source

`ChangedFile` is the authoritative persisted diff row at
`backend/src/main/java/com/hopeful117/devlogai/history/entity/ChangedFile.java`.
It exposes `oldPath`, `newPath`, `FileChangeType`, and diff statistics. The
repository-relative path fields are available, but there is no existing
repository file endpoint model in the relationship contract.

`ProjectCommit.changedFiles` is populated by the existing history import and
diff collection path. A second Git parser is therefore not justified.

### Revision scope

`ProjectCommit.commitHash` provides the natural scope for
`COMMIT --CHANGES--> FILE`. Story and Engineering Event snapshots expose
`baseCommit` and `targetCommit`, but this analysis did not establish a
relationship projection over those windows because the shared relation and
admission contracts cannot represent the required endpoints.

### Project and source scope

`ProjectCommit` carries both `Project` and `Source`. Any repository-derived
projection would need to preserve both identities together with commit/revision
scope.

### Existing relationship projection

`ProjectContextSnapshot.KnowledgeRelationSnapshot` at
`backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextSnapshot.java`
contains:

- `UUID id`;
- `EntityType sourceEntityType` and `UUID sourceEntityId`;
- `EntityType targetEntityType` and `UUID targetEntityId`;
- durable `KnowledgeRelationType`;
- description and creation time.

`EntityType` currently contains only `CHALLENGE`, `DECISION`,
`ENGINEERING_EVENT`, and `INSIGHT`. It has no `COMMIT`, `FILE`, or repository
evidence endpoint kind.

### Story 0124 admission compatibility

`KnowledgeSelectionServiceImpl` admits only `INSIGHT` and
`ENGINEERING_EVENT` endpoints. Its endpoint lookup and candidate maps are
UUID maps over those two domain categories. `relationalCapacity`, candidate
budgets, and final category budgets are intentionally fixed by Story 0124.

`SelectedKnowledgePromptProjectionService` also recognizes only the four
existing `EntityType` values and checks endpoint closure through selected
Insight/Event objects or repository-evidence keys for Decision/Challenge.

Therefore a repository-derived `COMMIT -> FILE` relation cannot currently
enter shared Story 0124 admission or final projection as a consumer-neutral
relation without introducing repository endpoint categories and corresponding
candidate/closure/projection behavior.

## Human Architectural Decision

The human selected **Alternative 1 - Hybrid shared relationship projection**.
The decision explicitly separates domain sources of truth from the
consumer-neutral relationship projection:

- `KnowledgeRelation` remains the durable validated domain relationship model.
- Repository change relationships are reconstructible technical facts derived
  from `ProjectCommit.changedFiles`.
- A new immutable, consumer-neutral relationship boundary represents both
  families without replacing either source of truth.
- Repository commit identity is `project/source + commitHash`.
- Repository file identity is `project/source + revision + repository-relative
  path`.
- `COMMIT --CHANGES--> FILE` is the only repository relationship in this
  Story. Story-window relationships remain out of scope.

## Resolved Architectural Mismatch

The previous mismatch was:

1. Story 0124's admitted endpoint universe is limited to `INSIGHT` and
   `ENGINEERING_EVENT`.
2. The existing relation snapshot is a durable `KnowledgeRelation` shape with
   `EntityType + UUID`, and its enum has no repository endpoint types.
3. A FILE endpoint requires canonical repository-relative identity and
   revision/source provenance, which cannot be represented safely as the
   current durable entity UUID endpoint.
4. The existing prompt projection's strict endpoint closure has no COMMIT/FILE
   projection path.

The human decision now authorizes a small shared relationship boundary and
repository endpoint projection, while explicitly prohibiting a universal graph,
durable repository relations, and a second admission pipeline.

## Authorized Implementation Boundary

- Add the smallest immutable shared relationship representation.
- Keep durable relationships represented by their existing snapshots and
  lifecycle.
- Add repository-derived relationships as reconstructed context data only.
- Reuse Story 0124's bounded capacity and deterministic ordering.
- Admit repository relationships only when both typed endpoints are available.
- Preserve repository provenance and canonical identities through AI projection.
- Do not implement `STORY --CHANGES--> FILE`.

## Story-Level Support

`COMMIT_CHANGE_RELATION_READY` is authorized and in scope.

`STORY_CHANGE_RELATION_BLOCKED` remains intentionally applicable: Story-window
relationship semantics are explicitly out of scope for Story 0125.

No production code, tests, prompts, or durable relation lifecycle were changed
before this authorization.
