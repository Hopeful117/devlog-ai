# Story 0118 — Revision-Pinned Human-Authored Repository Documents in Story Context Analysis

## Status

DESIGN CONSOLIDATED

## Baseline

- Baseline SHA: `504a867` (Story 0117 merge on `main`)
- Baseline branch: `main`
- Implementation branch: `story/0118-revision-pinned-repository-documents-in-sca`
- Governing ADRs: ADR-006, ADR-063 (§28, §42), ADR-067
- Preserved Stories: 0116, 0117

## Problem

ADR-063 §28 explicitly declares that repository-authored document bodies (ADRs,
Story Markdown, roadmap) are not yet retrievable as first-class evidence. Story
Context Analysis currently receives only:

- `DecisionSnapshot` (title/choice/rationale from DB) — not the ADR Markdown body;
- `EngineeringStorySnapshot` (title/status/storyPath from DB) — not the Story
  Markdown body;
- No roadmap evidence.

This means the AI agent cannot reason about ADR status, Story acceptance
criteria, or project roadmap when analyzing Story context, even when the
current Story explicitly references those documents.

The `DocumentationCollector` scans `*.md` files and produces presence Facts
(`MARKDOWN_DOCUMENT_PRESENT`, `ADR_DOCUMENT_PRESENT`) but does not retain
document body content, does not parse ADR status, and does not produce
`RepositoryEvidence` with content for document bodies.

## Objective

Allow Story Context Analysis to receive bounded, revision-pinned bodies of
explicitly related human-authored repository documents as structured
`HUMAN_AUTHORED` repository evidence, reusing existing repository-context
infrastructure.

```text
Current Engineering Story
        |
        v
resolve RepositoryRevisionScope once
  (project / source / revision)
        |
        v
deterministic document discovery
  (current Story body + explicit ADR/Story/roadmap references)
        |
        v
bounded document body reading
  (SecureRepositoryContentReader at pinned revision)
        |
        v
ADR status parsing (deterministic, ## Status heading only)
        |
        v
RepositoryEvidence production
  (HUMAN_AUTHORED, canonical reference, content, status metadata)
        |
        v
existing RepositoryContextEngine pipeline
  (ranking, selection, enrichment, budget)
        |
        v
existing grounding contract
  (canonical reference in allowlist)
```

## Architecture Direction

### RepositoryRevisionScope

A value object resolved once per SCA execution:

```java
record RepositoryRevisionScope(
    UUID projectId,
    UUID sourceId,
    String resolvedRevision,
    Path workspacePath,
    String revisionSource
) {}
```

Resolved from (in priority order):

1. `EngineeringStory.targetCommit` (if non-null);
2. `Analysis.targetRevision` (from baseline Analysis);
3. Source `currentRevision` (latest known);
4. HEAD (fallback via `WorkspaceManager`).

The scope is immutable for the execution and propagated to all
repository-context consumers. Collectors must not independently resolve
potentially different revisions.

### Document Discovery

Deterministic, relationship-driven, one-hop only:

- **Current Story**: eligible through exact registered `storyPath`.
- **ADR**: eligible when current Story contains exact `ADR-NNN` identifier or
  exact repository-relative Markdown link.
- **Engineering Story**: eligible when current Story contains exact
  `Story NNNN` identifier or exact repository-relative Markdown link.
- **Roadmap**: eligible only through explicit deterministic relationship.

No automatic project-global context. No recursive traversal.

### Document Body Reading

Reuses existing `SecureRepositoryContentReader`:

```java
ReadResult result = reader.readComplete(
    synchronizedWorkspace,  // at pinned revision
    relativePath,
    maximumCharacters       // from budget policy
);
```

### ADR Status Parsing

Deterministic parser for `## Status` Markdown section:

- Find `## Status` heading;
- Extract next non-empty line;
- Match against: `**Accepted**`, `**Proposed**`, `**Superseded**`,
  `**Deprecated**`, `**Rejected**`;
- Normalize: `Deprecated` → `SUPERSEDED`;
- Return `UNKNOWN` for unmapped values;
- Extract supersession reference if present.

No full Markdown AST. No AI classification. No semantic inference.

### Canonical Reference

Revision-aware document identity:

```text
document:{sourceId}:{normalizedPath}@{revision}
```

- `sourceId`: UUID of the repository source;
- `normalizedPath`: repository-relative file path;
- `revision`: resolved commit SHA.

Reuses existing `extractionMetadata` for source/revision transport. The
reference is deterministic, source-aware, path-aware, revision-aware and
suitable for grounding authorization.

### Evidence Production

For each successfully read document, produce `RepositoryEvidence`:

- `layer`: `ADR`, `ROADMAP`, or `PROJECT_DOCUMENTATION` (existing enum values);
- `kind`: `ADR_DOCUMENT`, `STORY_DOCUMENT`, `ROADMAP_DOCUMENT`;
- `reference`: canonical `document:{sourceId}:{path}@{revision}`;
- `content`: `RepositoryEvidenceContent` with body text, revision, status;
- `extractionMetadata`: `resolvedRevision`, `documentStatus`,
  `supersededBy` (if applicable);
- `provenance.sourceType`: `REPOSITORY_DOCUMENT`;
- `provenance.repositoryLocation`: sourceId;
- `provenance.originatingFile`: relative path.

### Budget

Configuration properties (reusing existing conventions):

```properties
devlog.repository-context.documents.max-selected=${REPOSITORY_CONTEXT_DOCUMENT_MAX_SELECTED:5}
devlog.repository-context.documents.max-characters-per-document=${REPOSITORY_CONTEXT_DOCUMENT_MAX_CHARS:4000}
devlog.repository-context.documents.max-total-characters=${REPOSITORY_CONTEXT_DOCUMENT_MAX_TOTAL_CHARS:12000}
```

Allocation: documents sorted by priority tier (current Story > ACCEPTED ADRs
> referenced Story > referenced roadmap > PROPOSED/SUPERSEDED ADRs), then
deterministic path order. First N within budget selected. Truncation applies
per-document.

### Integration Point

The `DocumentBodyCollector` is a new `RepositoryContextCollector` registered
in the existing `RepositoryContextEngine` collector pipeline. It receives the
`ContextRequest` and produces `RepositoryEvidence` candidates alongside
existing collectors.

`RepositoryRevisionScope` is resolved in `AnalyzeStoryContextUseCase` and
passed through `ContextRequest` (new optional field, backward-compatible).

## Responsibility Boundaries

### AnalsisStoryContextUseCase

Owns `RepositoryRevisionScope` resolution. Constructs the scope once from
Story, baseline Analysis and Source before any collector runs. Passes scope
through `ContextRequest`.

### DocumentBodyCollector

Owns document discovery, body reading, status parsing and evidence production.
Uses deterministic path resolution. Does not implement ranking or selection.

### RepositoryContextEngine

Unchanged. Receives additional candidates from `DocumentBodyCollector` via
existing collector pipeline. Applies existing ranking, selection, enrichment
and budget.

### KnowledgeSelectionService

Unchanged. Remains final selected-knowledge authority. Document evidence
flows through existing `RepositoryContext` path.

### SecureRepositoryContentReader

Unchanged. Reads document body content from synchronized workspace. Already
handles size limits, symlink checks, encoding validation.

## Acceptance Criteria

1. The current Story body reaches SCA from its exact registered `storyPath`.
2. One explicit project/source/revision scope is resolved for the SCA
   execution (`RepositoryRevisionScope`).
3. All repository document reads use that shared revision scope.
4. Multi-source ambiguity never silently selects the first active source;
   ambiguity produces deterministic omission or diagnostic behavior.
5. Direct ADR references (`ADR-NNN`) from the current Story are
   deterministically resolvable to document paths.
6. Direct Story references (`Story NNNN`) from the current Story are
   deterministically resolvable through existing Story identity/registry
   mechanisms.
7. Direct roadmap references (exact Markdown links or supported paths) from
   the current Story are deterministically resolvable.
8. Unrelated documents (not referenced by the current Story) remain excluded.
9. `docs/roadmap.md` is not automatically included without an explicit
   relationship from the current Story.
10. Retrieval remains one-hop: documents discovered from the current Story do
    not recursively expand their own references.
11. No fuzzy, semantic, vector, or LLM retrieval occurs.
12. Document candidates are bounded before body materialization.
13. Maximum selected document count defaults to 5 (configurable).
14. Per-document body limit defaults to 4,000 characters (configurable).
15. A configurable deterministic total document-content budget exists
    (initially 12,000 characters).
16. One large document cannot monopolize document-content allocation.
17. Document identity is source/path/revision aware
    (`document:{sourceId}:{path}@{revision}`).
18. Duplicate documents are eliminated by stable canonical identity.
19. Repository documents reach SCA as `HUMAN_AUTHORED` trust tier.
20. Status is parsed deterministically from the `## Status` Markdown section.
21. Unknown or unrecognized status values become `UNKNOWN`.
22. Status does not act as the primary relevance ranking; relationship
    determines eligibility.
23. Superseded and rejected documents remain distinguishable from accepted
    documents through status metadata.
24. Only canonical selected evidence references expand grounding authority.
25. Provenance metadata (path, sourceId, identifier) does not independently
    expand grounding authority.
26. Existing Story 0116 grounding validation remains unchanged.
27. Existing Story 0117 Fact/Observation historical retrieval remains
    unchanged.
28. Existing `KnowledgeSelectionService` remains final selected-knowledge
    authority.
29. Existing repository-context infrastructure is reused rather than creating
    a parallel document selector.
30. Deterministic repeated execution produces equivalent selection, order,
    truncation and context identity for identical inputs.
31. Cross-language SCA compatibility is verified (Java/Python boundary).
32. Tests prove no full documentation-tree body load or recursive document
    traversal.

## Explicitly Out Of Scope

- RAG, embeddings, vector databases/indexes, semantic search, fuzzy retrieval;
- LLM relationship discovery or AI-assisted document discovery;
- recursive document traversal or document graph expansion;
- automatic project-global roadmap inclusion;
- roadmap phase interpretation;
- repository ADR to `Decision` entity synchronization;
- Story Markdown to `EngineeringStory` entity synchronization;
- generic `Documentation` aggregate migration;
- Decision relevance redesign;
- EngineeringEvent relevance redesign;
- new trust tiers;
- full Markdown AST solely for status extraction;
- proactive agent behavior;
- universal ContextPack architecture;
- frontend work;
- unrelated refactoring;
- Decision or EngineeringEvent selection changes.

## Quality Gates

- focused `DocumentBodyCollector` tests (discovery, reading, evidence
  production);
- focused `AdrStatusParser` tests (status extraction grammar);
- focused `RepositoryRevisionScope` resolution tests;
- focused document budget allocation tests;
- existing Story-aware selection and SCA integration tests unchanged;
- existing `RepositoryContextEngine` tests unchanged;
- full backend verification with JaCoCo;
- full AI Engine regression;
- cross-language SCA compatibility test;
- `git diff --check`;
- no generated build artifacts committed.

## Authorization

- Design: consolidated
- Implementation: not authorized under this prompt
- Commit: not authorized under this prompt
- Push, merge, PR, remote operations, and branch deletion: not authorized
