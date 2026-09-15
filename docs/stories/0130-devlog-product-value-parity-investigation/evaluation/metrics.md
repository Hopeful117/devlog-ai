# Product Evaluation Metrics v1

## Frozen Thresholds

These thresholds are frozen before any Historical Engineering Synthesis
implementation. They are deliberately per-case and conjunctive.

| Metric | Threshold |
|---|---|
| Minimum evidence recall | `>= 0.80` for CASE-01..03; `>= 0.50` for CASE-04 evidence discovery |
| Minimum evidence precision | `>= 0.60` for CASE-01..03 |
| Minimum critical constraint recall | `>= 0.80` for CASE-02 and relevant positive cases |
| Minimum causal-link recall | `>= 0.67` for CASE-01..03, with no false `EXPLICITLY_DOCUMENTED` link |
| Maximum unsupported claim rate | `0.00` for factual and interpretative claims |
| Negative control accuracy | `100%`; CASE-04 must be `NOT_ESTABLISHED` |
| Minimum change-impact recall | `>= 0.75` across expected production components and tests for CASE-03 |
| Maximum agent follow-up searches | `<= 2` on at least 3 of 3 positive cases |
| Maximum selected context | `<= 60` evidence items and `<= 6000` estimated tokens, unless a versioned case override is approved |
| Evidence stability | `>= 0.90` Jaccard identity overlap across 3 fixed-input runs |
| Human median time improvement | `>= 30%` versus direct baseline across positive cases |
| Human correctness | Not lower than direct baseline on any positive case |
| Human subjective utility | `WOULD_USE_DEVLOG_FOR_THIS_TASK=YES` on at least 2 of 3 positive cases |

There is no averaging across gates. One failed mandatory threshold fails the
corresponding utility gate.

## Definitions

For a case, let `E` be the human-approved expected evidence identifiers and `R`
the returned evidence identifiers.

```text
evidenceRecall = |E ∩ R| / |E|
evidencePrecision = |E ∩ R| / |R|
```

The same set-based method applies to critical constraints, causal-link IDs,
affected production component IDs and affected test IDs. Returned aliases must
be normalized to canonical oracle identifiers before scoring; an unresolved
alias is not a match.

```text
unsupportedClaimRate = unsupported factual or interpretative claims / all factual or interpretative claims
```

Recommendations are not silently counted as facts. They must be explicitly
classified and cannot satisfy factual recall.

```text
evidenceStability = mean pairwise Jaccard(Run_i.references, Run_j.references)
```

Context efficiency records selected count, serialized size, estimated tokens,
warnings, truncation and follow-up searches. Large output is not a benefit.

## Gate Composition

```text
ENGINEERING_CORRECTNESS_PASS = existing engineering suite passes
  AND typed references resolve
  AND grounding is valid
  AND scope/freshness rules pass

AI_UTILITY_PASS = every mandatory AI threshold passes on all cases

HUMAN_UTILITY_PASS = correctness >= direct baseline
  AND median time improvement >= 30%
  AND subjective utility >= 2/3 positive cases

HUMAN_AI_PARITY_PASS = HUMAN_UTILITY_PASS AND AI_UTILITY_PASS

STORY_ACCEPTED = ENGINEERING_CORRECTNESS_PASS
  AND AI_UTILITY_PASS
  AND HUMAN_UTILITY_PASS
```

Engineering correctness passing alone is explicitly not Product Value PASS.
