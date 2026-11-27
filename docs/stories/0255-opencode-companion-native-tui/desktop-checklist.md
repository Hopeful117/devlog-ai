# Story 0255 — Desktop Non-Regression Checklist

This is an owner-led checklist for a future approved validation. It does not
authorize launch/reload, configuration edits, or new DevLog activity.

## Guardrails

- Use the already-installed artifact and the existing approved Desktop
  configuration. Do not edit active plugin paths or restart services without
  separate owner approval.
- Do not create a real DevLog operation to populate History.
- Do not interpret this checklist as Story 0254 acceptance or full Desktop
  compatibility certification.
- Record sanitized observations only.

## Checklist

- [ ] Record Desktop, sidecar/service, and GUI plugin SDK versions.
- [ ] Existing DevLog Companion remains discoverable and visible as before.
- [ ] Now remains the default view; existing Now, History and Context behavior
      are unchanged.
- [ ] Existing route/session changes clear stale GUI state.
- [ ] Desktop package still loads only the GUI contribution it is meant to
      load; TUI-only entrypoints/dependencies do not break GUI packaging.
- [ ] No TUI-only configuration or plugin activation is required by Desktop.
- [ ] Code Mode nested Context remains unavailable as documented in Story 0254.
- [ ] No Context Trace or additional DevLog request is triggered.
- [ ] Preflight Gate, Claude Companion, and active Desktop configuration remain
      unchanged by the TUI integration.

## Outcome

- Runtime outcome: `NOT RUN` / `PASS` / `FAIL` / `BLOCKED`
- Desktop / sidecar / GUI SDK tuple:
- Existing GUI artifact revision:
- Sanitized deviations/evidence:
- Owner acceptance: `YES` / `NO` (separate gate)
