# Story 0023: Commit-Scoped Evidence for Engineering Event Grounding

## Status

Completed

## Problem Statement

The Engineering Event vertical slice (Story 0022) is architecturally complete but blocked from
live proposal generation because the grounding contract cannot reference commit-diff-scoped
evidence. The `CommitDiffEvidenceCollector` produces file-level metadata (SOURCE_FILE, TEST_FILE,
CONFIG_FILE, MODULE), not the commit-scoped facts that the grounding contract expects.

When the AI Engine generates engineering event proposals, it needs `supportingFactIds` that trace
to actual evidence. Currently, the `allowedSupportingFactIds` list is empty for engineering
events, causing either:
- Zero proposals (original prompt: "Return zero proposals when evidence is insufficient")
- Validation failure (modified prompt: proposals generated but `supportingFactIds` rejected)

## Goal

Introduce commit-scoped fact types that the `CommitDiffEvidenceCollector` produces and the
grounding contract can reference, enabling end-to-end live validation of the Engineering Event
pipeline.

## Acceptance Criteria

### AC-1 — Commit-Diff Fact Types
The `CommitDiffEvidenceCollector` produces new fact types from commit diff analysis:
- `COMMIT_DIFF_SUMMARY`: high-level summary of changes in a commit (files changed, insertions,
  deletions, primary language)
- `COMMIT_CHANGES_MODULE`: identifies which modules/packages are affected
- `COMMIT_ADDS_FEATURE`: when commit message and file changes indicate a new feature
- `COMMIT_FIXES_BUG`: when commit message and file changes indicate a bug fix
- `COMMIT_REFACTORS_CODE`: when changes restructure without changing behavior
- `COMMIT_UPDATES_DEPS`: when dependency files are modified
- `COMMIT_CHANGES_CONFIG`: when configuration files are modified

Each fact includes:
- `factType`: the fact type identifier
- `evidenceReferences`: list of file paths affected by this fact
- `description`: human-readable description of the evidence
- `confidence`: evidence strength assessment (STRONG, MODERATE, WEAK)

### AC-2 — Evidence Collection Integration
The `CommitDiffEvidenceCollector` continues to produce existing file-level evidence types
(SOURCE_FILE, TEST_FILE, CONFIG_FILE, MODULE) alongside the new commit-scoped types. The
collector limit (40 items) applies to the combined set.

### AC-3 — Grounding Contract Coverage
When commit-scoped facts are available, the grounding contract's `allowedSupportingFactIds`
includes them. The AI Engine can reference these facts in `supportingFactIds` for engineering
event proposals.

### AC-4 — Live Validation
After implementation, trigger an engineering event analysis against the devlog-ai project and
verify that:
- Commit-scoped facts are collected and visible in the grounding contract
- OpenAI generates proposals with valid `supportingFactIds`
- At least one proposal can be accepted and promoted to an immutable engineering event

### AC-5 — Backward Compatibility
Existing file-level evidence types are unchanged. The knowledge selection v3 behavior is
unchanged when no evolution context is present. The existing proposal pipeline is unaffected.

## Out of Scope

- Changing the grounding contract validation logic
- Modifying the AI Engine prompt (prompt changes from Story 0022 validation are reverted)
- Introducing new AI task types or intents
- Bulk or automatic proposal generation
- Event category customization
- Cross-analysis event merging

## Technical Approach

### Commit-Diff Fact Extraction
Extend `CommitDiffEvidenceCollector` to analyze commit messages and file change patterns:
- Parse commit message prefixes (feat:, fix:, refactor:, etc.) when present
- Analyze file change patterns (dependency files → DEPS, config files → CONFIG)
- Group changes by module/package for MODULE-level facts
- Compute confidence based on evidence strength (multiple files, clear pattern = STRONG)

### Fact Schema
Each commit-scoped fact follows the existing `EvidenceItem` schema:
```json
{
  "type": "COMMIT_DIFF_SUMMARY",
  "description": "12 files changed across 3 modules, +450 -120 lines",
  "reference": "commit:2e6c71eee2f",
  "confidence": "STRONG",
  "evidenceReferences": ["src/main/java/...", "src/test/java/..."]
}
```

### Grounding Integration
The `EngineeringEventPromptBuilder._grounding()` method already collects `evidenceReferences`
from facts. Commit-scoped facts with their `evidenceReferences` will automatically populate
the `allowedEvidenceReferences` and `allowedSupportingFactIds` lists.

## Dependencies

- Story 0022 (Engineering Event Vertical Slice) — completed
- Existing `CommitDiffEvidenceCollector` infrastructure
- Existing knowledge selection v3 with evolution scope

## Validation Requirements

- Backend: `clean verify` passes
- Frontend: existing tests pass (no frontend changes expected)
- AI Engine: existing tests pass
- SonarQube: Quality Gate `OK`, new-code coverage ≥ 80%
- Live validation: end-to-end pipeline from analysis to event promotion

## Estimated Effort

Small — primarily extending an existing collector with new fact types and validation.
