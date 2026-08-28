# Engineering Report - Story 0096

## Delivery State

Story 0096 is implementation-complete and final-human-review-approved. Work remains uncommitted on
`main` at baseline `10e0e457abd7eeb28fbdd4ec5f01963b25ca1752`; no commit, push, pull request, or
merge was performed by the implementation agent. Pre-existing unrelated worktree changes were
preserved.

## Story Outcome

The Analysis detail experience now exposes the exact selected-evidence snapshot persisted for the
displayed AI Task execution. Humans can inspect which historical Facts, Observations, prior Insights,
architecture knowledge, Engineering Events, human context, evolution context, and repository evidence
were supplied to the model without confusing those inputs with current project state or generated
Analysis output.

## Backend

The Core exposes:

```http
GET /api/v1/analyses/{analysisId}/selected-evidence
```

The service resolves the newest associated AI Task by `createdAt DESC, id DESC` and projects only its
persisted `selectedKnowledgeSnapshot`. The task-specific response includes Analysis/project identity,
task audit identity, selection version and digest, typed snapshot metadata, and eight typed evidence
categories.

Successful states are `NO_AI_TASK`, `SNAPSHOT_PENDING`, `SNAPSHOT_UNAVAILABLE`, and `AVAILABLE`.
Malformed, unsupported, or contradictory snapshots fail through the existing sanitized internal-error
path rather than degrading to empty evidence.

`HistoricalSelectedEvidenceSnapshotProjector` supports persisted knowledge-selection V1-V4 shapes,
distinguishes missing keys from recorded empty values, ignores unknown additive keys, and exposes only
approved fields. It does not recompute selection or depend on current context, repository inspection,
MCP, prompts, or providers.

## Frontend

The Angular Analysis detail page loads the typed read contract directly from Core and displays it after
AI execution metadata and before generated Insight surfaces. The section provides:

- explicit historical-input wording and task/selection identity;
- eight category-preserving sections with counts and honest recorded/not-recorded semantics;
- repository content status, symbol details, evolution details, diagnostics, and selection metadata;
- loading, sanitized failure with retry, no-task, pending, unavailable, available, and globally empty
  states;
- safe inert rendering for repository and human-authored content;
- semantic headings, native disclosure, live status/error roles, and a narrow-layout presentation.

The final code-review pass corrected nullable historical commit-diff booleans so absent values display
as `Not recorded`, and added status semantics to the no-task and unavailable asynchronous states.

## Acceptance Assessment

AC1-AC10 are implemented. The capability is persisted-only, Analysis-scoped, category-faithful,
task-identified, strict-whitelist, safe-rendered, additive, and separate from generated synthesis. No
retrieval, ranking, persistence schema, prompt, provider, MCP, or ADR behavior changed.

## Verification

Backend verification completed before final human approval:

- focused backend suite: **48 tests passed**, 0 failures, 0 errors, 0 skipped;
- full `./mvnw clean verify`: **971 tests passed**, 0 failures, 0 errors, 0 skipped;
- JaCoCo 80% bundle line-coverage gate: **PASS**;
- PostgreSQL 17/Testcontainers coverage proves persisted-source fidelity, newest-task ordering,
  association checks, legacy null states, and fail-closed malformed reads.

Frontend verification was rerun after the final review corrections:

- focused Story suite: **3 files, 23 tests passed**;
- full unit suite: **45 files, 219 tests passed**;
- `npm run lint`: **PASS**;
- `npm run format:check`: **PASS**;
- `npm run build`: **PASS**.

Repository verification:

- `git diff --check`: **PASS**;
- no Story 0096 migration, MCP, retrieval, prompt, provider, or broad `AiTaskResponse` change;
- one unrelated pre-existing build warning remains: `project-maintenance-section.scss` exceeds its
  component-style budget by 135 bytes.

Playwright was not run because the existing flow has no API interception or deterministic persisted
selected-evidence fixture and depends on mutable local data. Deterministic component tests cover
semantic structure, native disclosure behavior, hostile text, state behavior, and responsive layout
hooks. Browser-level narrow-viewport behavior remains an explicit non-blocking verification gap.

## Residual Technical Debt

- No application authentication/authorization boundary exists.
- Broad AI Task polling still retransmits raw selection and current-context snapshots.
- Persisted V1-V4 JSONB has no database shape constraint.
- `selectionDigest` is not a complete persisted-snapshot checksum.
- Snapshot immutability is application-level rather than database-enforced.
- Persisted project evidence can contain sensitive text; no new redaction capability was introduced.

## Engineering Verdict

**READY_FOR_HUMAN_COMMIT_AND_MERGE** - implementation, final review corrections, lifecycle artifacts,
and affected quality gates are complete. Git delivery remains intentionally owned by the human
engineer.
