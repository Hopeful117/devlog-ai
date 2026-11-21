# Story 0220 Decision Report

## Evidence Basis

This report is based only on the eight deterministic provider-free captures in
`story-0220-runtime-loss-v1`. They are revision-pinned to `abcdef1` and do not
claim live provider quality.

## Technical Outcome

| Outcome | Count |
|---|---:|
| `COMPLETED` | 3 |
| `PROJECTION_REDUCED` | 1 |
| `PROVIDER_INCOMPLETE` | 1 |
| `PROVIDER_TIMEOUT` | 1 |
| `REJECTED` | 1 |
| invalid revision / `NOT_EVALUATED` | 1 |

Four captures are semantically eligible and four are excluded from the quality
denominator. Provider visibility is `UNKNOWN` for the provider-free baseline.

## Dominant Measured Bottleneck

The dominant measured loss in the eligible deterministic captures is
`PROJECTION_EVIDENCE_REMOVED`, with 20 removed evidence items in the broad
projection-reduced scenario. This is a measurement of the fixture's explicit
Core/projection accounting, not evidence that the provider received or lost
those items.

The provider-terminal scenarios are represented separately as
`PROVIDER_INCOMPLETE` and `PROVIDER_TIMEOUT`; they are not counted as successful
semantic responses and do not enter the semantic denominator.

## Decision

No production corrective Story is justified from this baseline alone. The
projection reduction is measurable and should be investigated only with a
future bounded live or Core-backed capture set that preserves the same revision,
ledger, and interaction identities. Provider remediation cannot be selected
because provider-visible evidence is currently unavailable.

## Follow-Up Recommendation

If human owners want to pursue the next investigation, create a separate Story
for bounded live-capture collection and provider attribution. That Story must
define credentials, retention, privacy, and acceptance rules independently; no
such authority is granted by Story 0220.
