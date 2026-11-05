# Evaluation Report - Story 0183

## Campaign

The rebuilt runtime was evaluated with the Story 0183 fixture at runtime
revision `d62315502c11b8dc6097ad0ef03a036fb87dfa9d`.

- Captures: `3/3` valid and evaluable.
- Candidate rows: `2052`.
- Selected/projected rows: `180`.
- Duplicate candidate references: `0`.
- Requested-file outcomes: all observed requested files were grounded.
- Provider outcomes: `2 COMPLETED`, `1 AUTHORIZED_NOT_YET_MEASURED`.

## Fixture Trust Coverage

The Core-produced fixture classified all captured candidates without unknown
fallbacks:

| Trust marker | Candidate rows |
|---|---:|
| `TRUSTED` | 51 |
| `HUMAN_AUTHORED` | 66 |
| `TECHNICAL_EVIDENCE` | 414 |
| `SYSTEM_METADATA` | 1521 |
| `UNVALIDATED` | 0 |
| `TRANSIENT_AI` | 0 |
| `UNKNOWN` | 0 |

This confirms that rejected candidates can receive an authoritative trust value
from Core candidate metadata when the existing classification rules support it.

## Offline Comparison

All policies replayed the same three fixtures with a maximum of `60` selected
rows per fixture.

| Policy | Selected rows | Grounded selected | Trust distribution |
|---|---:|---:|---|
| `current` | 180 | 3 | 33 `TRUSTED`, 33 `HUMAN_AUTHORED`, 36 `SYSTEM_METADATA`, 78 `TECHNICAL_EVIDENCE` |
| `balanced` | 180 | 3 | 4 `TRUSTED`, 16 `HUMAN_AUTHORED`, 34 `SYSTEM_METADATA`, 126 `TECHNICAL_EVIDENCE` |
| `unrestricted` | 180 | 3 | 180 `TECHNICAL_EVIDENCE` |

The comparison now has authoritative trust values for the alternative policy
selections. It no longer depends on treating rejected-candidate `UNKNOWN` values
as a quality signal.

## Interpretation

- Story 0183 closes the candidate-fixture trust coverage gap.
- The current policy selects a materially broader trust mix than the replayed
  alternatives in this sample.
- Requested-file survival and grounding remained stable across the three replay
  policies.
- The sample is too small and provider-incomplete to establish model-quality or
  production-quality superiority for any policy.
- Runtime ledger aggregate trust counts intentionally remain selected-map scoped;
  the enriched candidate fixture is the authoritative comparison input.

## Evidence Files

- Runtime report: `/tmp/opencode/story-0183-b6000-report.json`.
- Offline comparison: `/tmp/opencode/story-0183-b6000-comparison.json`.
- Runtime JSONL: `/tmp/opencode/story-0183-b6000-final.jsonl`.
