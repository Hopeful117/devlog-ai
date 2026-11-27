# Story 0253 — Repository Analysis

## Status and evidence boundary

**Pre-implementation evidence snapshot; not runtime certification.** Repository
inspection was performed before the authorized implementation against `origin/main` at
`fa96f5a28ab367c0ae11017b484e7031d56da6ab`. The current worktree is on
`story/0252-devlog-opencode-live-presence` at `e750052dc993e30d0081ae08945f0ee9e53cd443`;
`git diff origin/main...HEAD` is empty, so the trees match. A pre-existing,
untracked `docs/stories/0252-devlog-opencode-live-presence/repository-analysis.md`
was preserved.

The highest numbered Story directory is 0252, so 0253 is the next observed
repository number. This does not prove that a corresponding Story is registered
in DevLog.

## DevLog preflight

The bounded Story Context Agent task is
`7ae0ac23-52d4-4462-98f2-3dae9b09b613`. Its snapshot became `COMPLETED`, but
the original `devlog_story_agent` resume could not be reproduced: the stable
idempotency key was rejected with `409 RESOURCE_CONFLICT` because reconstructed
parameters differed from the original request. No second task was created.
The snapshot's selected files included ADR-084, Story 0252, and its minimal
presence contract. Its context was `PARTIALLY_FRESH`/`STALE`, truncated, and
resolved at `922e83e94c3c9328eb3f7959e7111609ca666c37`, not the current
`origin/main` revision. It is bounded historical context only; current
repository files govern physical state and this preparation does not rely on
its unreturned synthesis.

## Governing and implementation evidence

- ADR-084 requires native platform adapters, truthful uncertainty, bounded
  separately managed active state/history, route/session scoping, lifecycle
  cleanup, and no fabricated identity/correlation. It also requires an
  independent compatibility claim per affected platform.
- Story 0252 explicitly excluded operation-history UI and permits only the
  routed session's already-loaded transcript, bounded in-memory projection, no
  transcript fetch, persistence, external transmission, backend, Claude, or
  extra DevLog calls. A new Story is therefore needed for History.
- `src/gui.tsx` already follows reactive `MountedSession`, reads
  `server.data.session.message.list(session.id)`, and refreshes via native
  session events. It currently renders live presence only.
- `src/gui-presence.js` already provides the DevLog allowlist, bounded
  transcript/part/result limits, explicit task-outcome decoder, Code Mode
  name-only handling, and pure presence projection. It does not expose a
  per-operation history projection.
- Existing tests cover direct outcomes, Code Mode nested evidence, concurrency
  aggregation, stale running activity, route-filtered invalidation, and
  payload non-retention.
- Claude's `register.tsx` offers a History UX reference: a bounded list of up to
  20 operations with tool/status, duration, relative time and optional trace
  inspection. Its atom state is not explicitly session-scoped and its trace
  credential path is not a pattern to reproduce. Reuse only high-level UX
  ideas, not implementation or data semantics.

## Decoder 0252 finding

The 0252 implementation report confirms an observed mismatch between an earlier
assumed Code Mode row shape (`{tool,status}`) and the pinned host's terminal
`{name}` rows. The decoder and fixtures were corrected; the report records a
post-correction Desktop observation of `WORKING` then `UNKNOWN`, which matches
the available evidence. The current code and tests intentionally keep
name-only nested rows unknown and do not correlate results to nested rows.
This is a documented correction, not a confirmed remaining regression.

The report states that the corrected decoder did not receive a second
independent review. No separate current failing fixture or reproducible
regression was found in this bounded inspection. Any newly discovered decoder
defect should be evidenced and handled in its own approved scope, not silently
folded into History.

## Open questions for owner review

- Approve or adjust the proposed row fields and whether transcript sequence may
  be used for display ordering without implying wall-clock chronology.
- Approve explicit limits (proposed: 20 rows, with transcript/part bounds
  aligned to Story 0252).
- Decide whether unknown rows should be shown as incomplete entries or omitted;
  omission must not make an incomplete transcript appear complete.
- Confirm that a local History view derived from already-loaded transcript is
  desired despite its bounded/transcript-cache completeness limitations.

At this analysis snapshot, no production code, Desktop configuration, DevLog
registration, or live operation had been changed or invoked. The subsequent
implementation and validation are recorded in `implementation-report.md`.
