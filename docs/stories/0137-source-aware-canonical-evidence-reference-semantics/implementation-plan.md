# Implementation Plan - Story 0137

## Status

`SLICE IMPLEMENTED - REMAINING WORK DEFERRED TO FOLLOW-UP REVIEW`

## Implemented Slice

### Step 1: Source-aware changed-file grouping

File: `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/CommitDiffEvidenceCollector.java`

- Group changed files by `(sourceId, path)` rather than path alone.
- Use the owning commit source for the evidence reference and provenance.
- Emit `diff:{sourceId}:{commitHash}:{path}`.
- Preserve the existing aggregation, ordering, filtering and item limits.

### Step 2: Explicit repository-source ambiguity failure

File: `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/RepositoryStructureCollector.java`

- Reject multiple active sources with an explicit `IllegalStateException`.
- Preserve the existing soft-empty behavior for ordinary workspace failures.
- Do not select an active source by ordering.

### Step 3: Source-aware structure file semantics

File: `RepositoryStructureCollector.java`

- Emit file-level references as `file:{sourceId}:{path}@{revision}`.
- Attach `sourceId` and `resolvedRevision` metadata to all structure evidence.
- Mark file evidence `CANONICAL_FILE`.
- Mark aggregate structure evidence
  `NON_EXPANDABLE_STRUCTURE_PROJECTION`.

### Step 4: Domain identity separation

File: `DeterministicKnowledgeContextCollector.java`

- Emit Facts as `fact:{uuid}` regardless of source path or supporting reference.
- Keep supporting references in `relatedReferences`.
- Preserve source paths in provenance.
- Keep Observations as `observation:{uuid}` with supporting Fact references.

### Step 5: Conformance tests

- Add cross-source changed-file regression coverage.
- Add explicit multi-source structure failure coverage.
- Add canonical file and bounded aggregate structure assertions.
- Add Fact and Observation identity/provenance assertions.
- Add ranking identity preservation coverage.
- Add trust-separation coverage for Fact and Decision references.

## Deferred Work

- Shared evidence resolution: Story 0138.
- Retrieval, ranking redesign, authorization, MCP redesign and AI contract
  migration.
- Historical reference migration or rewriting.
- Full resolver support for structure aggregates.
- Broader mechanical collector adaptation after human review.

## Validation Commands

```bash
./backend/mvnw -pl backend test -Dtest=CommitDiffEvidenceCollectorTest,RepositoryStructureCollectorTest,DeterministicKnowledgeContextCollectorTest,DocumentReferenceTest,ProjectKnowledgeContextCollectorTest,BudgetedDiverseEvidenceSelectorTest,EngineeringContextContractMapperTest -B
git diff --check
```
