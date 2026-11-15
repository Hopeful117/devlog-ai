# DevLog AI A/B/C Question Review

## Status

`REVIEW COMPLETE - ORACLES PROPOSED, HUMAN REVIEW REQUIRED`

Question wording is unchanged from the strategic investigation. The review
does not silently optimize questions for C.

## Review Matrix

| Case | Discrimination | Multi-source required | Concrete code required | Contradiction/abstention | Review finding |
|---|---|---:|---:|---:|---|
| Q1 | Medium | Yes | Yes | No | Valid architecture case; cannot be answered reliably from ADR-067 alone because current flow details are in Java/Python. |
| Q2 | High | Yes | Yes | No | Strong code-behavior case; duplicate action semantics require reading implementation and tests. |
| Q3 | Low/Medium | Yes | No | No | Historical rationale is concentrated in ADR-006/067; retained because it tests historical-vs-current distinction, but it is the least discriminating case. |
| Q4 | High | Yes | Yes | No | Strong impact case; requires tracing contracts, Core projection, grounding and tests. |
| Q5 | High | Yes | Yes | Yes | Strong current-vs-historical reconciliation case; requires Git state and artifact comparison. |
| Q6 | High | Yes | Yes | Yes | Strong sequential diagnostic case; requires combining ledgers, projection, provider accounting and evaluator rules. |
| Q7 | Medium | Yes | No | Yes | Valid abstention case; answer must not convert technical readiness into quality evidence. |
| Q8 | High | Yes | Yes | Yes | Strong contradiction/history case; combines fixture evidence, live failures, ADR policy and current branch. |

## Overlap

- Q1 and Q3 overlap on ownership boundaries. Q1 asks for current execution;
  Q3 asks for historical architectural rationale. Keep both because the
  expected evidence and answer classes differ.
- Q6 and Q7 both test uncertainty. Q6 asks for a diagnostic method; Q7 asks
  for a conclusion about what is not established. They are not duplicates.
- Q8 subsumes some Q7 evidence but tests comparative interpretation across
  historical artifacts rather than only abstention.

## Cases Requiring Real Investigation

Q1, Q2, Q4, Q5, Q6 and Q8 require multiple searches or reads for a robust
answer. Q3 can be answered from ADRs but should be checked against current code.
Q7 is intentionally answerable from existing evaluation artifacts and should
test correct abstention rather than retrieval breadth.

## Potential Weakness

Q3 is likely too easy for a mature agent because ADR-006 and ADR-067 state the
boundary explicitly. It remains in v1 to test whether a direct investigator can
recover architectural intent from documents without DevLog context. A future
v1.1 may replace it only through a reviewed question-version change, not by
silent editing.

## Oracle Review Requirements

- Verify every referenced line against the frozen revision.
- Confirm that `INFERRED` claims are not scored as direct facts.
- Confirm that Q7 accepts `NOT_ESTABLISHED` and rejects both superiority and
  ineffectiveness claims.
- Confirm that Q5 does not treat historical report staleness as proof that its
  implementation description is false.
- Confirm that Q8 separates technical validity, semantic eligibility and
  architectural recommendation.
