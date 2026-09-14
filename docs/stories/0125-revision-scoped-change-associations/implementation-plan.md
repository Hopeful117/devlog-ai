# Story 0125 - Implementation Plan

1. Add an immutable consumer-neutral `EngineeringRelationship` boundary with
   typed Knowledge, repository Commit, and repository File endpoints, explicit
   origin/trust, revision scope, and evidence references.
2. Add repository-derived relationship snapshots to the shared project and
   analysis context without modifying `KnowledgeRelation`, `EntityType`, or
   durable persistence.
3. Project `ProjectCommit.changedFiles` through the existing
   `ProjectCommitRepository` and use `source + commitHash` plus canonical
   repository-relative paths for endpoint identity.
4. Extend bounded Story 0124 admission to admit repository change relations
   using the existing relational capacity and deterministic ordering; do not
   change budgets or introduce another admission pipeline.
5. Preserve strict endpoint closure by requiring both typed repository
   endpoints to be present in the selected context before admission and prompt
   projection.
6. Extend the structured relationship projection to emit the explicit
   `CHANGES` edge with canonical endpoint identities and evidence references.
7. Keep Story-level change-window relationships out of scope.
8. Add focused tests for extraction, identity, provenance, closure, bounded
   admission, coexistence with durable relations, and explicit AI projection.
9. Run focused regressions, full backend verification, and `git diff --check`.
