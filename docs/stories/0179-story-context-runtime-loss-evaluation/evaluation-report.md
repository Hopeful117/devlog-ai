# Story 0179 - Runtime Evaluation Report

## Capture

- Runtime revision: `1790e84f25ed15855301bbc578d7195a5b2df73b`
- Scenarios: 4
- Repetitions: 3 per scenario
- Captures: 12
- Valid bounded snapshots: 12
- Python validation: `py_compile` passed
- Raw captures: stored outside the repository under `/tmp/opencode/`
- Bounded aggregate artifact: `runtime-report.json`
- Raw capture SHA-256: `d49491bc49f84e9a87bdc58d70d5e33fd2af44f8b23c6780fa3d27b282290744`
- Aggregate report SHA-256: `afbf6248a5db7449d39f96ddbe7274352b9a7e34abfcbef9314c4602db892d4a`
- Capture validation: the 12-row dataset was revalidated against the final
  revision/digest/ledger rules after correcting the runner's profile-budget
  validity check.

## Terminal Outcomes

| Outcome | Count |
|---|---:|
| Completed | 10 |
| Timed out | 1 |
| Failed | 1 |
| Provider incomplete | 0 |
| Invalid capture | 0 |

The timeout occurred in the targeted-component scenario. The failed terminal
outcome occurred in the truncated-context scenario. Neither was counted as a
successful analysis.

The subsequent deterministic-input rerun exposed a runtime idempotency
limitation: repeated identical questions were mapped to the same task despite
distinct idempotency keys. Those rows were rejected by the final capture
validator and are not included in the 12-row dataset above.

## Aggregate Ledger

| Measure | Total |
|---|---:|
| Candidates | 7338 |
| Unique candidates | 7338 |
| Ranked | 7338 |
| Selected | 720 |
| Projected | 720 |
| Enriched | 9 |
| Grounded | 9 |
| Discarded | 6618 |
| Provider-visible | 0 / UNKNOWN |

Normalized loss reasons across captures:

| Reason | Count |
|---|---:|
| `SELECTION_CATEGORY_CONCENTRATION_LIMIT` | 5670 |
| `SELECTION_INSUFFICIENT_RELEVANCE` | 612 |
| `SELECTION_ITEM_BUDGET_EXCEEDED` | 336 |
| `CORE_SELECTION_REDUCED` | 12 |
| `ENRICHMENT_TRUNCATED` | 9 |

The provider outcome was `COMPLETED` for 10 captures, `FAILED` for one, and
`AUTHORIZED_NOT_YET_MEASURED` for the timed-out task snapshot. Per-reference
provider visibility remained explicitly unknown because the callback contract
does not expose that attribution.

## Scenario Outcomes

| Scenario | Completed | Timeout | Failed | Valid analyses |
|---|---:|---:|---:|---:|
| Broad question | 3 | 0 | 0 | 3 |
| Targeted component | 2 | 1 | 0 | 2 |
| Insufficient evidence | 3 | 0 | 0 | 3 |
| Truncated context | 2 | 0 | 1 | 2 |

## Interpretation

The dominant measured loss stage is Core selection, specifically category
concentration. It accounts for the large majority of normalized losses in every
scenario. Enrichment truncation is present but small in aggregate. Projection
reduction is consistently reported, while provider execution shows meaningful
variance: 2 of 12 captures did not produce a successful analysis.

The runner initially marked captures invalid because the `C0-current` profile
label stated 32000 while the observed Core budget was 6000. The runner was
corrected to treat the profile label and observed budget as separate values;
the runtime ledger and snapshot digests were valid before and after this
correction.

## Limitations

- This is one repository and one revision, not a general model-quality result.
- Provider-visible reference attribution remains unavailable.
- The runtime runner does not establish causality for the category concentration
  policy; it only measures its observed loss reason.
- Strict same-input repetition is currently blocked by task reuse in the live
  MCP/Core path; the 12-row dataset uses the earlier bounded captures and was
  independently revalidated for identity and digest integrity.
- The environment did not provide the Python `pytest` command, so pytest was not
  claimed as executed.
