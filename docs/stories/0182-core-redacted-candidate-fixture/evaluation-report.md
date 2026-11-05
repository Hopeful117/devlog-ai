# Evaluation Report - Story 0182

## Runtime Campaign

The rebuilt runtime was evaluated at revision
`2a92d65b42e41819b4e3bd915cf486d2f1bc8b83`.

- Final B6000 campaign: four scenarios, one repetition each.
- Captures: `4/4` valid and evaluable.
- Candidate rows: `2712` total.
- Selected/projected rows: `240` total.
- Duplicate candidate references: `0`.
- Requested-file outcomes: all requested files were found, selected, projected,
  and grounded.
- Fixture and cross-language telemetry digests: valid for all captures.
- Task statuses: `2 COMPLETED`, `1 FAILED`, `1 TIMED_OUT`.
- Provider statuses: `3 COMPLETED`, `1 AUTHORIZED_NOT_YET_MEASURED`.
- Invalid captures: `0`.

The targeted requested-file smoke also passed independently with `658` candidate
rows, `60` selected rows, and `docker-compose.yml` grounded.

## Offline Comparison

The comparator replayed the same Core fixture for `current`, `balanced`, and
`unrestricted`.

| Policy | Selected rows | Grounded selected | Requested files selected | Technical evidence | Unknown trust |
|---|---:|---:|---:|---:|---:|
| current | 240 | 4 | 4/4 | 240 | 0 |
| balanced | 240 | 4 | 4/4 | 154 | 86 |
| unrestricted | 240 | 4 | 4/4 | 88 | 152 |

The requested-file and grounding outcomes are preserved by all three replay
policies in this campaign. The alternative policies select more candidate rows
whose trust is `UNKNOWN`; this is an explicit Core limitation because trust
mapping currently covers authorized selected evidence, not every rejected
candidate. It must not be interpreted as proof that those candidates are
untrusted.

The alternatives also produce materially different category mixes. The
`unrestricted` replay concentrates heavily in `ANALYSIS`, while `balanced`
spreads selection across `ANALYSIS`, `CHANGED_FILE`, `CONFIG_FILE`, and related
kinds. These are offline replay observations, not production-policy conclusions.

## Interpretation

- The Core candidate fixture is complete for the captured, story-scoped
  candidate set and is usable for deterministic replay.
- Requested-file survival is measurable and remains stable in this campaign.
- Grounding remains bounded and Core-derived.
- Trust comparison for alternative policies is incomplete where Core reports
  `UNKNOWN`; no trust-quality ranking can be asserted from this campaign.
- The provider timeout is independent of fixture validity and does not invalidate
  the selection telemetry, but it limits conclusions about model output quality.
- The timed-out task outcome is independent of fixture validity, but
  they limit conclusions about model output quality and scenario-level AI
  behavior. Provider status and visibility remain reported separately.

## Decision Boundary

No production selection-policy change is recommended from this report. The
fixture and comparator now provide the required measurement path, but a policy
decision requires either:

1. an approved interpretation of `UNKNOWN` trust for alternative candidates; or
2. a separate Core change that exposes authoritative trust for the complete
   candidate set.

The current policy remains the only policy with fully authoritative trust
coverage in this campaign.

## Evidence Files

- Runtime report: `/tmp/opencode/story-0182-b6000-complete-report.json`
- Policy comparison: `/tmp/opencode/story-0182-b6000-comparison.json`
- Targeted smoke report: `/tmp/opencode/story-0182-runtime-final-report.json`
