# Story 0180 - Decision Report

## Decision

The runtime is ready for a descriptive Story Context loss evaluation at the
observed `B6000` budget. The next action is analysis of captured loss accounting,
not a production policy change.

## Evidence

- The synchronized campaign has twelve distinct task IDs and identities.
- All twelve rows match target revision `2a92d65...` and budget `6000`.
- All twelve rows contain valid loss ledgers and capture envelopes.
- The aggregate readiness report has zero failures.
- The readiness gate now stops and classifies these failures before aggregation.

## Next Action

Analyze `/tmp/opencode/story0180-runtime-b6000-report.json` descriptively. Keep
the observed `B6000` budget separate from the stale `C0-current` declaration.

## Analysis Outcome

- `7,626` unique candidates were ranked and `720` selections were projected.
- `SELECTION_CATEGORY_CONCENTRATION_LIMIT` dominated the recorded loss reasons,
  but this is descriptive evidence only.
- `11/12` captures produced analysis results; the remaining capture timed out
  after a valid bounded projection/provider envelope.
- Candidate counts varied across captures despite a stable reported revision;
  freshness was `UNKNOWN`, so no retrieval-quality conclusion is drawn.

The evaluation runner may use a bounded, labeled repetition marker to avoid the
current Core submission-digest fallback. This does not authorize changing Core
idempotency semantics.

## Deferred Actions

- Do not change Core submission idempotency semantics in this Story.
- Do not increase the context budget or alter the `C0-current` profile.
- Do not change selection category concentration, enrichment, projection, prompt,
  provider, or retry policy.
- Do not infer product or model quality from the invalid campaign.
- Do not convert the B6000 descriptive loss counts into a production policy
  change without a separately approved evaluation design.
