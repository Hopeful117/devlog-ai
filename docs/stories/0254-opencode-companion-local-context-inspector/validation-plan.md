# Story 0254 — Focused Validation Plan

Validate the implementation against the following deterministic and Desktop
scenarios. A successful build/package is not Desktop runtime certification.

## Pure decoder and projection tests

- Accept a fixture matching the actual direct completed
  `get_engineering_context` JSON text result and expected EngineeringContext
  DTO.
- Reject tool-name-only eligibility; assistant text containing JSON; unrelated
  tool; `devlog_story_agent`; arbitrary JSON; Code Mode outer success; malformed
  JSON; `isError`; multiple/ambiguous result blocks; missing required DTO
  fields; unsupported field types; negative/fractional counts.
- Reject over-limit JSON, text blocks, and revisions; verify fixed traversal
  and collection bounds.
- Preserve `CURRENT`, `STALE`, `NO_BASELINE`, `UNKNOWN`, and
  `PARTIALLY_FRESH` distinctly. Verify stale is not converted to untrusted or
  fabricated evidence.
- Verify no confidence is inferred or exposed.
- Handle metadata truncation and `RESPONSE_SIZE_TRUNCATED`; distinguish
  missing/unknown truncation from false.
- Count evidence by the four explicit trust tiers and `UNKNOWN`; count
  recognized sections only; verify missing/unknown tiers and mismatched
  declared/observed counts yield partial/unknown rather than repaired counts.
- Allowlist diagnostic codes and collapse unknown warning strings to a fixed
  indicator.
- Verify empty verified context differs from unavailable/unverified and
  partial/truncated context.
- Assert projected output contains no intent, request echo values, project
  details, evidence body/summary/identifier/reference, raw JSON, arbitrary
  warning/error text, token, or credential.

## Selection, route and regression tests

- The Context picker lists eligible direct results from the loaded History
  transcript, projects only fixed labels/IDs/optional observed times, and
  selecting an entry displays the exact same result as the History action.
- With no eligible direct result, Context explains the empty picker and does not
  trigger a transcript or backend fetch. Code Mode name-only rows never enter
  the picker.
- Select an eligible History entry and verify its projection comes from the
  exact same direct part/result and native call ID.
- Missing or duplicate native call IDs disable selection; no timestamp, tool
  name, aiTaskId, or synthesized identity is used as fallback.
- Updating a selected part's result recomputes the projection; changing its
  tool/status or removing it clears selection and projection.
- Eviction from the bounded transcript, session change, server/location
  change, panel close/reopen, and plugin disposal clear scoped state and clean
  up subscriptions; no old-session details flash in the new route.
- Code Mode name-only metadata remains grouped under the outer execution and
  nested outcome remains `UNKNOWN`; outer completion never creates an eligible
  Context source or a synthetic nested identity.
- An exact nested result is eligible only if a future, separately approved
  native contract provides both unique nested identity and exact result
  association; tests must reject name-, order-, time-, task-ID-, and
  outer-result-based joins.
- Preserve existing Now/History fixtures and behavior; run all existing
  Companion tests.

## Owner-led Desktop validation

- Code Mode is the owner's primary workflow, but nested context inspection is
  deferred. Confirm its operation does not produce fabricated or purportedly
  exact nested Context in the panel; the outer execute result is not a nested
  MCP result.
- Confirm no backend Context Trace or additional network operation is caused.
- Exercise direct result projection only if an eligible direct result already
  exists in the loaded transcript. Otherwise record that criterion as
  `NOT EVALUATED`; do not create another DevLog operation.
- Do not claim full runtime validation based on automated checks.

## Build and packaging

From `opencode-mod/devlog-companion`:

```bash
npm test
npm run typecheck
npm run build:gui
npm pack --dry-run
```

Also run `git diff --check`, inspect the full diff, and verify the dry-run
package contains the intended GUI artifact/source only, with no backend,
credential, Claude, or runtime-infrastructure additions. The existing
`pack:dry-run` script runs build, tests, typecheck, and `npm pack --dry-run` if
one combined command is desired.

## Evidence/reporting

Record commands and outcomes; distinguish skipped/blocked checks from passing.
A successful build/package is not Desktop runtime certification. Desktop
acceptance is owner-led and separately recorded in the manual checklist.
