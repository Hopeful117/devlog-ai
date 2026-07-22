# Deterministic Knowledge Collection

## Overview

The collection package implements ADR-023's deterministic boundary between a
Project's active Sources and the Facts attached to an Analysis.

The execution order is:

```text
Workspace synchronization -> Collectors -> Facts -> Observation Engine
```

`AnalysisWorkflowService` runs this pipeline before deterministic analysis,
`AnalysisContext` construction and AI task submission.

## Workspace

Each persisted Git Source owns one workspace named by its UUID under
`collection.workspace-root`. Synchronization is serialized per Source and runs
fetch, clean, detached checkout and hard reset. A missing or corrupted
workspace is recreated once automatically.

The default target is `origin/<defaultBranch>` (or `origin/HEAD`). An Analysis
may instead request a branch, tag or commit SHA. The resolved commit is recorded
in the generated Fact evidence.

## Extension Boundaries

- `WorkspaceManager` owns local source synchronization.
- `KnowledgeCollector` implementations are stateless and produce Facts only.
- `ObservationEngine` consumes the newly persisted Facts and produces
  deterministic Observation descriptions.
- `KnowledgeCollectionService` orchestrates these boundaries for active Sources
  belonging to the Analysis Project.

ADR-024 adds versioned repository metadata, build, Spring, Docker,
documentation and test-structure Collectors. They inspect files through a
shared deterministic scanner with file, byte, Fact, directory and timeout
limits. Paths are normalized relative to the workspace, symbolic links are not
followed, generated/dependency directories are excluded, and repository tools
or scripts are never executed.

Each collected Fact carries the Collector version as its source and a SHA-256
fingerprint derived from its normalized content, evidence, type, version and
resolved Git revision. The unique Analysis/fingerprint identity makes retries
idempotent while preserving Facts across separate Analyses. Structured
non-fatal warnings are returned by the collection result.

No speculative Observation rule is defined, so the Observation Engine still
intentionally emits no Observations.

The package does not build `AnalysisContext`, call the AI Engine, validate
knowledge or access AI providers.
