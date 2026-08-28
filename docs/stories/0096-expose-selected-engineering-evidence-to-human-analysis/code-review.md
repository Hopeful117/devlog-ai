# Code Review - Story 0096

## Scope Reviewed

- Analysis-scoped backend selected-evidence endpoint, service, historical projector, and typed DTO.
- Backend controller, unit, WebMvc, and PostgreSQL persistence coverage.
- Angular typed contract, service call, evidence section, Analysis-detail integration, styles, and
  focused tests.
- Story 0096 UI and lifecycle documentation.

Unrelated dirty and generated worktree files were excluded from the review.

## Findings

No blocking findings remain.

The final review found and resolved two presentation defects before approval:

- nullable historical commit-diff booleans now render `Not recorded` instead of fabricating a
  negative value;
- `NO_AI_TASK` and `SNAPSHOT_UNAVAILABLE` now use status semantics so asynchronously loaded states
  are announced consistently with loading and pending states.

Focused tests lock both corrections.

## Architecture and Contract

- The endpoint is Analysis-scoped and resolves the newest associated task by `createdAt DESC, id
  DESC`; it accepts no caller-supplied task identifier.
- Evidence is projected only from the task's persisted execution-time snapshot. The read service has
  no collection, selection, repository, MCP, provider, or prompt dependency.
- The response is a strict category-specific whitelist rather than a raw JSON map. Unknown extra keys
  are ignored; malformed known fields, unsupported versions, and contradictory identities fail
  closed.
- Missing category keys remain distinct from recorded empty categories, and pending, unavailable,
  no-task, available, and read-failure semantics are preserved.
- No canonical Analysis task, recomputation path, migration, generic evidence abstraction, or
  retrieval behavior was introduced.

## Security and Human Factors

- Persisted repository and human-authored strings render through Angular interpolation with no
  `innerHTML` or sanitizer bypass.
- Failure messages remain sanitized and projector exceptions do not retain evidence-bearing values.
- Supplied historical evidence is visually and semantically separated from generated Insights.
- Semantic headings, native disclosure controls, status/error roles, and narrow-layout styles are
  present.

Browser-level narrow-viewport behavior was not exercised because the repository has no deterministic
Playwright selected-evidence fixture. This is a documented non-blocking verification gap; component
tests cover semantic structure, disclosure behavior, hostile text, states, and responsive hooks.

## Verification Reviewed

- Focused backend: **48/48 passed**.
- Full backend: **971/971 passed**; JaCoCo's 80% bundle line gate passed.
- Focused frontend after final corrections: **23/23 passed** across 3 files.
- Full frontend after final corrections: **219/219 passed** across 45 files.
- Frontend lint, Prettier check, and production build passed.
- `git diff --check` passed.
- The build retains one unrelated pre-existing 135-byte component-style budget warning.

## Residual Risks

- The application still has no authentication or authorization boundary.
- Existing broad AI Task polling still transports raw context and selected-knowledge snapshots.
- Historical JSONB shape and snapshot immutability remain application-enforced, and the selection
  digest is not a complete snapshot checksum.
- Persisted evidence may contain sensitive project text; no secret-redaction capability was added.

## Verdict

**APPROVED_FOR_COMMIT_APPROVAL** - no blocking findings remain; the implementation is additive,
fail-closed, tested, and aligned with AC1-AC10 and the accepted ADR-063 boundary.
