# Story 0128 - Implementation Report

## Repository State

- Branch: `main`
- Baseline: `d831278cb39c7346f4960b059d3862537b043454`
- Worktree: modified; pre-existing generated target files and documentation changes preserved
- Commit created: **NO**
- Push performed: **NO**

## Scope Implemented

Story 0128 implements the typed provider boundary for
`architecture-overview-v3` while retaining the existing v1/v2 contracts.

- Added Core `AiReferenceResolver` and deterministic snapshot reverse lookup.
- Added typed Java provider reference DTOs for proposal and synthesis callbacks.
- Added v3 typed projection with separate facts, observations and evidence candidates.
- Added v3 Python/Pydantic schemas, prompt construction, dispatch and validation.
- Added authoritative Java resolution against the originating `AiTask` snapshot.
- Preserved the existing proposed Insight persistence lifecycle.
- Updated the Angular architecture review objective from v2 to v3.

## Files Changed

| Area | Main files |
|---|---|
| Core | `AiReferenceResolver`, `AiReferenceResolutionException`, callback DTOs and services |
| AI Engine | typed schemas, `insight.py`, `InsightGenerationService`, callback client |
| Frontend | `project-analyses-section.ts` and related tests |
| Tests | Core resolver/callback tests, Python typed-contract tests, frontend regression tests |

## Governance Preserved

- Java Core remains the deterministic and authoritative resolver.
- Provider references remain opaque `{type, ref, scope}` values.
- AI output remains a proposal and is never promoted directly to trusted knowledge.
- v1/v2 and null-snapshot legacy tasks are not reinterpreted as v3.

## Verification

- Focused backend tests: 29 passed.
- Full backend suite: 1,324 passed.
- Full AI Engine suite: passed.
- Frontend suite: 260 passed across 49 test files.
- Frontend lint: passed.
- Frontend format check: passed.
- `git diff --check`: passed.

## Readiness

**READY_FOR_HUMAN_ACCEPTANCE**

Human Story acceptance remains separate and has not been declared by this
report.
