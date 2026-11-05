# Story 0180 - Runtime Readiness Evaluation Report

## Campaign

- Profile: `B6000`
- Planned scenarios: 4
- Planned repetitions: 3 per scenario
- Target revision: `2a92d65b42e41819b4e3bd915cf486d2f1bc8b83`
- Runtime capture: `/tmp/opencode/story0180-runtime-b6000-combined.jsonl`
- Readiness smoke capture: `/tmp/opencode/story0180-readiness-live.jsonl`
- Evaluation identity: each request includes a bounded, explicitly labeled
  repetition marker because Core reuses identical logical submission digests.

## Result

The campaign is **evaluable**.

The synchronized twelve-row campaign produced twelve structurally valid captures:

- 12 distinct task IDs and scenario/repetition identities;
- 12 matching target revisions;
- observed and expected context budget `6000` in all rows;
- 12 versioned loss ledgers and valid capture envelopes;
- 11 `COMPLETED` outcomes and 1 bounded timeout observation.

The campaign was executed per scenario in fresh MCP processes. A prior combined
run exposed token expiry during a long polling request; this was avoided without
changing Core or MCP policy by using the runner's scenario and repetition
selectors.

The readiness report returned `campaignEvaluable: true`, `failureCount: 0`, and
`validCaptureCount: 12`.

## Interpretation

The campaign is suitable for descriptive loss-accounting analysis at the
observed `B6000` budget. It does not authorize changing production policy.

## Validation Evidence

- The existing invalid capture set is now classified through a bounded readiness
  report rather than rejected with an unstructured duplicate-task exception.
- The live runner stops before spending the remaining repetitions when readiness
  is not established.
- The B6000 campaign passed readiness aggregation across all four scenarios.
- Raw captures remain outside the repository.

## Descriptive Findings

- The campaign ranked `7,626` unique candidates and projected `720` selections
  across the twelve captures. No duplicate candidates were reported.
- The dominant recorded loss reason was
  `SELECTION_CATEGORY_CONCENTRATION_LIMIT` with `5,958` occurrences out of
  `6,906` total loss-reason occurrences. This is an observation of the current
  B6000 runtime, not an authorization to change the concentration policy.
- `CORE_SELECTION_REDUCED` occurred in all twelve captures. The preflight outcome
  was `PROJECTION_REDUCED` in all twelve captures.
- Enrichment was present in nine captures. The three insufficient-evidence
  captures had no enrichment, which is consistent with their scenario intent;
  this report does not treat that as a defect.
- Eleven captures had valid analysis results. The final truncated-context
  repetition timed out after producing its bounded projection/provider envelope;
  it is included in readiness accounting but excluded from analysis-result
  counts.
- Candidate counts varied from `627` to `640` while the reported target revision
  remained constant. Freshness remained `UNKNOWN`, so this variation is a
  runtime observation and not evidence of a product-level retrieval regression.
- Provider-visible attribution remained unavailable (`providerVisibleCount: 0`)
  under the current callback contract.

## Conclusion

The B6000 campaign is technically evaluable and provides descriptive loss
accounting. It does not justify changing context budget, selection concentration,
enrichment, projection, provider, retry, or product policy. Any future quality
comparison should first establish a stable treatment for the timeout observation
and an explicit provider-visibility contract.
