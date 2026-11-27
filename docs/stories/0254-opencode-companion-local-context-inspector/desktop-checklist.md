# Story 0254 — Owner-led Desktop Validation

## Guardrails

- Use the already-installed artifact. Do not alter active plugin settings,
  launch/reload Desktop, or create a DevLog operation as part of this checklist
  without separate owner authorization.
- The owner's normal workflow uses Code Mode. Code Mode nested Context
  inspection is deferred; this checklist verifies truthful unavailability,
  not nested-result inspection.
- Direct-result validation is optional. If no eligible direct
  `get_engineering_context` result is already present, record it as
  `NOT EVALUATED`; do not manufacture one.
- Record only sanitized observations. Do not copy transcripts, payloads, or
  secrets into screenshots or logs.

## Checklist

- [ ] Now remains the default view.
- [ ] Existing avatar and live-presence behavior remain functional.
- [ ] History remains available and usable.
- [ ] Context tab opens and renders correctly.
- [ ] Context picker renders; empty and unavailable states are understandable.
- [ ] Code Mode operations do not fabricate or claim an exact nested Context
      result; the UI explains that individual nested identity/result is
      unavailable.
- [ ] If an eligible direct result already exists, it can be selected from the
      Context picker and inspected as an observed result. Otherwise record
      `NOT EVALUATED`.
- [ ] Closing and reopening the panel does not expose stale selection or
      context from the prior panel/session.
- [ ] Context clearly distinguishes local observed-result inspection from
      backend Context Trace retrieval.

## Outcome

- Runtime outcome: `NOT RUN` / `PASS` / `FAIL` / `BLOCKED`
- OpenCode Desktop / sidecar / plugin versions observed:
- Direct eligible result present: `YES` / `NO` — inspection: `PASS` / `FAIL` /
  `NOT EVALUATED`
- Deviations or sanitized evidence references:
- Owner acceptance recorded separately: `YES` / `NO`

Automated tests, typecheck, build, and package checks do not constitute Desktop
runtime validation or Story acceptance.
